import { Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EditableText } from '../fields/editable-text';
import { FieldWrapper } from '../fields/field-wrapper';
import { generateId } from '../../../lib/editor-utils';
import type { EditorSection, CertificationsContent, CertificationItem } from '../../../types/editor';

interface Props {
  section: EditorSection;
  onUpdate: (content: Partial<CertificationsContent>) => void;
}

export function CertificationsSection({ section, onUpdate }: Props) {
  const content = section.content as CertificationsContent;
  const items = Array.isArray(content.items) ? content.items : [];

  const addItem = () => {
    const newItem: CertificationItem = { id: generateId(), name: '', issuer: '', date: '' };
    onUpdate({ items: [...items, newItem] });
  };

  const updateItem = (index: number, data: Partial<CertificationItem>) => {
    const updated = items.map((item, i) => (i === index ? { ...item, ...data } : item));
    onUpdate({ items: updated });
  };

  const removeItem = (index: number) => {
    onUpdate({ items: items.filter((_, i) => i !== index) });
  };

  return (
    <div className="flex flex-col gap-0">
      {items.map((item, index) => (
        <div key={item.id || `cert-${index}`} className="flex flex-col gap-2.5 py-3">
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
            <EditableText label="证书名称" value={item.name} onChange={(v) => updateItem(index, { name: v })} />
            <EditableText label="颁发机构" value={item.issuer} onChange={(v) => updateItem(index, { issuer: v })} />
          </FieldWrapper>
          <EditableText label="获得时间" value={item.date} onChange={(v) => updateItem(index, { date: v })} />
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
        添加证书
      </Button>
    </div>
  );
}
