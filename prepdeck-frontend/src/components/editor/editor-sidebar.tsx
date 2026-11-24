import { useState, useRef, useEffect, useCallback, type ElementType, type CSSProperties } from 'react';
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  Plus,
  GripVertical,
  User,
  FileText,
  Briefcase,
  GraduationCap,
  Wrench,
  FolderKanban,
  Award,
  Languages,
  LayoutList,
  BookOpen,
  Pencil,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useEditorStore } from '../../stores/editor-store';
import { useResumeStore } from '../../stores/resume-store';
import type { EditorSection, SectionType } from '../../types/editor';
import { SECTION_TYPES, SECTION_LABELS } from '../../types/editor';

const sectionIcons: Record<string, ElementType> = {
  personalInfo: User,
  summary: FileText,
  experience: Briefcase,
  education: GraduationCap,
  skills: Wrench,
  project: FolderKanban,
  certification: Award,
  language: Languages,
  publication: BookOpen,
  custom: LayoutList,
};

interface SortableSidebarItemProps {
  section: EditorSection;
  isSelected: boolean;
  onSelect: () => void;
  onRename?: (title: string) => void;
  icon: ElementType;
}

function SortableSidebarItem({ section, isSelected, onSelect, onRename, icon: Icon }: SortableSidebarItemProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: section.id });
  const [isRenaming, setIsRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState(section.title);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isRenaming) {
      inputRef.current?.focus();
      inputRef.current?.select();
    }
  }, [isRenaming]);

  const commitRename = () => {
    const trimmed = renameValue.trim();
    if (trimmed && trimmed !== section.title && onRename) {
      onRename(trimmed);
    } else {
      setRenameValue(section.title);
    }
    setIsRenaming(false);
  };

  const style: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : undefined,
  };

  const isRenamable = section.type !== 'personalInfo';

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`group flex items-center gap-0.5 rounded-md bg-card ${isSelected ? 'bg-brand/10' : ''}`}
    >
      <GripVertical
        size={14}
        className="shrink-0 cursor-grab text-muted-foreground active:cursor-grabbing"
        {...attributes}
        {...listeners}
      />
      <button type="button" className="flex min-w-0 flex-1 items-center gap-2 rounded-md bg-transparent px-1.5 py-1.5 text-left text-sm text-foreground" onClick={onSelect}>
        <Icon size={16} className="shrink-0 text-muted-foreground" />
        {isRenaming ? (
          <input
            ref={inputRef}
            className="min-w-0 flex-1 rounded-md border border-brand bg-background px-1.5 py-1 text-sm outline-none"
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
          <span className={`truncate ${isSelected ? 'text-brand' : ''}`}>{section.title}</span>
        )}
      </button>
      {isRenamable && !isRenaming && (
        <Button
          type="button"
          variant="ghost"
          size="icon-xs"
          className="shrink-0 opacity-0 transition-opacity group-hover:opacity-100"
          onClick={(e) => { e.stopPropagation(); setRenameValue(section.title); setIsRenaming(true); }}
          title="重命名"
        >
          <Pencil />
        </Button>
      )}
    </div>
  );
}

export function EditorSidebar() {
  const { selectedSectionId, selectSection } = useEditorStore();
  const sections = useResumeStore((s) => s.sections);
  const addSection = useResumeStore((s) => s.addSection);
  const updateSectionTitle = useResumeStore((s) => s.updateSectionTitle);
  const reorderSections = useResumeStore((s) => s.reorderSections);

  const handleSelect = useCallback(
    (id: string) => {
      selectSection(id);
      requestAnimationFrame(() => {
        const el = document.querySelector(`[data-section-id="${id}"]`);
        el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    },
    [selectSection]
  );

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event;
      if (over && active.id !== over.id) {
        const oldIndex = sections.findIndex((s) => s.id === active.id);
        const newIndex = sections.findIndex((s) => s.id === over.id);
        if (oldIndex !== -1 && newIndex !== -1) {
          const newSections = [...sections];
          const [removed] = newSections.splice(oldIndex, 1);
          newSections.splice(newIndex, 0, removed);
          reorderSections(newSections.map((s, i) => ({ ...s, order: i })));
        }
      }
    },
    [sections, reorderSections]
  );

  const existingTypes = new Set(sections.map((s) => s.type));
  const availableTypes = SECTION_TYPES.filter((type) => (type === 'custom' ? true : !existingTypes.has(type)));

  return (
    <div data-tour="editor-sidebar" className="flex w-64 shrink-0 flex-col border-r bg-card">
      <div className="px-4 pb-2 pt-4">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">模块</h3>
      </div>
      <div className="flex-1 overflow-y-auto px-2 pb-4">
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={sections.map((s) => s.id)} strategy={verticalListSortingStrategy}>
            <div className="flex flex-col gap-0.5">
              {sections.map((section) => {
                const Icon = sectionIcons[section.type] || LayoutList;
                return (
                  <SortableSidebarItem
                    key={section.id}
                    section={section}
                    isSelected={selectedSectionId === section.id}
                    onSelect={() => handleSelect(section.id)}
                    onRename={section.type !== 'personalInfo' ? (title) => updateSectionTitle(section.id, title) : undefined}
                    icon={Icon}
                  />
                );
              })}
            </div>
          </SortableContext>
        </DndContext>

        {availableTypes.length > 0 && (
          <div className="border-t px-3 pt-3">
            <p className="mb-2 text-xs text-muted-foreground">添加模块</p>
            <div className="flex flex-wrap gap-1.5">
              {availableTypes.map((type: SectionType) => {
                const Icon = sectionIcons[type] || LayoutList;
                return (
                  <button
                    key={type}
                    type="button"
                    className="inline-flex items-center gap-1 rounded-md border border-dashed border-border bg-muted/40 px-2.5 py-1.5 text-xs text-muted-foreground hover:border-brand hover:text-brand"
                    onClick={() => addSection(type)}
                  >
                    <Plus size={14} />
                    <Icon size={16} />
                    <span>{SECTION_LABELS[type]}</span>
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
