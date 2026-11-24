import { Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EditableText } from '../fields/editable-text';
import { FieldWrapper } from '../fields/field-wrapper';
import { generateId } from '../../../lib/editor-utils';
import type { EditorSection, LanguagesContent, LanguageItem } from '../../../types/editor';

interface Props {
  section: EditorSection;
  onUpdate: (content: Partial<LanguagesContent>) => void;
}

export function LanguagesSection({ section, onUpdate }: Props) {
  const content = section.content as LanguagesContent;
  const items = Array.isArray(content.items) ? content.items : [];

  const addItem = () => {
    const newItem: LanguageItem = { id: generateId(), language: '', proficiency: '' };
    onUpdate({ items: [...items, newItem] });
  };

  const updateItem = (index: number, data: Partial<LanguageItem>) => {
    const updated = items.map((item, i) => (i === index ? { ...item, ...data } : item));
    onUpdate({ items: updated });
  };

  const removeItem = (index: number) => {
    onUpdate({ items: items.filter((_, i) => i !== index) });
  };

  return (
    <div className="flex flex-col gap-0">
      {items.map((item, index) => (
        <div key={item.id || `lang-${index}`} className="flex flex-col gap-2.5 py-3">
          {index > 0 && <div className="mb-3 h-px bg-border" />}
          <FieldWrapper>
            <EditableText label="语言" value={item.language} onChange={(v) => updateItem(index, { language: v })} />
            <div className="flex flex-1 items-center gap-2">
              <div className="min-w-0 flex-1">
                <EditableText label="熟练程度" value={item.proficiency} onChange={(v) => updateItem(index, { proficiency: v })} />
              </div>
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
          </FieldWrapper>
          <EditableText label="说明" value={(item as LanguageItem).description || ''} onChange={(v) => updateItem(index, { description: v })} />
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
        添加语言
      </Button>
    </div>
  );
}
