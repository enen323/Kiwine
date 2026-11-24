import { Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EditableText } from '../fields/editable-text';
import { FieldWrapper } from '../fields/field-wrapper';
import { generateId } from '../../../lib/editor-utils';
import type { EditorSection, PublicationContent, PublicationItem } from '../../../types/editor';

interface Props {
  section: EditorSection;
  onUpdate: (content: Partial<PublicationContent>) => void;
}

export function PublicationsSection({ section, onUpdate }: Props) {
  const content = section.content as PublicationContent;
  const items = Array.isArray(content.items) ? content.items : [];

  const addItem = () => {
    const newItem: PublicationItem = { id: generateId(), title: '', authors: '', venue: '', date: '', url: '', description: '' };
    onUpdate({ items: [...items, newItem] });
  };

  const updateItem = (index: number, data: Partial<PublicationItem>) => {
    const updated = items.map((item, i) => (i === index ? { ...item, ...data } : item));
    onUpdate({ items: updated });
  };

  const removeItem = (index: number) => {
    onUpdate({ items: items.filter((_, i) => i !== index) });
  };

  return (
    <div className="flex flex-col gap-0">
      {items.map((item, index) => (
        <div key={item.id || `pub-${index}`} className="flex flex-col gap-2.5 py-3">
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
          <EditableText label="标题" value={item.title} onChange={(v) => updateItem(index, { title: v })} />
          <FieldWrapper>
            <EditableText label="作者" value={item.authors || ''} onChange={(v) => updateItem(index, { authors: v })} />
            <EditableText label="发表载体" value={item.venue || ''} onChange={(v) => updateItem(index, { venue: v })} />
          </FieldWrapper>
          <FieldWrapper>
            <EditableText label="发表时间" value={item.date || ''} onChange={(v) => updateItem(index, { date: v })} />
            <EditableText label="链接" value={item.url || ''} onChange={(v) => updateItem(index, { url: v })} />
          </FieldWrapper>
          <EditableText label="简介" value={item.description || ''} onChange={(v) => updateItem(index, { description: v })} />
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
        添加出版物
      </Button>
    </div>
  );
}
