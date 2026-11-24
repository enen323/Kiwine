"use client";

import { Check, ChevronDown } from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { CompanyEnum, PositionEnum } from "@/types";
import type { InterviewOption } from "@/types";
import { cn } from "@/lib/utils";

interface InterviewMultiSelectProps {
  options: InterviewOption[];
  selected: string[];
  onChange: (ids: string[]) => void;
  /** 选项仍在加载时禁用 */
  loading?: boolean;
}

function labelOf(opt: InterviewOption): string {
  const company = (CompanyEnum as Record<string, string>)[opt.company] ?? opt.company;
  const position = (PositionEnum as Record<string, string>)[opt.position] ?? opt.position;
  return `${company} · ${position}`;
}

function formatDate(iso: string): string {
  return iso.replace("T", " ").slice(0, 16);
}

export function InterviewMultiSelect({
  options,
  selected,
  onChange,
  loading,
}: InterviewMultiSelectProps) {
  const toggle = (id: string) => {
    onChange(
      selected.includes(id)
        ? selected.filter((x) => x !== id)
        : [...selected, id],
    );
  };

  const allSelected = options.length > 0 && selected.length === options.length;

  const summary =
    selected.length === 0
      ? "选择特定面试…"
      : `已选 ${selected.length} / ${options.length} 场`;

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          disabled={loading}
          className="w-full justify-between sm:w-[280px]"
        >
          <span className="truncate">{summary}</span>
          <ChevronDown className="size-4 shrink-0 opacity-60" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[300px] p-0" align="start">
        <div className="flex items-center justify-between px-3 py-2">
          <span className="text-sm font-medium">选择面试场次</span>
          <button
            type="button"
            onClick={() => onChange(allSelected ? [] : options.map((o) => String(o.id)))}
            className="text-xs text-brand hover:underline"
          >
            {allSelected ? "清空" : "全选"}
          </button>
        </div>
        <Separator />
        <ScrollArea className="max-h-[280px]">
          <div className="p-1">
            {options.length === 0 ? (
              <p className="px-3 py-6 text-center text-sm text-muted-foreground">
                暂无已完成的面试
              </p>
            ) : (
              options.map((opt) => {
                const id = String(opt.id);
                const checked = selected.includes(id);
                return (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => toggle(id)}
                    className={cn(
                      "flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm transition-colors hover:bg-accent",
                      checked && "bg-brand-muted/40",
                    )}
                  >
                    <span
                      className={cn(
                        "flex size-4 shrink-0 items-center justify-center rounded border",
                        checked
                          ? "border-brand bg-brand text-brand-foreground"
                          : "border-border",
                      )}
                    >
                      {checked && <Check className="size-3" />}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium">{labelOf(opt)}</span>
                      <span className="block text-xs text-muted-foreground">
                        {formatDate(opt.completedAt)}
                        {opt.scoreTotal != null && ` · ${opt.scoreTotal} 分`}
                      </span>
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </ScrollArea>
      </PopoverContent>
    </Popover>
  );
}
