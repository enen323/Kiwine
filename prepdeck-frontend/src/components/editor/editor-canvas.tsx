import { useCallback, type CSSProperties } from 'react';
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
} from '@dnd-kit/sortable';
import { SortableSection } from './dnd/sortable-section';
import { SectionWrapper } from './section-wrapper';
import { useEditorStore } from '../../stores/editor-store';
import { useResumeStore } from '../../stores/resume-store';
import type { EditorSection } from '../../types/editor';

export function EditorCanvas() {
  const sections = useResumeStore((s) => s.sections);
  const reorderSections = useResumeStore((s) => s.reorderSections);
  const themeConfig = useResumeStore((s) => s.currentResume?.themeConfig);
  const { setDragging, zoom } = useEditorStore();
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const handleDragStart = useCallback(
    () => {
      setDragging(true);
    },
    [setDragging]
  );

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event;
      setDragging(false);
      if (over && active.id !== over.id) {
        const oldIndex = sections.findIndex((s) => s.id === active.id);
        const newIndex = sections.findIndex((s) => s.id === over.id);
        if (oldIndex !== -1 && newIndex !== -1) {
          const newSections = [...sections];
          const [removed] = newSections.splice(oldIndex, 1);
          newSections.splice(newIndex, 0, removed);
          const reordered = newSections.map((s, i) => ({ ...s, order: i }));
          reorderSections(reordered);
        }
      }
    },
    [sections, reorderSections, setDragging]
  );

  const paperStyle: CSSProperties = {
    fontFamily: themeConfig?.fontFamily || 'Inter, system-ui, sans-serif',
    paddingTop: `${themeConfig?.margin?.top ?? 20}px`,
    paddingRight: `${themeConfig?.margin?.right ?? 20}px`,
    paddingBottom: `${themeConfig?.margin?.bottom ?? 20}px`,
    paddingLeft: `${themeConfig?.margin?.left ?? 20}px`,
    transform: `scale(${zoom / 100})`,
    transformOrigin: 'top center',
  };

  const visibleSections = sections.filter((s) => s.visible);

  return (
    <div className="min-w-0 flex-1 overflow-y-auto bg-muted/30">
      <div className="flex min-h-full justify-center p-6">
        <div className="mx-auto w-[210mm] max-w-full rounded-lg bg-white shadow-sm" style={paperStyle}>
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragStart={handleDragStart}
            onDragEnd={handleDragEnd}
          >
            <SortableContext
              items={sections.map((s: EditorSection) => s.id)}
              strategy={verticalListSortingStrategy}
            >
              <div className="flex flex-col">
                {sections.map((section) => (
                  <SortableSection key={section.id} id={section.id}>
                    <SectionWrapper section={section} />
                  </SortableSection>
                ))}
              </div>
            </SortableContext>
          </DndContext>
          {visibleSections.length === 0 && (
            <div className="px-5 py-[60px] text-center text-sm text-muted-foreground">从左侧添加模块，开始编辑你的简历</div>
          )}
        </div>
      </div>
    </div>
  );
}
