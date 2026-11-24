import { useCallback, useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { useTourStore, TOUR_MOBILE_BREAKPOINT } from '@/stores/tour-store';
import { tTour } from '@/lib/tour-i18n';
import type { TourPlacement, TourStep } from '@/lib/tour-steps';
import { cn } from '@/lib/utils';

/* =========================================================================
 * 新手引导 —— 第 3 层：渲染层
 *
 * 一层半透明遮罩用 clip-path 把目标矩形「挖空」形成聚光灯，
 * 目标外扩 8px 画品牌色高亮框，旁边浮一张气泡卡片。
 * 通过 createPortal 挂到 document.body，避免被父级 overflow / z-index 裁剪。
 *
 * 容错：目标元素不存在时自动跳过该步；视口 < 768px 不渲染；SSR 下不渲染。
 * ========================================================================= */

/** 高亮框相对目标的外扩距离 */
const SPOT_PAD = 8;
/** 气泡卡片宽度 */
const CARD_W = 340;
/** 气泡与高亮框之间的间距 */
const GAP = 14;
/** 卡片与视口边缘的最小留白 */
const EDGE = 16;
/** 卡片高度尚未测量出来时的估算值，仅用于首帧翻转判断 */
const EST_CARD_H = 200;
/** 目标滚动到视口内后，持续跟随测量的时长（覆盖平滑滚动动画） */
const SETTLE_MS = 700;

interface Rect {
  top: number;
  left: number;
  width: number;
  height: number;
}

/** 一次测量的完整快照：绑定 step 索引，避免切步瞬间用到上一步的旧坐标 */
interface Geometry {
  step: number;
  rect: Rect | null;
  /** 该步声明了 target 但 DOM 中找不到 */
  missing: boolean;
  vw: number;
  vh: number;
  cardH: number;
}

const clamp = (v: number, min: number, max: number) => Math.min(Math.max(v, min), max);

const sameGeometry = (a: Geometry | null, b: Geometry): boolean => {
  if (!a) return false;
  if (a.step !== b.step || a.missing !== b.missing) return false;
  if (a.vw !== b.vw || a.vh !== b.vh || a.cardH !== b.cardH) return false;
  if (!a.rect || !b.rect) return a.rect === b.rect;
  return (
    Math.abs(a.rect.top - b.rect.top) < 0.5 &&
    Math.abs(a.rect.left - b.rect.left) < 0.5 &&
    Math.abs(a.rect.width - b.rect.width) < 0.5 &&
    Math.abs(a.rect.height - b.rect.height) < 0.5
  );
};

/**
 * 计算气泡卡片位置。
 * 先按声明的 placement 摆放，空间不足时自动翻转到对侧，最后做视口边界 clamp。
 */
function computeCardPosition(
  rect: Rect | null,
  placement: TourPlacement | undefined,
  vw: number,
  vh: number,
  cardH: number,
): { left: number; top: number; width: number; placement: TourPlacement | 'center' } {
  const width = Math.min(CARD_W, vw - EDGE * 2);
  const h = cardH || EST_CARD_H;

  // 无目标（欢迎步）：居中展示
  if (!rect) {
    return {
      left: (vw - width) / 2,
      top: clamp((vh - h) / 2, EDGE, Math.max(EDGE, vh - h - EDGE)),
      width,
      placement: 'center',
    };
  }

  const spotTop = rect.top - SPOT_PAD;
  const spotBottom = rect.top + rect.height + SPOT_PAD;
  const spotLeft = rect.left - SPOT_PAD;
  const spotRight = rect.left + rect.width + SPOT_PAD;

  let p: TourPlacement = placement ?? 'bottom';
  const fitsBelow = spotBottom + GAP + h <= vh - EDGE;
  const fitsAbove = spotTop - GAP - h >= EDGE;
  const fitsLeft = spotLeft - GAP - width >= EDGE;
  const fitsRight = spotRight + GAP + width <= vw - EDGE;

  if (p === 'bottom' && !fitsBelow && fitsAbove) p = 'top';
  else if (p === 'top' && !fitsAbove && fitsBelow) p = 'bottom';
  else if (p === 'left' && !fitsLeft && fitsRight) p = 'right';
  else if (p === 'right' && !fitsRight && fitsLeft) p = 'left';

  let left: number;
  let top: number;
  switch (p) {
    case 'top':
      left = rect.left + rect.width / 2 - width / 2;
      top = spotTop - GAP - h;
      break;
    case 'left':
      left = spotLeft - GAP - width;
      top = rect.top + rect.height / 2 - h / 2;
      break;
    case 'right':
      left = spotRight + GAP;
      top = rect.top + rect.height / 2 - h / 2;
      break;
    case 'bottom':
    default:
      left = rect.left + rect.width / 2 - width / 2;
      top = spotBottom + GAP;
      break;
  }

  return {
    left: clamp(left, EDGE, Math.max(EDGE, vw - width - EDGE)),
    top: clamp(top, EDGE, Math.max(EDGE, vh - h - EDGE)),
    width,
    placement: p,
  };
}

/** 遮罩镂空：一个「回」字形 polygon，把目标矩形从暗色层里挖掉 */
function buildClipPath(rect: Rect | null): string | undefined {
  if (!rect) return undefined;
  const l = Math.max(0, rect.left - SPOT_PAD);
  const t = Math.max(0, rect.top - SPOT_PAD);
  const r = rect.left + rect.width + SPOT_PAD;
  const b = rect.top + rect.height + SPOT_PAD;
  return [
    'polygon(',
    '0 0, 0 100%, ',
    `${l}px 100%, ${l}px ${t}px, ${r}px ${t}px, ${r}px ${b}px, ${l}px ${b}px, ${l}px 100%, `,
    '100% 100%, 100% 0',
    ')',
  ].join('');
}

export interface TourOverlayProps {
  /** 本页要展示的引导 id；与 store 中 activeTourId 一致时才渲染 */
  tourId: string;
  /**
   * 步骤配置。请使用模块级常量（而非 JSX 内联数组），
   * 否则每次渲染都会产生新引用，导致测量副作用反复重建。
   */
  steps: TourStep[];
}

export function TourOverlay({ tourId, steps }: TourOverlayProps) {
  const isActive = useTourStore((s) => s.isActive);
  const activeTourId = useTourStore((s) => s.activeTourId);
  const currentStep = useTourStore((s) => s.currentStep);
  const totalSteps = useTourStore((s) => s.totalSteps);
  const nextStep = useTourStore((s) => s.nextStep);
  const prevStep = useTourStore((s) => s.prevStep);
  const goToStep = useTourStore((s) => s.goToStep);
  const dismiss = useTourStore((s) => s.dismiss);

  const [geo, setGeo] = useState<Geometry | null>(null);
  const [cardEl, setCardEl] = useState<HTMLDivElement | null>(null);

  const mine = isActive && activeTourId === tourId;
  const step: TourStep | undefined = mine ? steps[currentStep] : undefined;
  const target = step?.target;

  /** 读取目标元素坐标 + 视口尺寸 + 卡片实际高度，合成一次快照 */
  const measure = useCallback(() => {
    if (typeof window === 'undefined') return;
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const cardH = cardEl?.offsetHeight ?? 0;

    let rect: Rect | null = null;
    let missing = false;

    if (target) {
      const el = document.querySelector<HTMLElement>(`[data-tour="${target}"]`);
      const r = el?.getBoundingClientRect();
      if (!r || (r.width === 0 && r.height === 0)) {
        missing = true;
      } else {
        rect = { top: r.top, left: r.left, width: r.width, height: r.height };
      }
    }

    const snapshot: Geometry = { step: currentStep, rect, missing, vw, vh, cardH };
    setGeo((prev) => (sameGeometry(prev, snapshot) ? prev : snapshot));
  }, [target, currentStep, cardEl]);

  // 测量 + 跟随：切步时先把目标滚进视口，随后用 rAF 持续测量一小段时间以跟上平滑滚动；
  // 之后靠 resize / scroll(捕获阶段) 事件保持同步。
  useEffect(() => {
    if (!mine || !step) return;
    if (typeof window === 'undefined') return;

    if (target) {
      const el = document.querySelector<HTMLElement>(`[data-tour="${target}"]`);
      if (el) {
        const r = el.getBoundingClientRect();
        const outOfView = r.top < 72 || r.bottom > window.innerHeight - 72;
        if (outOfView) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }

    let raf = 0;
    let stopped = false;
    const startedAt = performance.now();
    const loop = () => {
      if (stopped) return;
      measure();
      if (performance.now() - startedAt < SETTLE_MS) raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);

    const onViewportChange = () => measure();
    window.addEventListener('resize', onViewportChange);
    window.addEventListener('scroll', onViewportChange, true);

    return () => {
      stopped = true;
      cancelAnimationFrame(raf);
      window.removeEventListener('resize', onViewportChange);
      window.removeEventListener('scroll', onViewportChange, true);
    };
  }, [mine, step, target, measure]);

  // 容错：当前步的目标不在 DOM 中 → 自动跳过（延后一拍执行，避免在渲染期同步改 store）
  const shouldSkip = mine && geo?.step === currentStep && geo.missing;
  useEffect(() => {
    if (!shouldSkip) return;
    const timer = window.setTimeout(() => useTourStore.getState().nextStep(), 0);
    return () => window.clearTimeout(timer);
  }, [shouldSkip, currentStep]);

  // 键盘操作：Esc 关闭，← → 翻页
  useEffect(() => {
    if (!mine) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.preventDefault(); dismiss(); }
      else if (e.key === 'ArrowRight') { e.preventDefault(); nextStep(); }
      else if (e.key === 'ArrowLeft') { e.preventDefault(); prevStep(); }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [mine, dismiss, nextStep, prevStep]);

  // 引导进行中禁止页面滚动，避免高亮框与内容脱节
  useEffect(() => {
    if (!mine || typeof document === 'undefined') return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = prev; };
  }, [mine]);

  // ---- 以下为渲染判定：所有 hooks 已在上方无条件执行 ----

  if (!mine || !step) return null;
  // SSR / 无 DOM 环境
  if (typeof document === 'undefined' || typeof window === 'undefined') return null;
  // 移动端不展示
  if (window.innerWidth < TOUR_MOBILE_BREAKPOINT) return null;
  // 首帧尚未测量，或快照属于上一步 —— 等下一帧，避免闪一下错误位置
  if (!geo || geo.step !== currentStep) return null;
  // 目标缺失：交给上面的副作用跳过，这一帧什么都不画
  if (geo.missing) return null;

  const { rect, vw, vh, cardH } = geo;
  const clipPath = buildClipPath(rect);
  const card = computeCardPosition(rect, step.placement, vw, vh, cardH);
  const isFirst = currentStep === 0;
  const isLast = currentStep === totalSteps - 1;

  return createPortal(
    <div
      className="fixed inset-0"
      style={{ zIndex: 9998 }}
      role="dialog"
      aria-modal="true"
      aria-label={tTour(`steps.${step.i18nKey}.title`)}
    >
      {/* 聚光灯遮罩：clip-path 挖空目标区域，点击遮罩不误关引导 */}
      <div
        className="absolute inset-0 transition-[clip-path] duration-200 ease-out"
        style={{ background: 'var(--tour-mask)', clipPath }}
      />

      {/* 高亮描边：目标外扩 8px 的品牌色圆角框 */}
      {rect && (
        <div
          className="pointer-events-none absolute rounded-lg ring-2 ring-brand transition-all duration-200 ease-out"
          style={{
            top: rect.top - SPOT_PAD,
            left: rect.left - SPOT_PAD,
            width: rect.width + SPOT_PAD * 2,
            height: rect.height + SPOT_PAD * 2,
            boxShadow: '0 0 0 6px var(--tour-glow)',
          }}
        />
      )}

      {/* 气泡卡片 */}
      <div
        ref={setCardEl}
        className={cn(
          'absolute rounded-xl border bg-popover text-popover-foreground shadow-2xl',
          'animate-in fade-in-0 zoom-in-95 duration-200',
        )}
        style={{ top: card.top, left: card.left, width: card.width }}
      >
        <button
          type="button"
          onClick={dismiss}
          aria-label={tTour('close')}
          className="absolute right-2 top-2 rounded-md p-1 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
        >
          <X className="size-4" />
        </button>

        <div className="px-5 pb-4 pt-5">
          <h3 className="pr-6 text-base font-semibold leading-snug text-foreground">
            {tTour(`steps.${step.i18nKey}.title`)}
          </h3>
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
            {tTour(`steps.${step.i18nKey}.description`)}
          </p>
        </div>

        <div className="flex items-center justify-between gap-3 border-t px-5 py-3">
          {/* 进度点：可点击直达任意一步 */}
          <div className="flex items-center gap-1.5" aria-label={tTour('progress', { current: currentStep + 1, total: totalSteps })}>
            {Array.from({ length: totalSteps }).map((_, i) => (
              <button
                key={i}
                type="button"
                onClick={() => goToStep(i)}
                aria-label={tTour('progress', { current: i + 1, total: totalSteps })}
                className={cn(
                  'h-1.5 rounded-full transition-all',
                  i === currentStep ? 'w-5 bg-brand' : 'w-1.5 bg-border hover:bg-muted-foreground/50',
                )}
              />
            ))}
          </div>

          <div className="flex items-center gap-2">
            {isFirst ? (
              <button
                type="button"
                onClick={dismiss}
                className="rounded-md px-2.5 py-1.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
              >
                {tTour('skip')}
              </button>
            ) : (
              <button
                type="button"
                onClick={prevStep}
                className="rounded-md border px-3 py-1.5 text-xs font-medium transition-colors hover:bg-accent"
              >
                {tTour('prev')}
              </button>
            )}
            <button
              type="button"
              onClick={nextStep}
              className="rounded-md bg-brand px-3.5 py-1.5 text-xs font-medium text-brand-foreground shadow-sm transition-colors hover:bg-brand-hover"
            >
              {isLast ? tTour('finish') : tTour('next')}
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}
