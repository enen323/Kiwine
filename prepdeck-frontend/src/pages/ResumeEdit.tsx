import { useEffect, useState } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import type { ResumeVO } from '../types';
import { EditorToolbar } from '../components/editor/editor-toolbar';
import { EditorSidebar } from '../components/editor/editor-sidebar';
import { EditorCanvas } from '../components/editor/editor-canvas';
import { EditorPreviewPanel } from '../components/editor/editor-preview-panel';
import { ThemeEditor } from '../components/editor/theme-editor';
import { AiChatFloatingButton } from '../components/editor/ai-chat/ai-chat-floating-button';
import { TourOverlay } from '@/components/tour/tour-overlay';
import { useTourTrigger } from '@/hooks/use-tour';
import { TOUR_IDS, EDITOR_TOUR_STEPS } from '@/lib/tour-steps';
import { useResumeStore } from '../stores/resume-store';
import { useEditorStore } from '../stores/editor-store';
import { resumeApi } from '../api/resume';

/* =========================================================================
 * 简历编辑页 —— 组合 JadeAI 风格的模块化编辑器
 * 布局（参照 JadeAI editor/page.tsx）：
 *   顶部工具栏 + 左侧模块列表 + 中间可拖拽画布 + 右侧实时预览（常驻）
 *   主题 / AI 面板作为画布与预览之间的「兄弟列」出现，不会覆盖预览。
 * 简历创建统一在 /templates 完成（含默认模块），本页只负责「编辑已有简历」。
 * ========================================================================= */

export default function ResumeEdit() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const isNew = !id || id === 'new';

  const [loading, setLoading] = useState(!isNew);
  const [error, setError] = useState('');

  const showThemeEditor = useEditorStore((s) => s.showThemeEditor);

  useEffect(() => {
    // 新建流程已收口到 /templates，直接进入新建路由时回退到模板页，
    // 避免在此处重复创建导致 StrictMode 下产生孤儿记录。
    if (isNew) {
      navigate('/templates', { replace: true });
      return;
    }

    let cancelled = false;

    // 若路由携带了已加载的简历（如 AI 对话刚创建成功即跳转），先直接灌入，
    // 保证「已存在」的简历立即渲染，不依赖紧随其后的 GET 是否瞬时成功。
    const seeded = (location.state as { resume?: ResumeVO } | null)?.resume;

    const fetchOnce = async () => {
      const res = await resumeApi.getById(id as string);
      return res.data.data;
    };

    const boot = async () => {
      if (seeded && !cancelled) {
        useResumeStore.getState().setResume(seeded);
        setLoading(false);
      }
      // GET 为「尽力刷新」：拿到最新数据就覆盖；失败（含瞬时 404）且不带种子数据时
      // 才提示「简历不存在」。带种子数据时静默保留已渲染的简历。
      try {
        let data: ResumeVO;
        try {
          data = await fetchOnce();
        } catch {
          data = await fetchOnce(); // 重试一次，规避刚创建后的瞬时失败
        }
        if (cancelled) return;
        useResumeStore.getState().setResume(data);
        setLoading(false);
      } catch {
        if (!cancelled && !seeded) {
          setError('简历不存在或加载失败');
          setLoading(false);
        }
      }
    };

    boot();

    return () => {
      cancelled = true;
      // 离开页面：若有未保存改动先落盘，再重置所有编辑器状态
      const current = useResumeStore.getState();
      if (current.isDirty) current.save();
      useResumeStore.getState().reset();
      useEditorStore.getState().reset();
    };
  }, [id, isNew, navigate]);

  // 新手引导：简历加载完成后触发，重点讲「AI 对话生成简历」入口
  useTourTrigger(TOUR_IDS.editor, EDITOR_TOUR_STEPS.length, { ready: !loading && !error });

  if (loading) {
    return (
      <div className="flex h-[calc(100vh-3.5rem)] flex-col overflow-hidden bg-background text-foreground">
        <div className="flex flex-1 flex-col items-center justify-center gap-3">
          <div className="size-8 animate-spin rounded-full border-2 border-muted border-t-brand" />
          <p className="text-sm text-muted-foreground">正在加载简历…</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex h-[calc(100vh-3.5rem)] flex-col overflow-hidden bg-background text-foreground">
        <div className="flex flex-1 flex-col items-center justify-center gap-3">
          <p className="text-muted-foreground">{error}</p>
          <button type="button" className="rounded-md bg-brand px-4 py-2 text-sm text-brand-foreground hover:bg-brand/90" onClick={() => navigate('/resumes')}>
            返回简历列表
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-[calc(100vh-3.5rem)] flex-col overflow-hidden bg-background text-foreground">
      <EditorToolbar />
      <div className="flex min-h-0 flex-1">
        <EditorSidebar />
        <EditorCanvas />
        {/* 主题 / AI 面板：作为画布与预览之间的「兄弟列」出现，
            打开时不会覆盖右侧实时预览（与 JadeAI 编排一致）。 */}
        {showThemeEditor && <ThemeEditor />}
        {/* 实时预览：始终挂载的右侧常驻列，编辑时随 store 实时刷新。 */}
        <div data-tour="editor-preview" className="flex min-h-0 min-w-[380px] max-w-[660px] flex-[1.25] flex-col border-l bg-muted/30">
          <EditorPreviewPanel />
        </div>
      </div>

      {/* AI 对话生成简历：右下角浮动入口，点击跳转到独立 AI 对话页 */}
      {id && <AiChatFloatingButton resumeId={id} />}

      {/* 新手引导 */}
      <TourOverlay tourId={TOUR_IDS.editor} steps={EDITOR_TOUR_STEPS} />
    </div>
  );
}
