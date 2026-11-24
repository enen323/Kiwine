// ===== API Response Types =====
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

// 后端 PageResult<T>：内嵌在 Result.data 中（即 Result<PageResult<T>> = { code, message, data: { data: T[], page, pageSize, total } }）。
// 见 question-service-api.md §1/§4.1 与 interview/resume 模块用法（res.data.data.data = 列表）。
export interface PageResult<T> {
  data: T[];
  page: number;
  pageSize: number;
  total: number;
}

// 兼容旧名，等同于 PageResult
export type PageResponse<T> = PageResult<T>;

// 简历分段（sections）结构，对应后端 SectionVO
export interface ResumeSection {
  id?: string | number;
  resumeId?: string | number;
  type?: string;
  title?: string;
  content?: string;
  sortOrder?: number;
  visible?: boolean;
}

// 编辑器使用的富结构分段（content 为结构化对象，持久化时序列化为字符串）
import type { EditorSection, SerializedSection, ThemeConfig } from './editor';
export type { EditorSection, SerializedSection, ThemeConfig };

// 用户 AI 设置（PUT /users/settings）。注意：apiKey 不落库，前端也不保存
export interface UserAISettings {
  provider?: string;
  baseUrl?: string;
  model?: string;
}

// ===== Auth Types =====
export interface LoginRequest {
  phone: string;
  password: string;
}

export interface RegisterRequest {
  phone: string;
  password: string;
  nickname: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserVO;
}

// ===== User Types =====
export interface UserVO {
  id: string | number;
  phone: string;
  nickname: string;
  avatarUrl: string | null;
  targetPosition: string | null;
  targetCompany: string | null;
  createTime: string;
}

export interface UpdateProfileRequest {
  nickname?: string;
  targetPosition?: string;
  targetCompany?: string;
}

// ===== Resume Types =====
export interface ResumeVO {
  id: string | number;
  userId: string | number;
  fileName: string;
  fileUrl: string | null;
  contentText: string | null;
  contentJson: string | null;
  isActive: boolean;
  isPublic?: boolean;
  shareToken?: string;
  viewCount?: number;
  title?: string;
  template?: string;
  language?: string;
  themeConfig?: ThemeConfig;
  sections?: EditorSection[];
  createTime: string;
  updateTime: string;
}

export interface CreateResumeRequest {
  fileName?: string;
  title?: string;
  contentText?: string;
  contentJson?: string;
  template?: string;
  language?: string;
  sections?: ResumeSection[];
  /** AI 对话生成简历时的会话 ID，成功后后端清理 Redis 中的已完成结果 */
  sessionId?: string;
}

// ===== AI 对话式简历生成（/resumes/ai-chat） =====
export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  /** 仅 assistant 消息可能携带：可点击快速回答的预置选项 */
  options?: string[];
}

/** AI 对话结束后 `event: complete` 返回的完整简历数据，等价于 CreateResumeRequest 的核心字段 */
export interface AiChatComplete {
  title: string;
  contentText: string;
  contentJson?: string;
  sections?: Array<{ type: string; content: string }>;
}

export interface UpdateResumeRequest {
  fileName?: string;
  title?: string;
  contentText?: string;
  contentJson?: string;
  template?: string;
  language?: string;
  themeConfig?: ThemeConfig;
  isPublic?: boolean;
  isActive?: boolean;
  sections?: SerializedSection[];
}

export type ResumeSectionType =
  | 'personalInfo'
  | 'summary'
  | 'education'
  | 'experience'
  | 'project'
  | 'skills'
  | 'certification'
  | 'language'
  | 'publication'
  | 'custom';

export interface PolishRequest {
  sectionType?: ResumeSectionType;
  rawText?: string;
  /**
   * 传输格式提示（对齐 JadeAI 的整数组 JSON 方案）：
   * - 'text'：rawText 为纯文本，后端返回纯文本（用于 summary.text / description 等字符串字段）。
   * - 'json'：rawText 为 JSON.stringify(string[])，后端应返回 JSON.stringify(string[])（用于 highlights / technologies / skills 等数组字段）。
   */
  format?: 'text' | 'json';
  resumeId?: string | number;
  sectionTypes?: ResumeSectionType[];
}

export interface PolishedSection {
  sectionType: string;
  polishedText: string;
}

export interface PolishResponse {
  polishedText: string | null;
  sections: PolishedSection[] | null;
}

// AI 生成简历接口返回的结果（POST /api/ai/generate-resume）
export interface GenerateResumeResult {
  resumeId: string | number;
  title: string;
}

