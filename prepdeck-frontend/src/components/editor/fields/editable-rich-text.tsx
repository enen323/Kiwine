import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

interface EditableRichTextProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  rows?: number;
}

// Plain-textarea "rich text" editor. Uses CSS field-sizing:content to grow
// with content so multi-line descriptions are comfortable to edit.
export function EditableRichText({
  label,
  value,
  onChange,
  placeholder,
  rows = 3,
}: EditableRichTextProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label>{label}</Label>
      <Textarea
        value={value || ''}
        rows={rows}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder || label}
      />
    </div>
  );
}
