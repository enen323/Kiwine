// TemplateThumbnail — 模板缩略图组件
//
// 模仿 JadeAI 模板市场页的方案：用固定宽度 (A4 @ 96dpi = 794px) 渲染真实的
// ResumePreview，再通过 transform: scale 等比缩小塞进卡片。这样每个模板展示的
// 就是它真实的版式（标题样式、分栏、强调色、签名特效），而非千篇一律的占位色条。
//
// 「严丝合缝」关键点：
// 1. renderTemplate 的 rp-root 有 maxWidth:210mm(≈793.7px)、minHeight:297mm、
//    margin:0 auto。这些会让 rp-root 与外层 794px 容器产生 0.3px 偏差、且
//    minHeight 会把内容不足一页的简历撑开留白。这里通过 scoped CSS 覆盖为
//    max-width:100% / min-height:0 / margin:0，让 rp-root 紧贴外层容器、高度
//    由内容决定。
// 2. transform:scale 不改变布局尺寸，所以容器高度无法靠 CSS 自适应缩放后的
//    内容。用 useLayoutEffect + ResizeObserver 实测 rp-root 的 offsetHeight，
//    容器高度 = offsetHeight * scale，做到上下也严丝合缝。
//
// 弹窗里同时渲染 6 个缩略图，用 memo 避免父组件状态变化时无谓重渲染。

import { memo, useLayoutEffect, useRef, useState } from 'react';
import type {
  EditorSection,
  ThemeConfig,
  PersonalInfoContent,
  SummaryContent,
  WorkExperienceContent,
  EducationContent,
  SkillsContent,
  ProjectsContent,
  CertificationsContent,
  LanguagesContent,
} from '../../types/editor';
import { ResumePreview } from './resume-preview';
import { cn } from '../../lib/utils';

const PREVIEW_WIDTH = 794; // A4 @ 96dpi 宽
const DEFAULT_THUMB_WIDTH = 150;

// 覆盖 rp-root 的固定尺寸限制，scoped 到缩略图容器，不影响编辑器/预览页的真实简历。
// !important 是为了盖过 buildThemeCSS 里 [data-theme-scope] > .rp-root 的样式。
const THUMB_OVERRIDE_CSS =
  '.tpl-thumb-scope .rp-root{min-height:0!important;max-width:100%!important;margin:0!important}';

// margin 必须为 0（rp-root 是 content-box，padding 会让实际占用宽度 > PREVIEW_WIDTH）。
const MOCK_THEME: ThemeConfig = {
  primaryColor: '#1e293b',
  accentColor: '#3b82f6',
  fontFamily: 'Inter',
  fontSize: 'medium',
  lineSpacing: 1.5,
  margin: { top: 0, right: 0, bottom: 0, left: 0 },
  sectionSpacing: 14,
};

