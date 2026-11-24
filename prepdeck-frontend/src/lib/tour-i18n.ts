import { FREE_QUOTA_PER_MONTH } from '@/types';

/* =========================================================================
 * 新手引导 —— 第 5 层：文案层（i18n）
 *
 * 本项目未引入 i18n 框架，这里用最小实现提供同等能力：
 *   - 命名空间固定为 `tour`，扁平 key（如 `steps.aiGenerate.title`）
 *   - `t(key, vars)` 支持 `{count}` 形式的占位符插值
 *   - 至少提供中、英两套文案，语言由 `resolveLocale()` 决定
 *
 * 新增一步引导时，只需在 zh-CN / en 两个字典的 `steps.<i18nKey>` 下
 * 补 `title` 与 `description`，渲染层无需改动。
 * ========================================================================= */

export type TourLocale = 'zh-CN' | 'en';

/** 语言偏好的本地存储 key（与未来接入的全局 i18n 共用同一约定） */
const LOCALE_STORAGE_KEY = 'app_locale';

/** SSR 安全的语言解析：本地存储优先，其次浏览器语言，兜底中文 */
export function resolveLocale(): TourLocale {
  if (typeof window === 'undefined') return 'zh-CN';
  try {
    const saved = window.localStorage.getItem(LOCALE_STORAGE_KEY);
    if (saved === 'en' || saved === 'zh-CN') return saved;
  } catch {
    /* 存储不可用则继续用浏览器语言判断 */
  }
  const nav = typeof navigator !== 'undefined' ? navigator.language : '';
  return nav && !nav.toLowerCase().startsWith('zh') ? 'en' : 'zh-CN';
}

type Dict = Record<string, string>;

