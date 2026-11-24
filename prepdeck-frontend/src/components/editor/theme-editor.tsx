import { useState, type ReactNode, type ElementType } from 'react';
import {
  Palette,
  Type,
  Space,
  Sparkles,
  ChevronDown,
  ChevronRight,
  RotateCcw,
  LayoutGrid,
  Check,
} from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Slider } from '@/components/ui/slider';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { cn } from '@/lib/utils';
import { useResumeStore } from '../../stores/resume-store';
import type { ThemeConfig } from '../../types/editor';
import { DEFAULT_THEME } from '../../types/editor';
import { TEMPLATE_LABELS, TEMPLATE_ACCENTS, TEMPLATE_GROUPS } from '../../lib/constants';

interface PresetTheme {
  id: string;
  colors: [string, string, string, string];
  config: ThemeConfig;
}

const PRESET_THEMES: PresetTheme[] = [
  {
    id: 'classic',
    colors: ['#1a1a1a', '#3b82f6', '#ffffff', '#374151'],
    config: { primaryColor: '#1a1a1a', accentColor: '#3b82f6', fontFamily: 'Georgia', fontSize: 'medium', lineSpacing: 1.5, margin: { top: 24, right: 24, bottom: 24, left: 24 }, sectionSpacing: 16 },
  },
  {
    id: 'modern',
    colors: ['#0f172a', '#6366f1', '#f8fafc', '#475569'],
    config: { primaryColor: '#0f172a', accentColor: '#6366f1', fontFamily: 'Inter', fontSize: 'medium', lineSpacing: 1.6, margin: { top: 20, right: 20, bottom: 20, left: 20 }, sectionSpacing: 14 },
  },
  {
    id: 'minimal',
    colors: ['#27272a', '#a1a1aa', '#ffffff', '#52525b'],
    config: { primaryColor: '#27272a', accentColor: '#a1a1aa', fontFamily: 'Helvetica', fontSize: 'small', lineSpacing: 1.4, margin: { top: 28, right: 28, bottom: 28, left: 28 }, sectionSpacing: 12 },
  },
  {
    id: 'elegant',
    colors: ['#1c1917', '#b45309', '#fffbeb', '#57534e'],
    config: { primaryColor: '#1c1917', accentColor: '#b45309', fontFamily: 'Palatino', fontSize: 'medium', lineSpacing: 1.6, margin: { top: 26, right: 26, bottom: 26, left: 26 }, sectionSpacing: 18 },
  },
  {
    id: 'bold',
    colors: ['#020617', '#e11d48', '#fff1f2', '#334155'],
    config: { primaryColor: '#020617', accentColor: '#e11d48', fontFamily: 'Arial', fontSize: 'large', lineSpacing: 1.5, margin: { top: 20, right: 20, bottom: 20, left: 20 }, sectionSpacing: 16 },
  },
  {
    id: 'creative',
    colors: ['#134e4a', '#0d9488', '#f0fdfa', '#115e59'],
    config: { primaryColor: '#134e4a', accentColor: '#0d9488', fontFamily: 'Verdana', fontSize: 'medium', lineSpacing: 1.5, margin: { top: 22, right: 22, bottom: 22, left: 22 }, sectionSpacing: 14 },
  },
];

const FONT_OPTIONS = ['Inter', 'Georgia', 'Helvetica', 'Arial', 'Palatino', 'Verdana', 'Times New Roman', 'Garamond', 'Courier New'];
const FONT_SIZE_OPTIONS: { value: ThemeConfig['fontSize']; label: string }[] = [
  { value: 'small', label: '小' },
  { value: 'medium', label: '中' },
  { value: 'large', label: '大' },
];

