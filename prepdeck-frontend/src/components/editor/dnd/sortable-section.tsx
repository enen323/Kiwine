import { createContext, useContext, type ReactNode, type CSSProperties } from 'react';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

type DragAttributes = ReturnType<typeof useSortable>['attributes'];
type DragListeners = ReturnType<typeof useSortable>['listeners'];

interface DragHandleContext {
  attributes: DragAttributes;
  listeners: DragListeners;
}

const DragHandleCtx = createContext<DragHandleContext>({
  attributes: undefined as unknown as DragAttributes,
  listeners: undefined,
});

/** Consume the drag-handle props registered by <SortableSection>. */
// eslint-disable-next-line react-refresh/only-export-components
export function useDragHandle() {
  return useContext(DragHandleCtx);
}

interface SortableSectionProps {
  id: string;
  children: ReactNode;
}

export function SortableSection({ id, children }: SortableSectionProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });

  const style: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <DragHandleCtx.Provider value={{ attributes, listeners }}>
      <div ref={setNodeRef} style={style} data-section-id={id}>
        {children}
      </div>
    </DragHandleCtx.Provider>
  );
}