const zhCN: Dict = {
  // ---- 通用按钮 ----
  next: '下一步',
  prev: '上一步',
  finish: '开始使用',
  skip: '跳过引导',
  close: '关闭引导',
  progress: '第 {current} / {total} 步',

  // ---- welcome：全站欢迎引导（首页） ----
  'steps.welcome.title': '欢迎来到 PrepDeck 👋',
  'steps.welcome.description':
    '用 AI 对话把经历写成简历，再让 AI 面试官对着你的简历真刀真枪面一轮。每月赠送 {count} 次免费 AI 简历对话 + {count} 次免费 AI 模拟面试，不用先付费也能完整体验一遍。花 30 秒看完这段引导，我带你走一圈。',
  'steps.navResumes.title': '第一步：写简历',
  'steps.navResumes.description':
    '「我的简历」是所有内容的起点。可以从模板新建，也可以直接把经历讲给 AI 听、由它整理成结构化简历。',
  'steps.navInterviews.title': '第二步：AI 模拟面试',
  'steps.navInterviews.description':
    '简历准备好后到「面试记录」发起模拟面试。选定目标公司和岗位，AI 面试官会读你的简历逐轮追问，答完当场给分和改进建议。',
  'steps.navReports.title': '第三步：看成长报告',
  'steps.navReports.description':
    '多轮面试之后，「成长报告」会把各维度得分汇总成雷达图和趋势曲线，直观看到自己哪块在进步、哪块还欠火候。',
  'steps.navQuota.title': '免费额度在这里查看',
  'steps.navQuota.description':
    '「个人中心」可以看到剩余额度：AI 简历对话与 AI 模拟面试每项每月各送 {count} 次，每月 1 号自动重置。免费次数用完后可按次充值，不会自动扣费。',

  // ---- resumes：我的简历页 ----
  'steps.aiGenerate.title': '推荐：让 AI 帮你生成简历',
  'steps.aiGenerate.description':
    '不知道怎么下笔就点这里。用大白话描述你的经历，AI 会自动拆成个人信息、工作经历、项目、技能等模块并润色成简历。本月还有免费额度，先拿它试试水。',
  'steps.create.title': '也可以从模板新建',
  'steps.create.description': '想自己写就点「新建简历」，挑一个中意的模板从空白开始填。模板随时可换，内容不会丢。',
  'steps.search.title': '简历多了就搜一下',
  'steps.search.description': '按标题或文件名搜索，右侧会显示简历总数。',
  'steps.resumeList.title': '设定「当前使用」的简历',
  'steps.resumeList.description':
    '点卡片进入编辑器；在「⋯」菜单里可以设为当前使用、复制副本或导出。AI 面试官默认会读取「使用中」的这份简历来提问。',
  'steps.gotoInterview.title': '简历好了就去练面试',
  'steps.gotoInterview.description': '简历完成后到「面试记录」开一场 AI 模拟面试，让它基于这份简历向你提问。',

  // ---- editor：简历编辑页 ----
  'steps.editorAiChat.title': 'AI 对话生成简历',
  'steps.editorAiChat.description':
    '这是编辑器里最省力的入口。点开后像聊天一样描述你的经历，AI 会实时把内容写进左边的简历模块；也可以让它改写某一段、压缩篇幅或针对某个岗位优化措辞。每月 {count} 次免费对话额度。',
  'steps.editorSidebar.title': '模块管理',
  'steps.editorSidebar.description': '在这里增删简历模块，画布里的模块支持拖拽排序。',
  'steps.editorPreview.title': '实时预览与导出',
  'steps.editorPreview.description': '右侧所见即所得，随改随更新。确认无误后可导出 PDF / HTML / TXT / JSON。',

  // ---- interviews：面试记录页 ----
  'steps.interviewStart.title': '开一场 AI 模拟面试',
  'steps.interviewStart.description':
    '点这里选目标公司和岗位，AI 面试官会按真实面试节奏分阶段提问、逐轮追问，结束后给出总分与逐项建议。每月 {count} 次免费机会。',
  'steps.interviewFilters.title': '筛选历史记录',
  'steps.interviewFilters.description': '按公司和面试状态过滤，快速找回某一次面试的完整问答记录。',
  'steps.interviewReports.title': '别忘了看成长报告',
  'steps.interviewReports.description': '完成的面试可以合并生成成长报告，用雷达图和趋势曲线看清能力变化。',

  // ---- interviewStart：开始面试配置页 ----
  'steps.interviewConfig.title': '选择目标公司与岗位',
  'steps.interviewConfig.description': '不同公司和岗位的提问风格、考察重点都不一样，选得越准，模拟越接近真实面试。',
  'steps.interviewResume.title': '关联你的简历',
  'steps.interviewResume.description':
    '强烈建议关联一份简历。AI 面试官会读你的项目和经历来出题追问，这也是模拟面试最像真面试的地方。',
  'steps.interviewSubmit.title': '开始面试',
  'steps.interviewSubmit.description':
    '点击后会创建面试并消耗 1 次额度（每月赠送 {count} 次免费）。面试过程中随时可以中断，记录会保留下来。',

  // ---- profile：个人中心 ----
  'steps.quota.title': '你的免费额度',
  'steps.quota.description':
    'AI 简历对话与 AI 模拟面试每项每月各有 {count} 次免费额度，每月 1 号自动重置。进度条是本月剩余免费次数，右上角是额外充值的付费次数余额。',
};

