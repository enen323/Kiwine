import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';

interface EditableDateProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

// Lightweight month/text date input (type=month on supporting browsers,
// falls back to text). Avoids pulling in a heavy date-picker dependency.
export function EditableDate({ label, value, onChange, placeholder }: EditableDateProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label>{label}</Label>
      <Input
        type="month"
        value={value || ''}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder || label}
      />
    </div>
  );
}