// ===== Interview Types =====
export type CompanyType =
  | 'BYTE_DANCE' | 'ALIBABA' | 'TENCENT'
  | 'BAIDU' | 'MEITUAN' | 'PDD';

export type PositionType =
  | 'BACKEND' | 'FRONTEND' | 'ALGORITHM'
  | 'CLIENT' | 'PRODUCT' | 'OPERATIONS'
  | 'JAVA_DEVELOPER';

export type InterviewStatus =
  | 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'TERMINATED';

export type InterviewStage =
  | 'SELF_INTRO' | 'PROJECT_DEEP_DIVE'
  | 'TECH_ASSESSMENT' | 'REVERSE_QA' | 'COMPLETED';

export type MessageRole = 'AI' | 'USER' | 'SYSTEM';

/** 面试问题类型（对应文档 QuestionTypeEnum） */
export type InterviewQuestionType =
  | 'SELF_INTRO' | 'TECH_PROJECT' | 'TECH_BASIC'
  | 'ALGORITHM' | 'SYSTEM_DESIGN' | 'BEHAVIORAL' | 'REVERSE_QA';

/** 面试问题类型中文标签 */
export const InterviewQuestionTypeMap: Record<InterviewQuestionType, string> = {
  SELF_INTRO: '自我介绍',
  TECH_PROJECT: '项目相关',
  TECH_BASIC: '技术基础',
  ALGORITHM: '算法',
  SYSTEM_DESIGN: '系统设计',
  BEHAVIORAL: '行为面试',
  REVERSE_QA: '反问环节',
};

/** 控制指令（对应文档 ControlCommand） */
export type ControlCommand = 'SKIP' | 'HINT' | 'END';

/** 控制指令请求体 */
export interface ControlRequest {
  command: ControlCommand;
}

/** 控制指令中文标签 */
export const ControlCommandMap: Record<ControlCommand, string> = {
  SKIP: '跳过此题',
  HINT: '思路提示',
  END: '提前结束',
};

export interface StartInterviewRequest {
  companyType: CompanyType;
  position: PositionType;
  resumeId?: string | number;
}

export interface InterviewVO {
  id: string | number;
  userId: string | number;
  company: string;
  companyDisplayName: string;
  position: string;
  positionDisplayName: string;
  status: InterviewStatus;
  currentStage: InterviewStage;
  stageOrder: number;
  totalQuestions: number;
  scoreTotal: number | null;
  scoreTech: number | null;
  scoreExpression: number | null;
  scoreProject: number | null;
  scoreLogic: number | null;
  scoreAdaptability: number | null;
  suggestion: string | null;
  startedAt: string | null;
  completedAt: string | null;
  durationSeconds: number | null;
  /** 文档统一为 createdAt；createTime 仅作旧字段兼容 */
  createTime?: string;
  createdAt?: string;
}

/**
 * 面试列表项（对应文档 InterviewListVO）。
 * 是 InterviewVO 的精简子集——列表接口 GET / 返回此类型。
 */
export interface InterviewListVO {
  id: string | number;
  company: string;
  position: string;
  status: InterviewStatus;
  currentStage: InterviewStage;
  totalQuestions: number;
  scoreTotal: number | null;
  startedAt: string | null;
  completedAt: string | null;
  durationSeconds: number | null;
  createdAt: string;
}

export interface InterviewMessage {
  id: string | number;
  interviewId: string | number;
  role: MessageRole;
  content: string;
  questionType: InterviewQuestionType | null;
  sequenceNum: number;
  tokenCount: number;
  createdAt: string;
}

export interface SubmitAnswerRequest {
  content: string;
}

/**
 * 提交回答 SSE 事件数据（对应文档 4.2 `ai_response` 事件）。
 * 每次 SSE 事件推送一个完整对象；面试结束时 scoreTotal / suggestion 才有值。
 */
export interface SubmitAnswerResponse {
  interview: InterviewVO;
  userAnswer: InterviewMessage;
  aiResponse: InterviewMessage;
  stageCompleted: boolean;
  interviewCompleted: boolean;
  /** 当前总评分（面试结束时才有值） */
  scoreTotal: number | null;
  /** 综合评价建议（面试结束时才有值） */
  suggestion: string | null;
}

/**
 * 开始面试响应中携带的简历画像（对应文档 `resumeProfile`）。
 * 未关联简历时各字段为空。
 */
export interface ResumeProfileEducation {
  field: string;
  highlights: string[];
}

export interface ResumeProfileProject {
  name: string;
  technologies: string[];
  highlights: string[];
}

export interface ResumeProfileSkill {
  name: string;
  skills: string[];
}

export interface ResumeProfile {
  summary: string;
  education: ResumeProfileEducation[];
  projects: ResumeProfileProject[];
  skills: ResumeProfileSkill[];
}

