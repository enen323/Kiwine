import { create } from 'zustand';

/* =========================================================================
 * 新手引导 —— 第 1 层：状态层（全局 Store）
 *
 * 职责：
 *   1. 维护「当前是哪一套引导、进行到第几步、共几步」；
 *   2. 通过本地存储做「只自动展示一次」的去重判断。
 *
 * 去重约定：localStorage key = `app_tour_${tourId}_completed`，值为 '1'。
 * 走完最后一步（nextStep）或主动关闭（dismiss）时写入。
 * ========================================================================= */

const STORAGE_PREFIX = 'app_tour_';
const STORAGE_SUFFIX = '_completed';

/** 引导完成标记的本地存储 key */
const storageKey = (tourId: string) => `${STORAGE_PREFIX}${tourId}${STORAGE_SUFFIX}`;

/** SSR 安全：只有在浏览器环境才访问 window / localStorage */
const isClient = () => typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';

/**
 * 该套引导是否已被看过（看完或跳过）。
 * 非浏览器环境统一返回 true —— 服务端不触发引导。
 * localStorage 被禁用（隐私模式）时同样返回 true，宁可不弹也不要每次都弹。
 */
export function hasCompletedTour(tourId: string): boolean {
  if (!isClient()) return true;
  try {
    return window.localStorage.getItem(storageKey(tourId)) === '1';
  } catch {
    return true;
  }
}

/** 写入完成标记 */
function markTourCompleted(tourId: string): void {
  if (!isClient()) return;
  try {
    window.localStorage.setItem(storageKey(tourId), '1');
  } catch {
    /* 存储不可用时忽略：本次会话内引导仍会正常结束，只是下次可能再弹一次 */
  }
}

/** 清除某套引导的完成标记（用于「重新观看引导」） */
export function resetTour(tourId: string): void {
  if (!isClient()) return;
  try {
    window.localStorage.removeItem(storageKey(tourId));
  } catch {
    /* ignore */
  }
}

/** 清除全部引导的完成标记（用于设置页的「重新观看新手引导」） */
export function resetAllTours(): void {
  if (!isClient()) return;
  try {
    const keys: string[] = [];
    for (let i = 0; i < window.localStorage.length; i += 1) {
      const k = window.localStorage.key(i);
      if (k && k.startsWith(STORAGE_PREFIX) && k.endsWith(STORAGE_SUFFIX)) keys.push(k);
    }
    keys.forEach((k) => window.localStorage.removeItem(k));
  } catch {
    /* ignore */
  }
}

/** 视口宽度小于该值视为移动端，不展示引导 */
export const TOUR_MOBILE_BREAKPOINT = 768;

/** 当前视口是否允许展示引导（移动端禁用） */
export function isTourViewportAllowed(): boolean {
  if (!isClient()) return false;
  return window.innerWidth >= TOUR_MOBILE_BREAKPOINT;
}

/**
 * 综合判断：是否应该自动启动某套引导。
 * 供触发层（各页面）在数据加载完成后调用。
 */
export function shouldAutoStartTour(tourId: string): boolean {
  return isTourViewportAllowed() && !hasCompletedTour(tourId);
}

interface TourStore {
  /** 引导是否正在进行 */
  isActive: boolean;
  /** 当前是哪一套引导（用于区分多套引导，渲染层据此判断是否是自己） */
  activeTourId: string | null;
  /** 当前第几步（从 0 开始） */
  currentStep: number;
  /** 总步数 */
  totalSteps: number;

  /** 启动某套引导 */
  startTour: (tourId: string, totalSteps: number) => void;
  /** 下一步；已是最后一步则标记完成并关闭 */
  nextStep: () => void;
  /** 上一步（不低于 0） */
  prevStep: () => void;
  /** 跳到指定步（用于进度点直达） */
  goToStep: (index: number) => void;
  /** 关闭引导（视为已完成，下次不再自动弹出） */
  dismiss: () => void;
}

const CLOSED = { isActive: false, activeTourId: null, currentStep: 0, totalSteps: 0 } as const;

export const useTourStore = create<TourStore>((set, get) => ({
  ...CLOSED,

  startTour: (tourId, totalSteps) => {
    if (totalSteps <= 0) return;
    set({ isActive: true, activeTourId: tourId, currentStep: 0, totalSteps });
  },

  nextStep: () => {
    const { isActive, activeTourId, currentStep, totalSteps } = get();
    if (!isActive) return;
    if (currentStep + 1 >= totalSteps) {
      if (activeTourId) markTourCompleted(activeTourId);
      set({ ...CLOSED });
      return;
    }
    set({ currentStep: currentStep + 1 });
  },

  prevStep: () => {
    const { isActive, currentStep } = get();
    if (!isActive) return;
    set({ currentStep: Math.max(0, currentStep - 1) });
  },

  goToStep: (index) => {
    const { isActive, totalSteps } = get();
    if (!isActive) return;
    set({ currentStep: Math.min(Math.max(0, index), totalSteps - 1) });
  },

  dismiss: () => {
    const { isActive, activeTourId } = get();
    if (!isActive) return;
    if (activeTourId) markTourCompleted(activeTourId);
    set({ ...CLOSED });
  },
}));
