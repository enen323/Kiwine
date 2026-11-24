import { useEffect } from 'react';
import { useTourStore, shouldAutoStartTour } from '@/stores/tour-store';

/* =========================================================================
 * 新手引导 —— 第 2 层：触发层
 *
 * 把「等数据就绪 → 判断是否看过 → 判断视口 → 延迟启动 → 卸载清理」
 * 这套固定逻辑收敛成一个 hook，页面接入只需一行。
 * ========================================================================= */

/** 默认延迟：避开首屏入场动画（Reveal / framer-motion）与布局抖动 */
const DEFAULT_DELAY = 900;

export interface UseTourTriggerOptions {
  /**
   * 本页数据是否已就绪。为 false 时不启动，
   * 避免引导出现在骨架屏 / 空列表上导致目标元素还没渲染。
   */
  ready?: boolean;
  /** 延迟毫秒数，默认 900ms */
  delay?: number;
  /** 额外的启动开关（例如仅登录用户才引导） */
  enabled?: boolean;
}

/**
 * 自动启动某套引导（只会自动启动一次，看完或跳过后不再打扰）。
 *
 * @param tourId     引导 id，与 `<TourOverlay tourId>` 一致
 * @param totalSteps 该套引导的步数，通常传 `STEPS.length`
 */
export function useTourTrigger(
  tourId: string,
  totalSteps: number,
  { ready = true, delay = DEFAULT_DELAY, enabled = true }: UseTourTriggerOptions = {},
): void {
  useEffect(() => {
    if (!enabled || !ready || totalSteps <= 0) return;
    // 已看过 / 移动端 / 非浏览器环境 → 不启动
    if (!shouldAutoStartTour(tourId)) return;

    const timer = window.setTimeout(() => {
      // 延迟期间可能已有别的引导被启动，避免相互覆盖
      if (useTourStore.getState().isActive) return;
      if (!shouldAutoStartTour(tourId)) return;
      useTourStore.getState().startTour(tourId, totalSteps);
    }, delay);

    return () => window.clearTimeout(timer);
  }, [tourId, totalSteps, ready, delay, enabled]);
}

/**
 * 手动启动某套引导（忽略「已看过」标记），用于「重新观看引导」按钮。
 * 移动端仍然不启动。
 */
export function startTourManually(tourId: string, totalSteps: number): boolean {
  if (typeof window === 'undefined') return false;
  if (window.innerWidth < 768) return false;
  useTourStore.getState().startTour(tourId, totalSteps);
  return true;
}