// 一份中文示例简历，覆盖各 section 类型，数据量足够呈现完整版式。
const MOCK_SECTIONS: EditorSection[] = [
  {
    id: 'pi',
    type: 'personalInfo',
    title: '个人信息',
    order: 0,
    visible: true,
    content: {
      fullName: '张明',
      jobTitle: '高级前端工程师',
      email: 'zhangming@example.com',
      phone: '138-0000-0000',
      location: '上海',
      github: 'github.com/zhangming',
      yearsOfExperience: '6年',
    } as PersonalInfoContent,
  },
  {
    id: 'sum',
    type: 'summary',
    title: '个人简介',
    order: 1,
    visible: true,
    content: {
      text: '6 年前端开发经验，擅长 React 生态与工程化体系建设，主导过多个百万级用户产品的架构升级，注重性能优化与团队协作。',
    } as SummaryContent,
  },
  {
    id: 'exp',
    type: 'experience',
    title: '工作经历',
    order: 2,
    visible: true,
    content: {
      items: [
        {
          id: 'e1',
          company: '星辰科技有限公司',
          position: '高级前端工程师',
          location: '上海',
          startDate: '2021-03',
          endDate: '',
          current: true,
          description: '负责中台前端架构设计与团队技术管理。',
          highlights: [
            '主导微前端架构落地，支撑 5 条业务线协同开发',
            '构建组件库与 CLI 工具链，研发效率提升 40%',
          ],
          technologies: ['React', 'TypeScript', 'Vite'],
        },
        {
          id: 'e2',
          company: '云栖互联网初创',
          position: '前端工程师',
          location: '杭州',
          startDate: '2018-07',
          endDate: '2021-02',
          current: false,
          description: '从 0 到 1 搭建核心产品前端体系。',
          highlights: ['实现实时协同编辑能力', '优化首屏加载至 1.2s 以内'],
        },
        {
          id: 'e3',
          company: '校园创业团队',
          position: '前端实习生',
          location: '上海',
          startDate: '2017-06',
          endDate: '2018-06',
          current: false,
          description: '参与校园生活服务平台前端开发。',
          highlights: ['完成小程序端核心模块'],
        },
      ],
    } as WorkExperienceContent,
  },
  {
    id: 'prj',
    type: 'project',
    title: '项目经历',
    order: 3,
    visible: true,
    content: {
      items: [
        {
          id: 'p1',
          name: '智能简历平台',
          startDate: '2022-05',
          endDate: '',
          description: '基于 AI 的简历编辑与面试模拟系统。',
          technologies: ['React', 'Vite', 'NestJS'],
          highlights: ['支持 50+ 模板实时预览', 'AI 生成简历准确率达 92%'],
        },
        {
          id: 'p2',
          name: '数据可视化中台',
          startDate: '2020-01',
          endDate: '2021-12',
          description: '面向运营的实时数据看板系统。',
          technologies: ['React', 'D3.js', 'WebSocket'],
          highlights: ['支撑百万级数据点流畅渲染'],
        },
      ],
    } as ProjectsContent,
  },
  {
    id: 'edu',
    type: 'education',
    title: '教育经历',
    order: 4,
    visible: true,
    content: {
      items: [
        {
          id: 'ed1',
          institution: '上海交通大学',
          degree: '工学硕士',
          field: '计算机科学与技术',
          startDate: '2016-09',
          endDate: '2018-06',
          gpa: '3.8',
          highlights: ['国家奖学金', 'ACM 区域赛银奖'],
        },
      ],
    } as EducationContent,
  },
  {
    id: 'skl',
    type: 'skills',
    title: '专业技能',
    order: 5,
    visible: true,
    content: {
      categories: [
        { id: 's1', name: '前端', skills: ['React', 'TypeScript', 'Next.js', 'Tailwind'] },
        { id: 's2', name: '工程化', skills: ['Vite', 'Webpack', 'ESLint', 'CI/CD'] },
        { id: 's3', name: '后端', skills: ['Node.js', 'Python', 'PostgreSQL'] },
      ],
    } as SkillsContent,
  },
  {
    id: 'cert',
    type: 'certification',
    title: '荣誉证书',
    order: 6,
    visible: true,
    content: {
      items: [
        { id: 'c1', name: 'PMP 项目管理认证', issuer: 'PMI', date: '2022-08' },
        { id: 'c2', name: 'AWS 解决方案架构师', issuer: 'Amazon', date: '2021-11' },
      ],
    } as CertificationsContent,
  },
  {
    id: 'lang',
    type: 'language',
    title: '语言能力',
    order: 7,
    visible: true,
    content: {
      items: [
        { id: 'l1', language: '英语', proficiency: '流利（CET-6）' },
        { id: 'l2', language: '普通话', proficiency: '母语' },
      ],
    } as LanguagesContent,
  },
];

interface TemplateThumbnailProps {
  template: string;
  className?: string;
  /** 缩略图宽度（px），高度由内容实测决定。默认 150。 */
  width?: number;
}

/**
 * Render a real resume preview for the given template, scaled down to a
 * thumbnail. Mirrors JadeAI's templates-page approach: fixed 794px width +
 * `transform: scale()`, so the thumbnail reflects the template's true layout.
 *
 * Container height is measured from the rendered rp-root (via ResizeObserver)
 * so the thumbnail hugs the content exactly — no top/bottom whitespace.
 */
export const TemplateThumbnail = memo(function TemplateThumbnail({
  template,
  className,
  width = DEFAULT_THUMB_WIDTH,
}: TemplateThumbnailProps) {
  const innerRef = useRef<HTMLDivElement>(null);
  // 初始按 A4 比例估一个高度，useLayoutEffect 实测后立即修正。
  const [contentH, setContentH] = useState(Math.round(PREVIEW_WIDTH * Math.SQRT2));

  useLayoutEffect(() => {
    const el = innerRef.current;
    if (!el) return;
    const rpRoot = el.querySelector('.rp-root') as HTMLElement | null;
    if (!rpRoot) return;
    const measure = () => setContentH(rpRoot.offsetHeight);
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(rpRoot);
    return () => ro.disconnect();
  }, [template]);

  const scale = width / PREVIEW_WIDTH;
  const height = Math.max(1, Math.round(contentH * scale));

  return (
    <div
      className={cn(
        'tpl-thumb-scope relative overflow-hidden rounded-md bg-white shadow-sm ring-1 ring-zinc-200/60',
        className,
      )}
      style={{ width, height }}
    >
      <style>{THUMB_OVERRIDE_CSS}</style>
      <div
        ref={innerRef}
        className="absolute left-0 top-0 origin-top-left"
        style={{ width: PREVIEW_WIDTH, transform: `scale(${scale})` }}
      >
        <ResumePreview
          resume={{ template, language: 'zh', themeConfig: MOCK_THEME, sections: MOCK_SECTIONS }}
        />
      </div>
    </div>
  );
});