export interface StartInterviewResponse {
  interview: InterviewVO;
  firstQuestion: InterviewMessage;
  /** 简历画像；未关联简历时为空结构 */
  resumeProfile: ResumeProfile;
}

// ===== Enums =====
export const CompanyEnum: Record<CompanyType, string> = {
  BYTE_DANCE: '字节跳动',
  ALIBABA: '阿里巴巴',
  TENCENT: '腾讯',
  BAIDU: '百度',
  MEITUAN: '美团',
  PDD: '拼多多',
};

export const PositionEnum: Record<PositionType, string> = {
  BACKEND: '后端开发',
  FRONTEND: '前端开发',
  ALGORITHM: '算法工程师',
  CLIENT: '客户端开发',
  PRODUCT: '产品经理',
  OPERATIONS: '运营',
  JAVA_DEVELOPER: 'Java开发工程师',
};

export const InterviewStatusMap: Record<InterviewStatus, string> = {
  CREATED: '已创建',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  TERMINATED: '已终止',
};

export const InterviewStageMap: Record<InterviewStage, string> = {
  SELF_INTRO: '自我介绍',
  PROJECT_DEEP_DIVE: '项目深挖',
  TECH_ASSESSMENT: '技术考核',
  REVERSE_QA: '反问环节',
  COMPLETED: '面试结束',
};

export const StageOrder: InterviewStage[] = [
  'SELF_INTRO',
  'PROJECT_DEEP_DIVE',
  'TECH_ASSESSMENT',
  'REVERSE_QA',
];

// ===== Quota & Payment Types =====

/** 服务类型（配额/支付维度） */
export type ServiceType = 'INTERVIEW' | 'RESUME_CHAT';

/** 配额类型：免费额度 / 付费额度 */
export type QuotaType = 'FREE' | 'PAID';

/** 配额服务错误码 */
export const QUOTA_ERROR_CODES = {
  /** 本月免费额度已用完 */
  FREE_EXHAUSTED: 70000,
  /** 需要付费解锁 */
  NEED_PAID: 70001,
  /** 支付凭证无效或已过期 */
  PAYMENT_INVALID: 70002,
  /** 配额操作失败，请重试 */
  OPERATION_FAILED: 70003,
} as const;

/** 面试服务错误码（Sentinel 熔断 / AI 异常 / 状态守卫） */
export const INTERVIEW_ERROR_CODES = {
  /** 面试已结束（提交回答 / 控制指令被守卫拒绝，verifyStatusForAnswer 抛 50001） */
  INTERVIEW_ALREADY_COMPLETED: 50001,
  /** AI 服务异常（DeepSeek 不可用） */
  LLM_SERVICE_ERROR: 50003,
  /** 远程服务不可用（Feign 调用熔断降级） */
  SERVICE_UNAVAILABLE: 90001,
} as const;

/** POST /quota/consume 请求参数 */
export interface ConsumeQuotaRequest {
  userId: string | number;
  serviceType: ServiceType;
}

/** POST /quota/consume 响应 data（检查并扣减，后端自动完成扣减） */
export interface QuotaCheckResult {
  /** 是否允许使用（免费或付费额度任一可用即为 true） */
  allowed: boolean;
  /** 命中的配额类型：FREE（免费额度）或 PAID（付费额度）；allowed=false 时为 null */
  type: QuotaType | null;
  /** 拒绝原因；allowed=true 时为 null */
  reason: string | null;
  /** 付费价格（元）；allowed=false 时返回应付价格，allowed=true 时为 null */
  price: number | null;
}

/** GET /quota/{userId} 响应 data */
export interface UserQuotaVO {
  userId: string | number;
  /** 面试免费额度已使用次数（每月 1 日 0 点重置） */
  interviewFreeUsed: number;
  /** 简历对话免费额度已使用次数（每月 1 日 0 点重置） */
  resumeChatFreeUsed: number;
  /** 面试付费额度剩余次数 */
  interviewPaid: number;
  /** 简历对话付费额度剩余次数 */
  resumeChatPaid: number;
}

/** POST /payment/charge 请求体 */
export interface ChargePaymentRequest {
  userId: string | number;
  serviceType: ServiceType;
}

/** POST /payment/charge 响应 data（微信 JSSDK 调起参数） */
export interface ChargePaymentResponse {
  appId: string;
  /** 时间戳（秒，字符串） */
  timeStamp: string;
  nonceStr: string;
  /** 固定格式 `prepay_id=<outTradeNo>` */
  packageValue: string;
  /** 签名类型，固定 RSA */
  signType: string;
  paySign: string;
  /** 支付金额（元） */
  amount: string;
  serviceType: ServiceType;
}

