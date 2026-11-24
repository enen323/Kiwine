import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';

interface EditableTextProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: string;
}

export function EditableText({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
}: EditableTextProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label>{label}</Label>
      <Input
        type={type}
        value={value || ''}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder || label}
      />
    </div>
  );
}
