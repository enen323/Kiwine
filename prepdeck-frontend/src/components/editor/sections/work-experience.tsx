import { Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EditableText } from '../fields/editable-text';
import { EditableDate } from '../fields/editable-date';
import { EditableRichText } from '../fields/editable-rich-text';
import { EditableList } from '../fields/editable-list';
import { FieldWrapper } from '../fields/field-wrapper';
import { generateId } from '../../../lib/editor-utils';
import type { EditorSection, WorkExperienceContent, WorkExperienceItem } from '../../../types/editor';

interface Props {
  section: EditorSection;
  onUpdate: (content: Partial<WorkExperienceContent>) => void;
}

export function WorkExperienceSection({ section, onUpdate }: Props) {
  const content = section.content as WorkExperienceContent;
  const items = Array.isArray(content.items) ? content.items : [];

  const addItem = () => {
    const newItem: WorkExperienceItem = {
      id: generateId(),
      company: '',
      position: '',
      location: '',
      startDate: '',
      endDate: null,
      current: false,
      description: '',
      technologies: [],
      highlights: [],
    };
    onUpdate({ items: [...items, newItem] });
  };

  const updateItem = (index: number, data: Partial<WorkExperienceItem>) => {
    const updated = items.map((item, i) => (i === index ? { ...item, ...data } : item));
    onUpdate({ items: updated });
  };

  const removeItem = (index: number) => {
    onUpdate({ items: items.filter((_, i) => i !== index) });
  };

  return (
    <div className="flex flex-col gap-0">
      {items.map((item, index) => (
        <div key={item.id || `we-${index}`} className="flex flex-col gap-2.5 py-3">
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
            <EditableText label="公司" value={item.company} onChange={(v) => updateItem(index, { company: v })} />
            <EditableText label="职位" value={item.position} onChange={(v) => updateItem(index, { position: v })} />
          </FieldWrapper>
          <FieldWrapper>
            <EditableDate label="开始时间" value={item.startDate} onChange={(v) => updateItem(index, { startDate: v })} />
            <EditableDate label="结束时间" value={item.endDate || ''} onChange={(v) => updateItem(index, { endDate: v || null, current: !v })} />
          </FieldWrapper>
          <EditableRichText label="工作描述" value={item.description} onChange={(v) => updateItem(index, { description: v })} />
          <EditableList label="技术栈" items={item.technologies || []} onChange={(v) => updateItem(index, { technologies: v })} />
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
        添加工作经历
      </Button>
    </div>
  );
}
