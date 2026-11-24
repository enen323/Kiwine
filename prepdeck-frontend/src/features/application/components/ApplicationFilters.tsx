import * as React from 'react';
import { Search } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { STATUS_LABELS, STATUS_OPTIONS } from '../types';

interface ApplicationFiltersProps {
  keyword: string;
  status: string;
  onKeywordSubmit: (keyword: string) => void;
  onStatusChange: (status: string) => void;
}

/** 筛选区：仅搜索框 + 状态下拉，不额外加筛选条件。 */
export function ApplicationFilters({
  keyword,
  status,
  onKeywordSubmit,
  onStatusChange,
}: ApplicationFiltersProps) {
  const [input, setInput] = React.useState(keyword);

  // 外部 keyword 变化（如 URL 恢复）时同步输入框
  React.useEffect(() => {
    setInput(keyword);
  }, [keyword]);

  const submit = () => onKeywordSubmit(input.trim());

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
      <div className="relative w-full sm:w-[280px]">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') submit();
          }}
          placeholder="搜索公司或职位"
          className="pl-9 pr-2"
        />
      </div>
      <Button type="button" variant="outline" onClick={submit} className="shrink-0">
        <Search className="mr-1 size-4" />
        搜索
      </Button>
      <Select
        value={status || '__all__'}
        onValueChange={(v) => onStatusChange(v === '__all__' ? '' : v)}
      >
        <SelectTrigger className="w-full sm:w-[180px]">
          <SelectValue placeholder="状态筛选" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="__all__">状态筛选</SelectItem>
          {STATUS_OPTIONS.map((s) => (
            <SelectItem key={s} value={s}>
              {STATUS_LABELS[s]}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