function ColorPickerField({ label, value, onChange }: { label: string; value: string; onChange: (color: string) => void }) {
  return (
    <div className="mb-3 flex items-center justify-between gap-3">
      <Label>{label}</Label>
      <div className="flex items-center gap-2">
        <input type="color" value={value} onChange={(e) => onChange(e.target.value)} className="size-8 cursor-pointer rounded-md border border-border bg-none p-0.5" />
        <Input
          className="w-[110px]"
          value={value}
          onChange={(e) => {
            const v = e.target.value;
            if (/^#[0-9a-fA-F]{0,6}$/.test(v)) onChange(v);
          }}
          placeholder="#000000"
        />
      </div>
    </div>
  );
}

function ThemeSection({ icon: Icon, title, children, defaultOpen = true }: { icon: ElementType; title: string; children: ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border-b border-border">
      <button type="button" className="flex w-full items-center gap-2 border-0 bg-transparent px-1 py-3.5 text-left text-sm font-semibold text-foreground" onClick={() => setOpen(!open)}>
        {open ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
        <Icon size={14} />
        <span>{title}</span>
      </button>
      {open && <div className="px-1 pb-3.5">{children}</div>}
    </div>
  );
}

export function ThemeEditor() {
  const currentResume = useResumeStore((s) => s.currentResume);
  const updateTheme = useResumeStore((s) => s.updateTheme);
  const setTemplate = useResumeStore((s) => s.setTemplate);

  // Deep-merge margin so controls reading `themeConfig.margin[side]` don't
  // crash when the stored themeConfig lacks the `margin` sub-object.
  const themeConfig: ThemeConfig = {
    ...DEFAULT_THEME,
    ...(currentResume?.themeConfig || {}),
    margin: { ...DEFAULT_THEME.margin, ...(currentResume?.themeConfig?.margin || {}) },
  };

  return (
    <div className="flex h-full w-80 shrink-0 flex-col border-l bg-card">
      <div className="flex items-center justify-between border-b p-4">
        <h3 className="m-0 flex items-center gap-2 text-[15px] font-semibold">
          <Palette size={16} className="text-muted-foreground" />
          主题与样式
        </h3>
        <Button variant="ghost" size="icon-sm" title="重置" onClick={() => updateTheme(DEFAULT_THEME)}>
          <RotateCcw size={14} />
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 pb-6">
        <ThemeSection icon={LayoutGrid} title={`模板 (${TEMPLATE_GROUPS.reduce((n, g) => n + g.items.length, 0)})`} defaultOpen={false}>
          <div className="flex flex-col gap-3.5">
            {TEMPLATE_GROUPS.map((group) => (
              <div key={group.id}>
                <div className="mb-1.5 text-[11px] font-semibold text-muted-foreground">{group.label}</div>
                <div className="grid grid-cols-3 gap-2">
                  {group.items.map((tpl) => {
                    const isSelected = currentResume?.template === tpl;
                    const accent = TEMPLATE_ACCENTS[tpl] || '#3b82f6';
                    return (
                      <button
                        key={tpl}
                        type="button"
                        className={cn(
                          'relative flex flex-col items-center gap-1.5 rounded-md border border-border bg-muted/40 p-2.5 cursor-pointer hover:border-brand',
                          isSelected && 'border-brand bg-brand/10'
                        )}
                        style={isSelected ? { borderColor: accent, background: `${accent}0d` } : undefined}
                        onClick={() => setTemplate(tpl)}
                      >
                        <div className="flex h-12 w-full flex-col gap-0.5 rounded border border-border bg-background p-1.5">
                          <span className="mb-0.5 h-1 rounded-full" style={{ background: accent }} />
                          <span className="h-[5px] w-3/5 rounded-full bg-foreground/70" />
                          <span className="h-0.5 w-full rounded-full bg-muted" />
                          <span className="h-0.5 w-3/5 rounded-full bg-muted" />
                          <span className="flex-1" />
                          <span className="h-0.5 w-full rounded-full bg-muted" />
                          <span className="h-0.5 w-3/5 rounded-full bg-muted" />
                        </div>
                        {isSelected && (
                          <span className="absolute right-1 top-1 flex size-4 items-center justify-center rounded-full bg-brand text-white">
                            <Check size={11} />
                          </span>
                        )}
                        <span className="text-[11px] text-muted-foreground">{TEMPLATE_LABELS[tpl] || tpl}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </ThemeSection>

        <ThemeSection icon={Sparkles} title="预设主题">
          <div className="grid grid-cols-3 gap-2">
            {PRESET_THEMES.map((preset) => (
              <button key={preset.id} type="button" className="flex flex-col items-center gap-1.5 rounded-md border border-border bg-card p-2 cursor-pointer hover:border-brand" onClick={() => updateTheme(preset.config)} title={preset.id}>
                <div className="flex gap-0.5">
                  {preset.colors.map((color, i) => (
                    <span key={i} className="size-3 rounded-full" style={{ background: color }} />
                  ))}
                </div>
                <span className="text-[11px] capitalize text-muted-foreground">{preset.id}</span>
              </button>
            ))}
          </div>
        </ThemeSection>

        <ThemeSection icon={Palette} title="颜色">
          <ColorPickerField label="主色" value={themeConfig.primaryColor} onChange={(c) => updateTheme({ primaryColor: c })} />
          <ColorPickerField label="强调色" value={themeConfig.accentColor} onChange={(c) => updateTheme({ accentColor: c })} />
        </ThemeSection>

        <ThemeSection icon={Type} title="字体">
          <div className="mb-3 flex items-center justify-between gap-3">
            <Label>字体</Label>
            <Select value={themeConfig.fontFamily} onValueChange={(v) => updateTheme({ fontFamily: v })}>
              <SelectTrigger className="w-[180px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {FONT_OPTIONS.map((font) => (
                  <SelectItem key={font} value={font}>
                    {font}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="mb-3 flex items-center justify-between gap-3">
            <Label>字号</Label>
            <div className="inline-flex w-full max-w-[220px] gap-0.5 rounded-md bg-muted p-0.5">
              {FONT_SIZE_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={cn(
                    'flex-1 justify-center rounded px-3 py-1.5 text-[13px] cursor-pointer',
                    themeConfig.fontSize === opt.value ? 'bg-background text-brand shadow-sm' : 'text-muted-foreground'
                  )}
                  onClick={() => updateTheme({ fontSize: opt.value })}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>
          <div className="mb-3">
            <div className="mb-1.5 flex w-full items-center justify-between">
              <Label>行距</Label>
              <span className="text-xs tabular-nums text-muted-foreground">{themeConfig.lineSpacing.toFixed(1)}</span>
            </div>
            <Slider
              min={1.0}
              max={2.5}
              step={0.1}
              value={[themeConfig.lineSpacing]}
              onValueChange={(v) => updateTheme({ lineSpacing: v[0] })}
              className="[&_[data-slot=slider-range]]:bg-brand [&_[data-slot=slider-thumb]]:border-brand"
            />
          </div>
        </ThemeSection>

        <ThemeSection icon={Space} title="间距">
          <div className="mb-3">
            <div className="mb-1.5 flex w-full items-center justify-between">
              <Label>模块间距</Label>
              <span className="text-xs tabular-nums text-muted-foreground">{themeConfig.sectionSpacing}px</span>
            </div>
            <Slider
              min={4}
              max={32}
              step={2}
              value={[themeConfig.sectionSpacing]}
              onValueChange={(v) => updateTheme({ sectionSpacing: v[0] })}
              className="[&_[data-slot=slider-range]]:bg-brand [&_[data-slot=slider-thumb]]:border-brand"
            />
          </div>
          <div>
            <Label>页边距 (px)</Label>
            <div className="mt-1.5 grid grid-cols-4 gap-2">
              {(['top', 'right', 'bottom', 'left'] as const).map((side) => (
                <div key={side} className="flex flex-col gap-1">
                  <span className="text-[11px] uppercase text-muted-foreground">{side}</span>
                  <Input
                    type="number"
                    className="text-center"
                    value={themeConfig.margin[side]}
                    min={0}
                    max={60}
                    onChange={(e) =>
                      updateTheme({
                        margin: { ...themeConfig.margin, [side]: Math.max(0, Math.min(60, Number(e.target.value) || 0)) },
                      })
                    }
                  />
                </div>
              ))}
            </div>
          </div>
        </ThemeSection>
      </div>
    </div>
  );
}