/** 服务类型中文标签 */
export const SERVICE_TYPE_LABELS: Record<ServiceType, string> = {
  INTERVIEW: '模拟面试',
  RESUME_CHAT: '简历对话',
};

/** 每月免费额度（INTERVIEW 与 RESUME_CHAT 分别计算） */
export const FREE_QUOTA_PER_MONTH = 3;

/** 服务价格表（元），仅用于前端展示 */
export const SERVICE_PRICE: Record<ServiceType, number> = {
  INTERVIEW: 1.99,
  RESUME_CHAT: 0.99,
};

// ===== Report Types（Report Service /api/v1/reports） =====

/** 五维维度固定顺序（雷达图轴向按此稳定排列） */
export const DIMENSION_ORDER = [
  '技术深度',
  '表达能力',
  '项目经验',
  '逻辑思维',
  '应变能力',
] as const;
export type DimensionName = (typeof DIMENSION_ORDER)[number];

/** 单维平均分（DimensionScore） */
export interface DimensionScore {
  /** 维度名称：技术深度 / 表达能力 / 项目经验 / 逻辑思维 / 应变能力 */
  name: string;
  /** 该维度平均分（保留 1 位小数） */
  score: number;
  /** 排名（1=最高） */
  rank: number;
}

/** 单题洞察（QuestionInsight） */
export interface QuestionInsight {
  /** AI 提炼的题目考察点 */
  description: string;
  /** 该题得分 */
  score: number;
  /** AI 对该题的反馈意见 */
  feedback: string;
}

/** 强弱项洞察（AreaInsight） */
export interface AreaInsight {
  /** 维度名称 */
  dimension: string;
  /** 该维度平均分 */
  avgScore: number;
  /** 该维度下最具代表性的题目（最多 3 条） */
  questions: QuestionInsight[];
}

/** 趋势点（TrendPoint） */
export interface TrendPoint {
  /** 面试完成日期，格式 yyyy-MM-dd */
  date: string;
  /** 该场面试总分 */
  score: number;
}

/** 综合改进建议（ImprovementSuggestion） */
export interface ImprovementSuggestion {
  /** 薄弱维度名称 */
  dimension: string;
  /** 针对该维度的改进建议文案 */
  suggestion: string;
}

/** 总体统计（OverallSummary） */
export interface OverallSummary {
  /** 纳入统计的面试场次 */
  totalInterviews: number;
  /** 平均总分（保留 1 位小数） */
  avgScore: number;
  /** 最高总分 */
  highestScore: number;
  /** 最低总分 */
  lowestScore: number;
  /** 总面试时长（秒） */
  totalDurationSeconds: number;
}

/** 五维雷达（DimensionScores） */
export interface DimensionScores {
  dimensions: DimensionScore[];
}

/** 擅长区（StrongAreas） */
export interface StrongAreas {
  insights: AreaInsight[];
}

/** 薄弱区（WeakAreas） */
export interface WeakAreas {
  insights: AreaInsight[];
}

/** 趋势分析（TrendData） */
export interface TrendData {
  points: TrendPoint[];
}

/** 综合建议（ImprovementSuggestions） */
export interface ImprovementSuggestions {
  items: ImprovementSuggestion[];
}

/** 面试成长报告（generate 响应 data 全量） */
export interface InterviewReport {
  summary: OverallSummary;
  dimensionScores: DimensionScores;
  strongAreas: StrongAreas;
  weakAreas: WeakAreas;
  trend: TrendData;
  suggestions: ImprovementSuggestions;
}

/** 生成报告请求体（GenerateReportRequest） */
export interface GenerateReportRequest {
  /** 筛选开始时间，ISO-8601；与 interviewIds 二选一 */
  startDate?: string;
  /** 筛选结束时间，ISO-8601 */
  endDate?: string;
  /** 指定面试 ID 列表；null 或空数组则取用户全量已完成面试 */
  interviewIds?: Array<string | number>;
}

/** 可参与报告的面试选项（InterviewOption，GET /reports/interviews/available） */
export interface InterviewOption {
  /** 面试记录 ID */
  id: string | number;
  /** 目标公司（英文标识，如 ALIBABA） */
  company: string;
  /** 目标职位（英文标识，如 BACKEND） */
  position: string;
  /** 面试完成时间，ISO-8601 */
  completedAt: string;
  /** 该场面试总分 */
  scoreTotal: number | null;
}

// ===== Question Service Types（考题服务 /api/v1/questions、/api/v1/tags） =====
export * from './question';
