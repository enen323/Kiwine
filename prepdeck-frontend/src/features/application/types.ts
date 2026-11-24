// 投递记录（JobApplication）领域类型与常量
// 契约已对齐后端 job-service-api.md（v1.0）。

export type ApplicationStatus =
  | 'applied'
  | 'interviewing'
  | 'offered'
  | 'rejected'
  | 'withdrawn';

export interface JobApplication {
  id: string;
  company: string;
  position: string;
  status: ApplicationStatus;
  channel: string | null;
  resumeId: string | null;
  resumeTitle: string | null; // 后端 join 出来的简历标题
  appliedAt: string; // ISO 字符串
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

// 列表查询参数（与 URL searchParams 一一对应）
export interface JobApplicationListQuery {
  status?: string;
  keyword?: string;
  page?: number;
  pageSize?: number;
}

// 创建与更新载荷结构完全一致，合并为一个类型
export interface JobApplicationPayload {
  company: string;
  position: string;
  status: string;
  channel?: string | null;
  resumeId?: string | null;
  appliedAt?: string | null;
  notes?: string | null;
}

// 关联简历下拉选项
export interface ResumeOption {
  id: string;
  title: string;
}

// 列表分页响应（后端 GET /applications 实际返回结构：items / total / page / pageSize / totalPages）
export interface JobApplicationPage {
  items: JobApplication[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

// 投递渠道建议值（供 AutoComplete 使用，用户也可自由输入）
// 文档示例渠道：Boss直聘 / 智联招聘 / 猎聘 / 牛客
export const DEFAULT_CHANNELS = [
  'Boss直聘',
  '智联招聘',
  '猎聘',
  '牛客',
  '前程无忧',
  '58同城',
  '拉勾',
];

// ===== AI 求职信（job-service-api.md §二）=====

/** 输出语言：中文 / 英文 */
export type CoverLetterLanguage = 'CHINESE' | 'ENGLISH';

/** AI 求职信数据模型 AiCoverLetter */
export interface AiCoverLetter {
  id: string;
  resumeId: string;
  /** 关联投递记录 ID（可空） */
  applicationId: string | null;
  company: string;
  position: string;
  /** JD 原文（可空） */
  jobDescription: string | null;
  /** 用户补充说明（可空） */
  extraNotes: string | null;
  outputLanguage: CoverLetterLanguage;
  title: string;
  body: string;
  createdAt: string;
  updatedAt: string;
}

/** 生成求职信请求体（POST /applications/{id}/cover-letters） */
export interface GenerateCoverLetterPayload {
  company: string;
  position: string;
  jobDescription?: string;
  extraNotes?: string;
  outputLanguage: CoverLetterLanguage;
}

/** 更新求职信请求体（仅改元数据，不重新生成内容） */
export type UpdateCoverLetterPayload = GenerateCoverLetterPayload;

// 状态文案（中文）
export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  applied: '已投递',
  interviewing: '面试中',
  offered: '已通过',
  rejected: '已拒绝',
  withdrawn: '已放弃',
};

// 状态配色（映射到 shadcn Badge 的语义 variant + 自定义样式）
// 设计：applied=蓝 / interviewing=金 / offered=绿 / rejected=红 / withdrawn=灰
export const STATUS_BADGE_CLASS: Record<ApplicationStatus, string> = {
  applied: 'bg-blue-50 text-blue-600 border-blue-200 dark:bg-blue-950/40 dark:text-blue-300 dark:border-blue-900',
  interviewing: 'bg-amber-50 text-amber-600 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-900',
  offered: 'bg-green-50 text-green-600 border-green-200 dark:bg-green-950/40 dark:text-green-300 dark:border-green-900',
  rejected: 'bg-red-50 text-red-600 border-red-200 dark:bg-red-950/40 dark:text-red-300 dark:border-red-900',
  withdrawn: 'bg-muted text-muted-foreground border-border',
};

export const STATUS_OPTIONS: ApplicationStatus[] = [
  'applied',
  'interviewing',
  'offered',
  'rejected',
  'withdrawn',
];
