import { useState, useRef, useEffect, type ComponentType } from 'react';
import { GripVertical, Trash2, Eye, EyeOff, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useEditorStore } from '../../stores/editor-store';
import { useResumeStore } from '../../stores/resume-store';
import { useAiPolish } from '../../hooks/useAiPolish';
import { useDragHandle } from './dnd/sortable-section';
import type { EditorSection, SectionContent } from '../../types/editor';
import { PersonalInfoSection } from './sections/personal-info';
import { SummarySection } from './sections/summary';
import { WorkExperienceSection } from './sections/work-experience';
import { EducationSection } from './sections/education';
import { SkillsSection } from './sections/skills';
import { ProjectsSection } from './sections/projects';
import { CertificationsSection } from './sections/certifications';
import { LanguagesSection } from './sections/languages';
import { PublicationsSection } from './sections/publications';
import { CustomSection } from './sections/custom-section';

interface SectionWrapperProps {
  section: EditorSection;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const sectionComponents: Record<string, ComponentType<{ section: EditorSection; onUpdate: (content: any) => void }>> = {
  personalInfo: PersonalInfoSection,
  summary: SummarySection,
  experience: WorkExperienceSection,
  education: EducationSection,
  skills: SkillsSection,
  project: ProjectsSection,
  certification: CertificationsSection,
  language: LanguagesSection,
  publication: PublicationsSection,
  custom: CustomSection,
};

export function SectionWrapper({ section }: SectionWrapperProps) {
  const { selectedSectionId, selectSection } = useEditorStore();
  const { toggleSectionVisibility, updateSectionTitle, removeSection, updateSection } = useResumeStore();
  const { polishingIds, polishSection } = useAiPolish();
  const polishing = polishingIds.has(section.id);
  const { attributes, listeners } = useDragHandle();
  const isSelected = selectedSectionId === section.id;
  const [isRenaming, setIsRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState(section.title);
  const renameInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isRenaming) {
      renameInputRef.current?.focus();
      renameInputRef.current?.select();
    }
  }, [isRenaming]);

  const commitRename = () => {
    const trimmed = renameValue.trim();
    if (trimmed && trimmed !== section.title) {
      updateSectionTitle(section.id, trimmed);
    } else {
      setRenameValue(section.title);
    }
    setIsRenaming(false);
  };

  const SectionComponent = sectionComponents[section.type];
  const isRenamable = section.type !== 'personalInfo';

  const onUpdate = (content: Partial<SectionContent>) => updateSection(section.id, content);

  return (
    <div
      className={cn(
        'mx-3 my-2 rounded-lg border border-transparent bg-card p-3.5 transition-colors hover:border-border',
        isSelected && 'border-brand ring-[3px] ring-brand/30',
        !section.visible && 'opacity-50'
      )}
      onClick={() => selectSection(section.id)}
    >
      <div className="flex items-center gap-1.5">
        <div className="flex min-w-0 flex-1 items-center gap-1.5">
          <GripVertical
            size={16}
            className="shrink-0 cursor-grab text-muted-foreground active:cursor-grabbing"
            {...attributes}
            {...listeners}
          />
          {isRenaming ? (
            <input
              ref={renameInputRef}
              className="min-w-0 flex-1 rounded-md border border-brand bg-background px-2 py-0.5 text-[15px] font-semibold outline-none"
              value={renameValue}
              onChange={(e) => setRenameValue(e.target.value)}
              onBlur={commitRename}
              onKeyDown={(e) => {
                if (e.key === 'Enter') commitRename();
                if (e.key === 'Escape') { setRenameValue(section.title); setIsRenaming(false); }
              }}
              onClick={(e) => e.stopPropagation()}
            />
          ) : (
            <h3
              className={`m-0 truncate text-[15px] font-semibold text-foreground ${isRenamable ? 'cursor-text' : ''}`}
              onDoubleClick={isRenamable ? (e) => { e.stopPropagation(); setRenameValue(section.title); setIsRenaming(true); } : undefined}
            >
              {section.title}
            </h3>
          )}
        </div>
        <div className="flex items-center gap-0.5">
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            className="text-muted-foreground hover:bg-brand/10 hover:text-brand disabled:opacity-60"
            title="AI 润色"
            disabled={polishing}
            onClick={(e) => { e.stopPropagation(); polishSection(section.id); }}
          >
            <Sparkles className={polishing ? 'animate-spin' : ''} />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            className="text-muted-foreground hover:bg-accent hover:text-foreground"
            title={section.visible ? '隐藏模块' : '显示模块'}
            onClick={(e) => { e.stopPropagation(); toggleSectionVisibility(section.id); }}
          >
            {section.visible ? <Eye /> : <EyeOff />}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            className="text-muted-foreground hover:bg-accent hover:text-foreground"
            title="删除模块"
            onClick={(e) => { e.stopPropagation(); removeSection(section.id); }}
          >
            <Trash2 />
          </Button>
        </div>
      </div>
      <div className="mt-2">
        {!SectionComponent ? (
          <p className="py-2 text-[13px] text-destructive">未知模块类型：{section.type}</p>
        ) : (
          <SectionComponent section={section} onUpdate={onUpdate} />
        )}
      </div>
    </div>
  );
}