const en: Dict = {
  // ---- Common buttons ----
  next: 'Next',
  prev: 'Back',
  finish: 'Get started',
  skip: 'Skip tour',
  close: 'Close tour',
  progress: 'Step {current} of {total}',

  // ---- welcome ----
  'steps.welcome.title': 'Welcome to PrepDeck 👋',
  'steps.welcome.description':
    'Turn your experience into a resume by chatting with AI, then let an AI interviewer grill you on that exact resume. Every month you get {count} free AI resume chats and {count} free AI mock interviews — no payment needed to try the full flow. This 30-second tour shows you around.',
  'steps.navResumes.title': 'Step 1: Build a resume',
  'steps.navResumes.description':
    'Everything starts in My Resumes. Start from a template, or just describe your experience and let AI structure it for you.',
  'steps.navInterviews.title': 'Step 2: Run an AI mock interview',
  'steps.navInterviews.description':
    'Once your resume is ready, start a mock interview. Pick a target company and role — the AI interviewer reads your resume, asks follow-ups, then scores you with concrete feedback.',
  'steps.navReports.title': 'Step 3: Track your growth',
  'steps.navReports.description':
    'After a few interviews, Growth Report aggregates your scores into radar charts and trend lines so you can see exactly where you are improving.',
  'steps.navQuota.title': 'Check your free quota here',
  'steps.navQuota.description':
    'Your profile shows the balance: {count} free AI resume chats and {count} free mock interviews per month, reset on the 1st. Run out and you can top up per use — nothing is charged automatically.',

  // ---- resumes ----
  'steps.aiGenerate.title': 'Recommended: let AI write it',
  'steps.aiGenerate.description':
    'Stuck on a blank page? Describe your experience in plain language and AI splits it into profile, work history, projects and skills, then polishes the wording. You still have free credits this month.',
  'steps.create.title': 'Or start from a template',
  'steps.create.description': 'Prefer writing it yourself? Create a resume and pick a template. You can switch templates anytime without losing content.',
  'steps.search.title': 'Search your resumes',
  'steps.search.description': 'Filter by title or file name; the total count shows on the right.',
  'steps.resumeList.title': 'Set your active resume',
  'steps.resumeList.description':
    'Click a card to edit. The "⋯" menu lets you set it active, duplicate or export it. The AI interviewer uses your active resume by default.',
  'steps.gotoInterview.title': 'Resume ready? Go practice',
  'steps.gotoInterview.description': 'Head to Interviews and run a mock session based on the resume you just built.',

  // ---- editor ----
  'steps.editorAiChat.title': 'Generate your resume by chatting',
  'steps.editorAiChat.description':
    'The fastest way to fill the editor. Describe your experience like a conversation and AI writes it straight into the sections on the left. You can also ask it to rewrite, shorten or tailor a section to a specific role. {count} free chats per month.',
  'steps.editorSidebar.title': 'Manage sections',
  'steps.editorSidebar.description': 'Add or remove resume sections here; drag them on the canvas to reorder.',
  'steps.editorPreview.title': 'Live preview and export',
  'steps.editorPreview.description': 'WYSIWYG preview updates as you type. Export to PDF / HTML / TXT / JSON when ready.',

  // ---- interviews ----
  'steps.interviewStart.title': 'Start an AI mock interview',
  'steps.interviewStart.description':
    'Pick a target company and role. The AI interviewer runs staged questions with real follow-ups, then gives you a total score and per-item advice. {count} free sessions per month.',
  'steps.interviewFilters.title': 'Filter past sessions',
  'steps.interviewFilters.description': 'Filter by company and status to jump back into any past transcript.',
  'steps.interviewReports.title': "Don't skip the growth report",
  'steps.interviewReports.description': 'Combine completed interviews into a report and watch your skills trend over time.',

  // ---- interviewStart ----
  'steps.interviewConfig.title': 'Choose company and role',
  'steps.interviewConfig.description': 'Question style and focus differ per company and role — the more accurate your pick, the more realistic the simulation.',
  'steps.interviewResume.title': 'Link your resume',
  'steps.interviewResume.description':
    'Strongly recommended. The AI interviewer builds questions from your actual projects and experience — that is what makes it feel like a real interview.',
  'steps.interviewSubmit.title': 'Start the interview',
  'steps.interviewSubmit.description':
    'This creates the session and uses 1 credit ({count} free per month). You can stop anytime — the transcript is kept.',

  // ---- profile ----
  'steps.quota.title': 'Your free quota',
  'steps.quota.description':
    'AI resume chat and AI mock interview each come with {count} free runs per month, reset on the 1st. The bar shows free runs left; the top-right number is your paid balance.',
};

const DICTS: Record<TourLocale, Dict> = { 'zh-CN': zhCN, en };

/** 所有文案默认可用的插值变量（每月免费额度次数） */
const DEFAULT_VARS: Record<string, string | number> = { count: FREE_QUOTA_PER_MONTH };

/**
 * 取引导文案。找不到 key 时原样返回 key，方便定位漏配的文案。
 * 支持 `{name}` 占位符插值，`count` 默认注入每月免费额度次数。
 */
export function tTour(
  key: string,
  vars?: Record<string, string | number>,
  locale: TourLocale = resolveLocale(),
): string {
  const dict = DICTS[locale] ?? zhCN;
  const raw = dict[key] ?? DICTS['zh-CN'][key] ?? key;
  const merged = { ...DEFAULT_VARS, ...vars };
  return raw.replace(/\{(\w+)\}/g, (m, name: string) =>
    name in merged ? String(merged[name]) : m,
  );
}
