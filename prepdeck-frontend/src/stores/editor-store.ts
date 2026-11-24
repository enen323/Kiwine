import { create } from 'zustand';
import type { EditorSection } from '../types/editor';
import { MAX_UNDO_STACK } from '../types/editor';

export interface ResumeSnapshot {
  sections: EditorSection[];
  timestamp: number;
}

interface EditorStore {
  // ----- Canvas editor UI state -----
  selectedSectionId: string | null;
  selectedItemId: string | null;
  isDragging: boolean;
  showThemeEditor: boolean;
  zoom: number;
  mobileActiveTab: 'edit' | 'preview';

  // ----- Undo / Redo -----
  undoStack: ResumeSnapshot[];
  redoStack: ResumeSnapshot[];
  pendingAiMessage: string | null;

  // ----- Selectors / mutations -----
  selectSection: (id: string | null) => void;
  selectItem: (id: string | null) => void;
  setDragging: (isDragging: boolean) => void;
  toggleThemeEditor: () => void;
  setShowThemeEditor: (show: boolean) => void;
  setZoom: (zoom: number) => void;
  setMobileActiveTab: (tab: 'edit' | 'preview') => void;

  // ----- Undo / Redo engine -----
  pushSnapshot: (sections: EditorSection[]) => void;
  undo: () => ResumeSnapshot | null;
  redo: () => ResumeSnapshot | null;

  setPendingAiMessage: (message: string | null) => void;
  reset: () => void;
}

export const useEditorStore = create<EditorStore>((set, get) => ({
  selectedSectionId: null,
  selectedItemId: null,
  isDragging: false,
  showThemeEditor: false,
  zoom: 100,
  mobileActiveTab: 'edit',

  undoStack: [],
  redoStack: [],
  pendingAiMessage: null,

  selectSection: (id) => set({ selectedSectionId: id, selectedItemId: null }),
  selectItem: (id) => set({ selectedItemId: id }),
  setDragging: (isDragging) => set({ isDragging }),
  toggleThemeEditor: () => set((s) => ({ showThemeEditor: !s.showThemeEditor })),
  setShowThemeEditor: (show) => set({ showThemeEditor: show }),
  setZoom: (zoom) => set({ zoom }),
  setMobileActiveTab: (tab) => set({ mobileActiveTab: tab }),

  // Push a snapshot of the current sections onto the undo stack (max 50).
  // Any new snapshot clears the redo stack.
  pushSnapshot: (sections) => {
    set((state) => ({
      undoStack: [
        ...state.undoStack.slice(-MAX_UNDO_STACK + 1),
        { sections, timestamp: Date.now() },
      ],
      redoStack: [],
    }));
  },

  undo: () => {
    const { undoStack } = get();
    if (undoStack.length === 0) return null;
    const snapshot = undoStack[undoStack.length - 1];
    set((state) => ({
      undoStack: state.undoStack.slice(0, -1),
      redoStack: [...state.redoStack, snapshot],
    }));
    return snapshot;
  },

  redo: () => {
    const { redoStack } = get();
    if (redoStack.length === 0) return null;
    const snapshot = redoStack[redoStack.length - 1];
    set((state) => ({
      redoStack: state.redoStack.slice(0, -1),
      undoStack: [...state.undoStack, snapshot],
    }));
    return snapshot;
  },

  setPendingAiMessage: (message) => set({ pendingAiMessage: message }),
  reset: () =>
    set({
      selectedSectionId: null,
      selectedItemId: null,
      isDragging: false,
      showThemeEditor: false,
      zoom: 100,
      mobileActiveTab: 'edit',
      undoStack: [],
      redoStack: [],
      pendingAiMessage: null,
    }),
}));
