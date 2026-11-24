// ===== Editor / Resume content model (mirrors JadeAI types) =====
// Rich, structured content for the supported resume sections. The in-memory
// editor operates on these objects; persistence serializes `content` to a JSON
// string (see src/stores/resume-store.ts) to match the backend's string field.
//
// NOTE: section `type` values are camelCase and MUST match the backend
// contract (`ResumeSectionType` in src/types/index.ts) so resumes load and
// save round-trip correctly.

export interface PersonalInfoContent {
  fullName: string;
  jobTitle: string;
  age?: string;
  gender?: string;
  politicalStatus?: string;
  ethnicity?: string;
  hometown?: string;
  maritalStatus?: string;
  yearsOfExperience?: string;
  educationLevel?: string;
  email: string;
  phone: string;
  wechat?: string;
  location: string;
  website?: string;
  linkedin?: string;
  github?: string;
  customLinks?: { label: string; url: string }[];
  avatar?: string;
}

export interface SummaryContent {
  text: string;
}

export interface WorkExperienceItem {
  id: string;
  company: string;
  position: string;
  location?: string;
  startDate: string;
  endDate: string | null;
  current: boolean;
  description: string;
  technologies: string[];
  highlights: string[];
}

export interface WorkExperienceContent {
  items: WorkExperienceItem[];
}

export interface EducationItem {
  id: string;
  institution: string;
  degree: string;
  field: string;
  location?: string;
  startDate: string;
  endDate: string;
  gpa?: string;
  highlights: string[];
}

export interface EducationContent {
  items: EducationItem[];
}

export interface SkillCategory {
  id: string;
  name: string;
  skills: string[];
}

export interface SkillsContent {
  categories: SkillCategory[];
}

export interface ProjectItem {
  id: string;
  name: string;
  url?: string;
  startDate?: string;
  endDate?: string;
  description: string;
  technologies: string[];
  highlights: string[];
}

export interface ProjectsContent {
  items: ProjectItem[];
}

export interface CertificationItem {
  id: string;
  name: string;
  issuer: string;
  date: string;
  url?: string;
}

export interface CertificationsContent {
  items: CertificationItem[];
}

export interface LanguageItem {
  id: string;
  language: string;
  proficiency: string;
  description?: string;
}

export interface LanguagesContent {
  items: LanguageItem[];
}

export interface PublicationItem {
  id: string;
  title: string;
  authors?: string;
  venue?: string;
  date?: string;
  url?: string;
  description?: string;
}

export interface PublicationContent {
  items: PublicationItem[];
}

export interface CustomItem {
  id: string;
  title: string;
  subtitle?: string;
  date?: string;
  description: string;
}

export interface CustomContent {
  items: CustomItem[];
}

export type SectionContent =
  | PersonalInfoContent
  | SummaryContent
  | WorkExperienceContent
  | EducationContent
  | SkillsContent
  | ProjectsContent
  | CertificationsContent
  | LanguagesContent
  | PublicationContent
  | CustomContent;

export type SectionType =
  | 'personalInfo'
  | 'summary'
  | 'education'
  | 'experience'
  | 'project'
  | 'skills'
  | 'certification'
  | 'language'
  | 'publication'
  | 'custom';

export interface EditorSection {
  id: string;
  type: SectionType;
  title: string;
  order: number;
  visible: boolean;
  content: SectionContent;
}

// Wire format for the API: structured content is serialized to a JSON string
// (the backend stores `content` as text). Matches SectionRequest from the API spec.
export interface SerializedSection {
  id?: string | number;
  type: SectionType;
  title?: string;
  sortOrder?: number;
  visible?: boolean;
  content: string;
}

export interface ThemeConfig {
  primaryColor: string;
  accentColor: string;
  fontFamily: string;
  fontSize: 'small' | 'medium' | 'large';
  lineSpacing: number;
  margin: { top: number; right: number; bottom: number; left: number };
  sectionSpacing: number;
  avatarStyle?: 'circle' | 'oneInch';
}

// Canonical ordering of every supported section type. The editor sidebar lists
// these in order; `DEFAULT_SECTIONS` is the subset created for a new resume.
export const SECTION_TYPES: SectionType[] = [
  'personalInfo',
  'summary',
  'education',
  'experience',
  'project',
  'skills',
  'certification',
  'language',
  'publication',
  'custom',
];

export const SECTION_LABELS: Record<SectionType, string> = {
  personalInfo: '个人信息',
  summary: '个人概述',
  education: '教育经历',
  experience: '工作经历',
  project: '项目经历',
  skills: '技能',
  certification: '证书',
  language: '语言能力',
  publication: '出版物',
  custom: '自定义模块',
};

// The default sections used when creating a brand-new resume.
export const DEFAULT_SECTIONS: SectionType[] = [
  'personalInfo',
  'summary',
  'education',
  'experience',
  'project',
  'skills',
];

// The full 50-template set is the canonical source of truth in
// src/lib/constants.ts. Re-export it here so consumers can import template
// metadata from the editor types module without a second import path.
export { TEMPLATES, TEMPLATE_LABELS } from '../lib/constants';

export const AUTOSAVE_DELAY = 800;
export const MAX_UNDO_STACK = 50;

export const DEFAULT_THEME: ThemeConfig = {
  primaryColor: '#1a1a1a',
  accentColor: '#0066cc',
  fontFamily: 'Inter',
  fontSize: 'medium',
  lineSpacing: 1.5,
  margin: { top: 20, right: 20, bottom: 20, left: 20 },
  sectionSpacing: 16,
  avatarStyle: 'oneInch',
};
