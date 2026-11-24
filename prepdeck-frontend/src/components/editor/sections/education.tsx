import { Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EditableText } from '../fields/editable-text';
import { EditableDate } from '../fields/editable-date';
import { EditableList } from '../fields/editable-list';
import { FieldWrapper } from '../fields/field-wrapper';
import { generateId } from '../../../lib/editor-utils';
import type { EditorSection, EducationContent, EducationItem } from '../../../types/editor';

interface Props {
  section: EditorSection;
  onUpdate: (content: Partial<EducationContent>) => void;
}

export function EducationSection({ section, onUpdate }: Props) {
  const content = section.content as EducationContent;
  const items = Array.isArray(content.items) ? content.items : [];

  const addItem = () => {
    const newItem: EducationItem = {
      id: generateId(),
      institution: '',
      degree: '',
      field: '',
      startDate: '',
      endDate: '',
      highlights: [],
    };
    onUpdate({ items: [...items, newItem] });
  };

  const updateItem = (index: number, data: Partial<EducationItem>) => {
    const updated = items.map((item, i) => (i === index ? { ...item, ...data } : item));
    onUpdate({ items: updated });
  };

  const removeItem = (index: number) => {
    onUpdate({ items: items.filter((_, i) => i !== index) });
  };

  return (
    <div className="flex flex-col gap-0">
      {items.map((item, index) => (
        <div key={item.id || `edu-${index}`} className="flex flex-col gap-2.5 py-3">
          {index > 0 && <div className="mb-3 h-px bg-border" />}
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-semibold text-muted-foreground">#{index + 1}</span>
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              className="text-muted-foreground hover:bg-accent hover:text-foreground"
              onClick={() => removeItem(index)}
              title="删除"
            >
              <Trash2 />
            </Button>
          </div>
          <FieldWrapper>
            <EditableText label="学校" value={item.institution} onChange={(v) => updateItem(index, { institution: v })} />
            <EditableText label="学位" value={item.degree} onChange={(v) => updateItem(index, { degree: v })} />
          </FieldWrapper>
          <FieldWrapper>
            <EditableText label="专业" value={item.field} onChange={(v) => updateItem(index, { field: v })} />
            <EditableText label="GPA" value={item.gpa || ''} onChange={(v) => updateItem(index, { gpa: v })} />
          </FieldWrapper>
          <FieldWrapper>
            <EditableDate label="开始时间" value={item.startDate} onChange={(v) => updateItem(index, { startDate: v })} />
            <EditableDate label="结束时间" value={item.endDate} onChange={(v) => updateItem(index, { endDate: v })} />
          </FieldWrapper>
          <EditableList label="亮点" items={item.highlights || []} onChange={(v) => updateItem(index, { highlights: v })} />
        </div>
      ))}
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="mt-1.5 self-start rounded-md border border-dashed border-border bg-muted/40 px-3.5 py-2 text-[13px] text-muted-foreground hover:border-brand hover:text-brand"
        onClick={addItem}
      >
        <Plus />
        添加教育经历
      </Button>
    </div>
  );
}
