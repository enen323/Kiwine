import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2, CheckCircle2, AlertTriangle, Sparkles } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { TEMPLATES, TEMPLATE_LABELS } from '@/lib/constants';
import { getAIHeaders, getAiKey, setAiKey } from '@/lib/ai-config';
import type { GenerateResumeResult } from '@/types';

interface GenerateResumeDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

// 固定业务状态流转：form → generating → success / error
type GenerateState = 'form' | 'generating' | 'success' | 'error';

// 仅当 open 为 true 时挂载内部组件，每次打开都是一份全新的表单状态，
// 避免用 effect 在关闭时同步重置 state（lint: react-hooks/set-state-in-effect）。
export function GenerateResumeDialog({ open, onOpenChange }: GenerateResumeDialogProps) {
  if (!open) return null;
  return <GenerateResumeDialogContent onOpenChange={onOpenChange} />;
}

function GenerateResumeDialogContent({ onOpenChange }: { onOpenChange: (open: boolean) => void }) {
  const navigate = useNavigate();

  // 表单字段
  const [jobTitle, setJobTitle] = useState('');
  const [yearsOfExperience, setYearsOfExperience] = useState<number | ''>('');
  const [skills, setSkills] = useState('');
  const [industry, setIndustry] = useState('');
  const [experience, setExperience] = useState('');
  const [template, setTemplate] = useState('classic');
  const [language, setLanguage] = useState<'zh' | 'en'>('zh');
  const [aiKey, setAiKeyState] = useState(() => getAiKey());

  // 业务状态
  const [state, setState] = useState<GenerateState>('form');
  const [error, setError] = useState('');
  const [result, setResult] = useState<GenerateResumeResult | null>(null);

  const handleGenerate = async () => {
    if (!jobTitle.trim()) return;
    setState('generating');
    setError('');

    // 密钥仅保存在本地浏览器，写入后立即随请求头携带，不会上传至服务端保存
    const key = aiKey.trim();
    if (key) setAiKey(key);

    try {
      const res = await fetch('/api/ai/generate-resume', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...getAIHeaders(),
        },
        body: JSON.stringify({
          jobTitle: jobTitle.trim(),
          yearsOfExperience: yearsOfExperience === '' ? undefined : Number(yearsOfExperience),
          skills: skills.trim()
            ? skills.split(/[\s,，、]+/).map((s) => s.trim()).filter(Boolean)
            : undefined,
          industry: industry.trim() || undefined,
          experience: experience.trim() || undefined,
          template,
          language,
        }),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || `生成失败（${res.status}）`);
      }

      const data = (await res.json()) as GenerateResumeResult;
      setResult(data);
      setState('success');
      // 生成成功后自动跳转到对应简历的在线编辑器
      window.setTimeout(() => {
        onOpenChange(false);
        navigate(`/editor/${data.resumeId}`);
      }, 1000);
    } catch (err) {
      setError(err instanceof Error ? err.message : '生成失败，请稍后重试');
      setState('error');
    }
  };

  const handleOpenResume = () => {
    if (result) {
      onOpenChange(false);
      navigate(`/editor/${result.resumeId}`);
    }
  };

  const handleRetry = () => {
    setState('form');
    setError('');
  };

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl p-0 gap-0 overflow-hidden">
        <DialogHeader className="px-6 pt-6 pb-0">
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="h-5 w-5 text-brand" />
            AI 生成简历
          </DialogTitle>
          <DialogDescription>
            填写目标岗位与背景信息，AI 将自动生成一份完整简历。
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[70vh] overflow-y-auto px-6 py-4 space-y-4">
          {state === 'form' && (
            <>
              {/* 必填：目标岗位 + 选填：工作年限、所属行业 */}
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
                    目标岗位 <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    value={jobTitle}
                    onChange={(e) => setJobTitle(e.target.value)}
                    placeholder="如：前端工程师"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
                    工作年限
                  </Label>
                  <Input
                    type="number"
                    min={0}
                    max={50}
                    value={yearsOfExperience}
                    onChange={(e) => setYearsOfExperience(e.target.value ? Number(e.target.value) : '')}
                    placeholder="3"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
                    所属行业
                  </Label>
                  <Input
                    value={industry}
                    onChange={(e) => setIndustry(e.target.value)}
                    placeholder="如：互联网 / 金融"
                  />
                </div>
              </div>

              {/* 选填：技能列表 */}
              <div className="space-y-1.5">
                <Label className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
                  技能列表
                </Label>
                <Input
                  value={skills}
                  onChange={(e) => setSkills(e.target.value)}
                  placeholder="用逗号或空格分隔，如：React, TypeScript, Node.js"
                />
              </div>

              {/* 选填：个人经历自由文本 */}
              <div className="space-y-1.5">
                <Label className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
                  个人经历
                </Label>
                <Textarea
                  value={experience}
                  onChange={(e) => setExperience(e.target.value)}
                  placeholder="简要描述你的工作 / 项目经历，AI 会据此丰富简历内容"
                  rows={4}
                  className="resize-none"
                />
              </div>

              {/* 选填：简历语种 + 选用模板 */}
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
                    简历语种
                  </Label>
                  <Select value={language} onValueChange={(v) => setLanguage(v as 'zh' | 'en')}>
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="zh">中文</SelectItem>
                      <SelectItem value="en">English</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
                    选用模板
                  </Label>
                  <Select value={template} onValueChange={setTemplate}>
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent className="max-h-64">
                      {TEMPLATES.map((tpl) => (
                        <SelectItem key={tpl} value={tpl}>
                          {TEMPLATE_LABELS[tpl] ?? tpl}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              {/* 选填：AI 密钥（仅保存在本地） */}
              <div className="space-y-1.5">
                <Label className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
                  AI 密钥（可选）
                </Label>
                <Input
                  type="password"
                  value={aiKey}
                  onChange={(e) => setAiKeyState(e.target.value)}
                  placeholder="sk-..."
                  autoComplete="off"
                />
                <p className="text-xs text-muted-foreground">
                  密钥仅保存在本机浏览器，不会上传至服务端。
                </p>
              </div>
            </>
          )}

          {state === 'generating' && (
            <div className="flex flex-col items-center justify-center py-8 text-center">
              <Loader2 className="h-8 w-8 animate-spin text-brand mb-3" />
              <p className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
                AI 正在生成简历…
              </p>
            </div>
          )}

          {state === 'success' && (
            <div className="flex flex-col items-center justify-center py-8 text-center">
              <CheckCircle2 className="h-8 w-8 text-green-500 mb-3" />
              <p className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
                简历已生成
              </p>
              <p className="text-xs text-zinc-500 mt-1">
                即将打开编辑器…
              </p>
            </div>
          )}

          {state === 'error' && (
            <div className="flex flex-col items-center justify-center py-6 text-center">
              <AlertTriangle className="h-8 w-8 text-red-500 mb-3" />
              <p className="text-sm font-medium text-red-600 dark:text-red-400">
                {error || '生成失败'}
              </p>
            </div>
          )}
        </div>

        {/* 底部操作区 */}
        <div className="flex justify-end gap-2 border-t border-zinc-100 px-6 py-4 dark:border-zinc-800">
          {state === 'form' && (
            <>
              <Button variant="outline" onClick={() => onOpenChange(false)} className="cursor-pointer">
                取消
              </Button>
              <Button
                onClick={handleGenerate}
                disabled={!jobTitle.trim()}
                className="cursor-pointer bg-brand text-brand-foreground hover:bg-brand/90"
              >
                <Sparkles className="mr-1.5 size-4" />
                生成简历
              </Button>
            </>
          )}
          {state === 'generating' && (
            <Button disabled className="cursor-pointer bg-brand text-brand-foreground">
              <Loader2 className="mr-1.5 size-4 animate-spin" />
              生成中…
            </Button>
          )}
          {state === 'success' && (
            <Button
              onClick={handleOpenResume}
              className="cursor-pointer bg-brand text-brand-foreground hover:bg-brand/90"
            >
              打开编辑器
            </Button>
          )}
          {state === 'error' && (
            <>
              <Button variant="outline" onClick={() => onOpenChange(false)} className="cursor-pointer">
                关闭
              </Button>
              <Button
                onClick={handleRetry}
                className="cursor-pointer bg-brand text-brand-foreground hover:bg-brand/90"
              >
                重试
              </Button>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
