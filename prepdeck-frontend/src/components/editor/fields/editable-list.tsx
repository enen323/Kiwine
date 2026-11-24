import { Plus, Trash2 } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';

interface EditableListProps {
  label: string;
  items: string[];
  onChange: (items: string[]) => void;
  placeholder?: string;
}

// Tag-style list editor: each entry is a removable text input with an
// "add" button. Used for skills, technologies, highlights, etc.
export function EditableList({ label, items, onChange, placeholder }: EditableListProps) {
  const list = Array.isArray(items) ? items : [];

  const addItem = () => onChange([...list, '']);

  const updateItem = (index: number, value: string) => {
    const updated = [...list];
    updated[index] = value;
    onChange(updated);
  };

  const removeItem = (index: number) => {
    onChange(list.filter((_, i) => i !== index));
  };

  return (
    <div className="flex flex-col gap-1.5">
      <Label>{label}</Label>
      <div className="flex flex-col gap-1.5">
        {list.map((item, index) => (
          <div key={index} className="flex items-center gap-1.5">
            <Input
              className="flex-1"
              value={item}
              onChange={(e) => updateItem(index, e.target.value)}
              placeholder={placeholder}
            />
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              className="shrink-0 text-muted-foreground hover:bg-accent hover:text-foreground"
              onClick={() => removeItem(index)}
              title="删除"
            >
              <Trash2 />
            </Button>
          </div>
        ))}
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="self-start text-brand hover:text-brand/80"
          onClick={addItem}
        >
          <Plus />
          添加
        </Button>
      </div>
    </div>
  );
}
