import { useNavigate } from 'react-router-dom';
import { resumeApi } from '../api/resume';
import {
  generateId,
  defaultContentForType,
  defaultTitleForType,
  serializeSectionContent,
} from '../lib/editor-utils';
import { DEFAULT_SECTIONS, type SectionType } from '../types/editor';
import { TemplateThumbnail } from '../components/preview/template-thumbnail';

// 模板库：展示可选模板，点击后用该模板新建简历
// 视觉风格与「新建简历」弹窗的缩略图卡片统一
const TEMPLATES: { value: string; label: string; desc: string }[] = [
  { value: 'classic', label: '经典', desc: '简洁大方，适合大多数岗位' },
  { value: 'modern', label: '现代', desc: '留白充足，视觉清爽' },
  { value: 'minimal', label: '极简', desc: '聚焦内容，无多余装饰' },
  { value: 'professional', label: '专业', desc: '稳重商务风格' },
  { value: 'creative', label: '创意', desc: '个性鲜明，适合设计 / 产品' },
  { value: 'ats', label: 'ATS 友好', desc: '便于招聘系统解析' },
];

export default function Templates() {
  const navigate = useNavigate();

  // 直接用所选模板创建简历（含默认模块），再跳转到编辑页。
  // 创建放在导航入口处，避免编辑页在 StrictMode 下重复创建。
  const createFromTemplate = async (value: string) => {
    try {
      const created = await resumeApi.create({
        title: '未命名简历',
        template: value,
        sections: DEFAULT_SECTIONS.map((type: SectionType, i) => ({
          id: generateId(),
          type,
          title: defaultTitleForType(type),
          sortOrder: i,
          visible: true,
          content: serializeSectionContent(defaultContentForType(type)),
        })),
      });
      const newId = created.data.data.id;
      navigate(newId ? `/resumes/${newId}` : '/resumes');
    } catch {
      navigate('/resumes');
    }
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">简历模板</h1>
        <p className="mt-1 text-muted-foreground">选择一个模板开始创建你的简历</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {TEMPLATES.map(t => (
          <button
            key={t.value}
            type="button"
            className="group flex flex-col rounded-xl border bg-card p-4 text-left shadow-sm transition-colors hover:border-brand/50 hover:shadow-md"
            onClick={() => { void createFromTemplate(t.value); }}
          >
            <TemplateThumbnail template={t.value} width={220} className="mx-auto" />
            <div className="mt-4 font-medium text-foreground">{t.label}</div>
            <div className="mt-0.5 text-sm text-muted-foreground">{t.desc}</div>
            <span className="mt-3 text-sm font-medium text-brand group-hover:underline">用此模板新建 →</span>
          </button>
        ))}
      </div>
    </div>
  );
}
