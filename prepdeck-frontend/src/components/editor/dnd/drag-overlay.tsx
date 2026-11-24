import type { ReactNode } from 'react';
import { DragOverlay as DndDragOverlay } from '@dnd-kit/core';

interface DragOverlayProps {
  children: ReactNode;
}

export function DragOverlay({ children }: DragOverlayProps) {
  return <DndDragOverlay>{children}</DndDragOverlay>;
}
