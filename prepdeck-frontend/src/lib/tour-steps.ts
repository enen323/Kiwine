/* =========================================================================
 * 新手引导 —— 步骤配置（声明式）
 *
 * 每一步只声明三件事：高亮哪个元素、气泡朝哪个方向、用哪条文案。
 * 渲染层（TourOverlay）按数组顺序逐步展示，新增引导无需改动渲染层代码。
 *
 * target 省略时该步不做聚光灯，气泡居中展示（用于「欢迎/总览」这类步骤）。
 * target 对应的元素若不在 DOM 中，渲染层会自动跳过该步，不阻塞引导。
 * ========================================================================= */

export type TourPlacement = 'top' | 'bottom' | 'left' | 'right';

export interface TourStep {
  /** 目标元素的 data-tour 属性值；省略则气泡居中、无聚光灯 */
  target?: string;
  /** 气泡相对目标的方向；空间不足时渲染层会自动翻转 */
  placement?: TourPlacement;
  /** 对应文案 `steps.<i18nKey>.title` / `steps.<i18nKey>.description` */
  i18nKey: string;
}

/** 引导 id 常量：避免各页面手写字符串拼错 */
export const TOUR_IDS = {
  welcome: 'welcome',
  resumes: 'resumes',
  editor: 'editor',
  interviews: 'interviews',
  interviewStart: 'interview-start',
  profile: 'profile',
} as const;

export type TourId = (typeof TOUR_IDS)[keyof typeof TOUR_IDS];

/** 首页：全站欢迎引导。先讲清「免费额度」，再串起简历 → 面试 → 报告主链路 */
export const WELCOME_TOUR_STEPS: TourStep[] = [
  { i18nKey: 'welcome' },
  { target: 'nav-resumes', placement: 'bottom', i18nKey: 'navResumes' },
  { target: 'nav-interviews', placement: 'bottom', i18nKey: 'navInterviews' },
  { target: 'nav-reports', placement: 'bottom', i18nKey: 'navReports' },
  { target: 'nav-profile', placement: 'bottom', i18nKey: 'navQuota' },
];

/** 我的简历页：主推「AI 生成简历」，收尾把用户引向 AI 面试 */
export const RESUMES_TOUR_STEPS: TourStep[] = [
  { target: 'resume-ai-generate', placement: 'bottom', i18nKey: 'aiGenerate' },
  { target: 'resume-create', placement: 'bottom', i18nKey: 'create' },
  { target: 'resume-search', placement: 'bottom', i18nKey: 'search' },
  // 列表为空时该元素不存在，渲染层自动跳过
  { target: 'resume-list', placement: 'top', i18nKey: 'resumeList' },
  { target: 'nav-interviews', placement: 'bottom', i18nKey: 'gotoInterview' },
];

/** 简历编辑页：主推右下角「AI 帮你写」对话入口 */
export const EDITOR_TOUR_STEPS: TourStep[] = [
  { target: 'editor-ai-chat', placement: 'left', i18nKey: 'editorAiChat' },
  { target: 'editor-sidebar', placement: 'right', i18nKey: 'editorSidebar' },
  { target: 'editor-preview', placement: 'left', i18nKey: 'editorPreview' },
];

/** 面试记录页：主推「开始面试」 */
export const INTERVIEWS_TOUR_STEPS: TourStep[] = [
  { target: 'interview-start', placement: 'bottom', i18nKey: 'interviewStart' },
  { target: 'interview-filters', placement: 'bottom', i18nKey: 'interviewFilters' },
  { target: 'nav-reports', placement: 'bottom', i18nKey: 'interviewReports' },
];

/** 开始面试配置页：讲清「关联简历」的价值与额度消耗 */
export const INTERVIEW_START_TOUR_STEPS: TourStep[] = [
  { target: 'interview-config', placement: 'right', i18nKey: 'interviewConfig' },
  { target: 'interview-resume', placement: 'right', i18nKey: 'interviewResume' },
  { target: 'interview-submit', placement: 'top', i18nKey: 'interviewSubmit' },
];

/** 个人中心：解释免费额度的口径与重置规则 */
export const PROFILE_TOUR_STEPS: TourStep[] = [
  { target: 'quota-summary', placement: 'top', i18nKey: 'quota' },
];
