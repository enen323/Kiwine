// ===== 题型 / 难度 / 易错 =====
// 注意：后端 question 表种子数据未遵循 schema 的英文枚举约定，
// type/difficulty 实际存储中文值，easyMistake 存的是易错点描述文本。
// 前端按 string 适配：中文值原样展示/透传筛选，同时兼容英文枚举
// （将来后端数据归一为枚举后前端无需再改）。
export type QuestionType = string;
export type QuestionDifficulty = string;
export type EasyMistake = string;

/** 英文枚举 → 中文标签（兼容后端将来归一为枚举值的情况；未知/中文值原样返回）。 */
const TYPE_ENUM_LABELS: Record<string, string> = {
  SCENARIO: '场景题',
  ALGORITHM: '算法题',
  SYSTEM_DESIGN: '系统设计',
  BEHAVIORAL: '行为面试',
  LANGUAGE: '语言基础',
};

const DIFFICULTY_ENUM_LABELS: Record<string, string> = {
  EASY: '简单',
  MEDIUM: '中等',
  HARD: '困难',
};

/** 题型显示文案：中文值直通，英文枚举映射为中文。 */
export function questionTypeLabel(type: string): string {
  return TYPE_ENUM_LABELS[type] ?? type;
}

/** 难度显示文案：中文值直通，英文枚举映射为中文。 */
export function questionDifficultyLabel(difficulty: string): string {
  return DIFFICULTY_ENUM_LABELS[difficulty] ?? difficulty;
}

/** 后端题库中实际存在的题型（知识点分类）——筛选下拉选项。 */
export const QUESTION_TYPE_OPTIONS: { value: string; label: string }[] = [
  'Java 基础',
  '集合框架',
  '多线程',
  'JVM',
  'Spring',
  'MyBatis-Plus',
  'MySQL',
  'Redis',
  '消息队列',
  '分布式',
  '系统设计',
  '算法',
  '网络',
  '安全',
  'Docker',
].map((v) => ({ value: v, label: v }));

/** 难度筛选下拉选项。 */
export const QUESTION_DIFFICULTY_OPTIONS: { value: string; label: string }[] = [
  '简单',
  '中等',
  '困难',
].map((v) => ({ value: v, label: v }));

export const QUESTION_SORT_OPTIONS: {
  value: 'heat' | 'views' | 'favorites' | 'interviewCount' | 'createTime';
  label: string;
}[] = [
  { value: 'heat', label: '最热门' },
  { value: 'views', label: '最多浏览' },
  { value: 'favorites', label: '最多收藏' },
  { value: 'interviewCount', label: '最多面试' },
  { value: 'createTime', label: '最新录入' },
];

export interface QuestionTag {
  id: number;
  name: string;
  description: string | null;
}

export interface QuestionAnswer {
  summary: string;
  analysis: string;
  code: string | null;
}

export interface QuestionDetailVO {
  id: string | number;
  title: string;
  content: string;
  answer: QuestionAnswer;
  type: QuestionType;
  difficulty: QuestionDifficulty;
  easyMistake: EasyMistake;
  interviewCount: number;
  views: number;
  favorites: number;
  tags: QuestionTag[];
  isFavorited: boolean;
  createTime: string;
}

export interface DailyQuestion {
  id: string | number;
  question: QuestionDetailVO;
  completed: boolean;
}

export interface QuestionListParams {
  page?: number;
  pageSize?: number;
  sortBy?: 'heat' | 'views' | 'favorites' | 'interviewCount' | 'createTime';
  order?: 'asc' | 'desc';
  type?: QuestionType;
  difficulty?: QuestionDifficulty;
  tagIds?: number[];
  keyword?: string;
}

// ===== 推荐引擎（question-service-api.md §3） =====
/** 推荐策略：薄弱项专项 / 目标公司风格 / 热门推荐。 */
export type RecommendStrategy = 'WEAK_AREA' | 'TARGET_COMPANY' | 'HOT';

const RECOMMEND_STRATEGY_LABELS: Record<RecommendStrategy, string> = {
  WEAK_AREA: '薄弱项专项',
  TARGET_COMPANY: '目标公司风格',
  HOT: '热门推荐',
};

/** 推荐策略显示文案。 */
export function recommendStrategyLabel(strategy: RecommendStrategy): string {
  return RECOMMEND_STRATEGY_LABELS[strategy] ?? strategy;
}

/** 推荐结果：`data.questions` 复用题目详情 VO。 */
export interface QuestionRecommendation {
  strategy: RecommendStrategy;
  message: string;
  questions: QuestionDetailVO[];
}
