import { useState, useCallback } from 'react';
import { toast } from 'sonner';
import { useResumeStore } from '../stores/resume-store';
import { resumeApi } from '../api/resume';
import { polishSectionContent, polishBatch } from '../lib/ai-polish';

export function useAiPolish() {
  const [polishingIds, setPolishingIds] = useState<Set<string>>(new Set());
  const [polishingAll, setPolishingAll] = useState(false);

  const polishSection = useCallback(async (sectionId: string) => {
    const state = useResumeStore.getState();
    const section = state.sections.find((s) => s.id === sectionId);
    if (!section) return;

    setPolishingIds((prev) => new Set(prev).add(sectionId));
    try {
      const newContent = await polishSectionContent(section);
      useResumeStore.getState().updateSection(sectionId, newContent);
      toast.success(`「${section.title}」已润色`);
    } catch {
      toast.error(`「${section.title}」润色失败`);
    } finally {
      setPolishingIds((prev) => {
        const n = new Set(prev);
        n.delete(sectionId);
        return n;
      });
    }
  }, []);

  const polishAll = useCallback(async () => {
    const state = useResumeStore.getState();
    const resumeId = state.currentResume?.id;
    if (!resumeId) return;

    setPolishingAll(true);
    try {
      await polishBatch(resumeId);
      const res = await resumeApi.getById(resumeId);
      useResumeStore.getState().setResume(res.data.data);
      toast.success('已润色全部模块');
    } catch {
      toast.error('批量润色失败');
    } finally {
      setPolishingAll(false);
    }
  }, []);

  return { polishingIds, polishingAll, polishSection, polishAll };
}
