import * as React from 'react';
import { XIcon } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';

interface ChannelAutocompleteProps {
  value?: string | null;
  onChange: (value: string | null) => void;
  options: string[];
  placeholder?: string;
  allowClear?: boolean;
  className?: string;
}

/**
 * 渠道自动补全：可自由输入，也可从建议列表中选择。
 * 建议值来自 DEFAULT_CHANNELS，输入时按前缀/包含过滤。零依赖实现（relative 容器 + 绝对定位建议层）。
 */
export function ChannelAutocomplete({
  value,
  onChange,
  options,
  placeholder = '如：Boss直聘',
  allowClear = true,
  className,
}: ChannelAutocompleteProps) {
  const [focused, setFocused] = React.useState(false);
  const wrapRef = React.useRef<HTMLDivElement>(null);

  const query = value ?? '';
  const filtered = React.useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return options;
    return options.filter((o) => o.toLowerCase().includes(q) && o.toLowerCase() !== q);
  }, [options, query]);

  const showList = focused && filtered.length > 0;

  // 点击外部关闭建议层
  React.useEffect(() => {
    if (!focused) return;
    const onDown = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setFocused(false);
      }
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [focused]);

  return (
    <div ref={wrapRef} className={cn('relative', className)}>
      <div className="relative">
        <Input
          value={query}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value.trim() ? e.target.value : null)}
          onFocus={() => setFocused(true)}
          className={cn(allowClear && value && 'pr-8')}
        />
        {allowClear && value && (
          <button
            type="button"
            aria-label="清除"
            onClick={() => onChange(null)}
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded-full p-0.5 text-muted-foreground hover:bg-accent hover:text-foreground"
          >
            <XIcon className="size-3.5" />
          </button>
        )}
      </div>
      {showList && (
        <div className="absolute z-50 mt-1 max-h-56 w-full overflow-auto rounded-md border bg-popover p-1 shadow-md">
          {filtered.map((o) => (
            <button
              key={o}
              type="button"
              // mousedown 先于 input blur，确保点击能命中
              onMouseDown={(e) => {
                e.preventDefault();
                onChange(o);
                setFocused(false);
              }}
              className="flex w-full items-center rounded-sm px-2 py-1.5 text-left text-sm outline-none transition-colors hover:bg-accent hover:text-accent-foreground"
            >
              {o}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
