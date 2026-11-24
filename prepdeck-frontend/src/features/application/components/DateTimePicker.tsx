import * as React from 'react';
import { CalendarIcon, XIcon } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { dateToLocalInput, localInputToDate, formatDateTime } from '../utils';

interface DateTimePickerProps {
  value?: Date | null;
  onChange: (date: Date | undefined) => void;
  placeholder?: string;
}

/**
 * 零依赖的日期时间选择器：Popover 包裹原生 datetime-local 输入。
 * 对外暴露 Date（本地时区），空值传 undefined。
 */
export function DateTimePicker({ value, onChange, placeholder = '默认为当前时间' }: DateTimePickerProps) {
  const [open, setOpen] = React.useState(false);
  const localValue = value ? dateToLocalInput(value) : '';

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          className="w-full justify-start text-left font-normal"
        >
          <CalendarIcon className="mr-2 size-4 opacity-60" />
          {value ? (
            formatDateTime(value.toISOString())
          ) : (
            <span className="text-muted-foreground">{placeholder}</span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-3" align="start">
        <Input
          type="datetime-local"
          value={localValue}
          onChange={(e) => {
            const d = localInputToDate(e.target.value);
            onChange(d);
          }}
        />
        <div className="mt-2 flex justify-end gap-2">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => {
              onChange(undefined);
              setOpen(false);
            }}
          >
            <XIcon className="mr-1 size-3" />
            清除
          </Button>
          <Button type="button" size="sm" onClick={() => setOpen(false)}>
            确定
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
