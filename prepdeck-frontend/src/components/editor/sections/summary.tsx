import { EditableRichText } from '../fields/editable-rich-text';
import type { EditorSection, SummaryContent } from '../../../types/editor';

interface Props {
  section: EditorSection;
  onUpdate: (content: Partial<SummaryContent>) => void;
}

export function SummarySection({ section, onUpdate }: Props) {
  const content = section.content as SummaryContent;
  return (
    <div className="flex flex-col gap-2.5">
      <EditableRichText
        label="个人概述"
        value={content.text}
        onChange={(v) => onUpdate({ text: v })}
        rows={4}
        placeholder="一句话概括你的核心优势与职业目标…"
      />
    </div>
  );
}
