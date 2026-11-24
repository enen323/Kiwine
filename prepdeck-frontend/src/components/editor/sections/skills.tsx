import { Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EditableText } from '../fields/editable-text';
import { EditableList } from '../fields/editable-list';
import { generateId } from '../../../lib/editor-utils';
import type { EditorSection, SkillsContent, SkillCategory } from '../../../types/editor';

interface Props {
  section: EditorSection;
  onUpdate: (content: Partial<SkillsContent>) => void;
}

export function SkillsSection({ section, onUpdate }: Props) {
  const content = section.content as SkillsContent;
  const categories = Array.isArray(content.categories) ? content.categories : [];

  const addCategory = () => {
    const newCategory: SkillCategory = { id: generateId(), name: '', skills: [] };
    onUpdate({ categories: [...categories, newCategory] });
  };

  const updateCategory = (index: number, data: Partial<SkillCategory>) => {
    const updated = categories.map((cat, i) => (i === index ? { ...cat, ...data } : cat));
    onUpdate({ categories: updated });
  };

  const removeCategory = (index: number) => {
    onUpdate({ categories: categories.filter((_, i) => i !== index) });
  };

  return (
    <div className="flex flex-col gap-0">
      {categories.map((cat, index) => (
        <div key={cat.id || `cat-${index}-${cat.name}`} className="flex flex-col gap-2.5 py-3">
          {index > 0 && <div className="mb-3 h-px bg-border" />}
          <div className="flex items-center justify-between gap-2">
            <div className="min-w-0 flex-1">
              <EditableText label="技能分类" value={cat.name} onChange={(v) => updateCategory(index, { name: v })} />
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              className="text-muted-foreground hover:bg-accent hover:text-foreground"
              onClick={() => removeCategory(index)}
              title="删除"
            >
              <Trash2 />
            </Button>
          </div>
          <EditableList label="技能" items={cat.skills || []} onChange={(v) => updateCategory(index, { skills: v })} />
        </div>
      ))}
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="mt-1.5 self-start rounded-md border border-dashed border-border bg-muted/40 px-3.5 py-2 text-[13px] text-muted-foreground hover:border-brand hover:text-brand"
        onClick={addCategory}
      >
        <Plus />
        添加技能分类
      </Button>
    </div>
  );
}
