import { create } from 'zustand';
import type {
  EditorSection,
  SectionContent,
  SectionType,
  ThemeConfig,
} from '../types/editor';
import { DEFAULT_THEME, AUTOSAVE_DELAY } from '../types/editor';
import type { ResumeVO, UpdateResumeRequest } from '../types';
import { resumeApi } from '../api/resume';
import { useEditorStore } from './editor-store';
import {
  parseSectionContent,
  serializeSectionContent,
  defaultContentForType,
  normalizeSectionTitle,
  generateId,
} from '../lib/editor-utils';
import { normalizeSectionContent } from '../lib/normalize-content';

// Content edits coalesce undo snapshots within this window so a burst of
// keystrokes becomes one undo step instead of dozens.
const UNDO_COALESCE_MS = 600;

interface ResumeStore {
  currentResume: ResumeVO | null;
  sections: EditorSection[];
  isDirty: boolean;
  isSaving: boolean;
  _saveTimeout: ReturnType<typeof setTimeout> | null;

  setResume: (resume: ResumeVO) => void;
  updateSection: (sectionId: string, content: Partial<SectionContent>) => void;
  updateSectionTitle: (sectionId: string, title: string) => void;
  addSection: (type: SectionType, title?: string) => void;
  removeSection: (sectionId: string) => void;
  reorderSections: (sections: EditorSection[]) => void;
  toggleSectionVisibility: (sectionId: string) => void;
  setTemplate: (template: string) => void;
  setTitle: (title: string) => void;
  updateTheme: (updates: Partial<ThemeConfig>) => void;
  applySnapshot: (sections: EditorSection[]) => void;
  save: () => Promise<void>;
  _scheduleSave: () => void;
  reset: () => void;
}

let lastContentSnapshot = 0;

export const useResumeStore = create<ResumeStore>((set, get) => ({
  currentResume: null,
  sections: [],
  isDirty: false,
  isSaving: false,
  _saveTimeout: null,

  setResume: (resume) => {
    const { _saveTimeout } = get();
    if (_saveTimeout) clearTimeout(_saveTimeout);

    // Parse each section's content into the structured shape the renderers
    // expect, repairing corrupted arrays so a broken resume opens & repairs.
    const sections: EditorSection[] = (resume.sections || []).map((s, i) => ({
      id: String(s.id ?? generateId()),
      type: (s.type as SectionType) ?? 'custom',
      title: normalizeSectionTitle((s.type as SectionType) ?? 'custom', s.title),
      order: typeof (s as { sortOrder?: number }).sortOrder === 'number'
        ? (s as { sortOrder?: number }).sortOrder!
        : i,
      visible: s.visible !== false,
      content: normalizeSectionContent(
        s.type ?? 'custom',
        parseSectionContent(s.type ?? 'custom', s.content),
      ) as unknown as SectionContent,
    }));

    set({
      currentResume: {
        ...resume,
        sections,
        // Deep-merge so a backend themeConfig that exists but is missing the
        // `margin` sub-object (older/partial records) still gets sane defaults.
        // A shallow `?? DEFAULT_THEME` would pass through a margin-less object
        // and crash renderers that read `themeConfig.margin.top`.
        themeConfig: {
          ...DEFAULT_THEME,
          ...(resume.themeConfig || {}),
          margin: { ...DEFAULT_THEME.margin, ...(resume.themeConfig?.margin || {}) },
        },
      },
      sections,
      isDirty: false,
      _saveTimeout: null,
    });
  },

  updateSection: (sectionId, content) => {
    const prev = get().sections;
    set((state) => {
      const sections = state.sections.map((s) =>
        s.id === sectionId
          ? { ...s, content: { ...s.content, ...content } as SectionContent }
          : s
      );
      return {
        sections,
        currentResume: state.currentResume ? { ...state.currentResume, sections } : null,
        isDirty: true,
      };
    });
    // Coalesce content-edit undo snapshots.
    const now = Date.now();
    if (now - lastContentSnapshot > UNDO_COALESCE_MS) {
      lastContentSnapshot = now;
      useEditorStore.getState().pushSnapshot(prev);
    }
    get()._scheduleSave();
  },

  updateSectionTitle: (sectionId, title) => {
    const prev = get().sections;
    useEditorStore.getState().pushSnapshot(prev);
    set((state) => {
      const sections = state.sections.map((s) =>
        s.id === sectionId ? { ...s, title } : s
      );
      return {
        sections,
        currentResume: state.currentResume ? { ...state.currentResume, sections } : null,
        isDirty: true,
      };
    });
    get()._scheduleSave();
  },

  addSection: (type, title) => {
    const prev = get().sections;
    useEditorStore.getState().pushSnapshot(prev);
    const order = get().sections.length;
    const section: EditorSection = {
      id: generateId(),
      type,
      title: title || normalizeSectionTitle(type),
      order,
      visible: true,
      content: defaultContentForType(type),
    };
    set((state) => {
      const sections = [...state.sections, section];
      return {
        sections,
        currentResume: state.currentResume ? { ...state.currentResume, sections } : null,
        isDirty: true,
      };
    });
    get()._scheduleSave();
  },

  removeSection: (sectionId) => {
    const prev = get().sections;
    useEditorStore.getState().pushSnapshot(prev);
    set((state) => {
      const sections = state.sections.filter((s) => s.id !== sectionId);
      return {
        sections,
        currentResume: state.currentResume ? { ...state.currentResume, sections } : null,
        isDirty: true,
      };
    });
    get()._scheduleSave();
  },

  reorderSections: (sections) => {
    const prev = get().sections;
    useEditorStore.getState().pushSnapshot(prev);
    const reordered = sections.map((s, i) => ({ ...s, order: i }));
    set((state) => ({
      sections: reordered,
      currentResume: state.currentResume ? { ...state.currentResume, sections: reordered } : null,
      isDirty: true,
    }));
    get()._scheduleSave();
  },

  toggleSectionVisibility: (sectionId) => {
    const prev = get().sections;
    useEditorStore.getState().pushSnapshot(prev);
    set((state) => {
      const sections = state.sections.map((s) =>
        s.id === sectionId ? { ...s, visible: !s.visible } : s
      );
      return {
        sections,
        currentResume: state.currentResume ? { ...state.currentResume, sections } : null,
        isDirty: true,
      };
    });
    get()._scheduleSave();
  },

  setTemplate: (template) => {
    const prev = get().sections;
    useEditorStore.getState().pushSnapshot(prev);
    set((state) => ({
      currentResume: state.currentResume ? { ...state.currentResume, template } : null,
      isDirty: true,
    }));
    get()._scheduleSave();
  },

  setTitle: (title) => {
    const prev = get().sections;
    useEditorStore.getState().pushSnapshot(prev);
    set((state) => ({
      currentResume: state.currentResume ? { ...state.currentResume, title } : null,
      isDirty: true,
    }));
    get()._scheduleSave();
  },

  updateTheme: (updates) => {
    const prev = get().sections;
    useEditorStore.getState().pushSnapshot(prev);
    set((state) => {
      const themeConfig: ThemeConfig = {
        ...(state.currentResume?.themeConfig ?? DEFAULT_THEME),
        ...updates,
      };
      return {
        currentResume: state.currentResume ? { ...state.currentResume, themeConfig } : null,
        isDirty: true,
      };
    });
    get()._scheduleSave();
  },

  save: async () => {
    const { currentResume, sections, isDirty } = get();
    if (!currentResume?.id || !isDirty) return;

    set({ isSaving: true });
    try {
      const payload: UpdateResumeRequest = {
        title: currentResume.title,
        template: currentResume.template,
        language: currentResume.language,
        themeConfig: currentResume.themeConfig,
        isPublic: currentResume.isPublic,
        sections: sections.map((s, i) => ({
          id: s.id,
          type: s.type,
          title: s.title,
          sortOrder: i,
          visible: s.visible,
          content: serializeSectionContent(s.content),
        })),
      };
      await resumeApi.update(currentResume.id, payload);
      set({ isDirty: false });
    } catch (error) {
      console.error('Failed to save resume:', error);
    } finally {
      set({ isSaving: false });
    }
  },

  // Restore a snapshot's sections without pushing a new undo entry and without
  // an immediate autosave — used by undo/redo so the stack isn't corrupted.
  applySnapshot: (sections) => {
    set((state) => ({
      sections,
      currentResume: state.currentResume ? { ...state.currentResume, sections } : null,
      isDirty: true,
    }));
    get()._scheduleSave();
  },

  // Debounced autosave (frontend layer). Fires PUT /resumes/:id after a pause.
  _scheduleSave: () => {
    const { _saveTimeout } = get();
    if (_saveTimeout) clearTimeout(_saveTimeout);
    const timeout = setTimeout(() => {
      get().save();
    }, AUTOSAVE_DELAY);
    set({ _saveTimeout: timeout });
  },

  reset: () => {
    const { _saveTimeout } = get();
    if (_saveTimeout) clearTimeout(_saveTimeout);
    set({
      currentResume: null,
      sections: [],
      isDirty: false,
      isSaving: false,
      _saveTimeout: null,
    });
  },
}));
