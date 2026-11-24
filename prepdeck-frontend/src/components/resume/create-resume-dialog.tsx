// CreateResumeDialog — 新建简历对话框
//
// 重做后的版本：模板选择模块不再用文字按钮，而是按 TEMPLATE_GROUPS 分组的
// 缩略图画廊——每个模板渲染真实的 TemplateThumbnail（794px 真实版式等比缩放），
// 所见即所得。「从模板创建」与「从文件上传」两个 Tab 共用同一个 TemplateGallery，
// 避免旧版两份网格重复维护。
//
// 性能：6 个模板同时挂载 6 份完整简历预览。每份都是纯 inline-style div，
// 单份约一两百个节点，总量浏览器可承受；卡片再加 content-visibility:auto，
// 让滚动容器外的卡片跳过渲染排版，进一步降低首开成本。
//
// 注意：对话框中允许用户浏览和选择任意模板（展示用），但实际创建时始终提交
// 'classic'（经典）模板——确保所有新建简历统一使用经典布局，用户在编辑器里
// 可以后续切换。
//
// 状态重置：与 GenerateResumeDialog 相同——open 为 false 时不挂载内部组件，
// 每次打开都是全新表单，无需 effect 清理（lint: react-hooks/set-state-in-effect）。

import { useLayoutEffect, useRef, useState } from 'react';
import type { ChangeEvent, DragEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check, FileUp, Loader2 } from 'lucide-react';
import { resumeApi } from '../../api/resume';
import { cn } from '../../lib/utils';
import { TEMPLATE_ACCENTS, TEMPLATE_GROUPS, TEMPLATE_LABELS } from '../../lib/constants';
import { TemplateThumbnail } from '../preview/template-thumbnail';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

type CreateTab = 'template' | 'upload';

interface CreateResumeDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 创建成功但响应里没拿到 id 时的兜底（跳转不可用时刷新列表）。 */
  onCreated?: () => void;
}

export function CreateResumeDialog({ open, onOpenChange, onCreated }: CreateResumeDialogProps) {
  if (!open) return null;
  return <CreateResumeDialogContent onOpenChange={onOpenChange} onCreated={onCreated} />;
}

/* ---------------------------------------------------------------------------
 * 模板卡片：缩略图 + 名称 + 选中态。
 * TemplateThumbnail 需要固定像素宽，这里用 ResizeObserver 实测卡片内容宽，
 * 让缩略图随栅格列宽自适应（对话框宽度变化、断点变化都不用手调）。
 * ------------------------------------------------------------------------ */
function TemplateCard({
  value,
  selected,
  onSelect,
}: {
  value: string;
  selected: boolean;
  onSelect: (value: string) => void;
}) {
  const boxRef = useRef<HTMLDivElement>(null);
  const [boxWidth, setBoxWidth] = useState(0);

  useLayoutEffect(() => {
    const el = boxRef.current;
    if (!el) return;
    const measure = () => setBoxWidth(el.clientWidth);
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const accent = TEMPLATE_ACCENTS[value];

  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={() => onSelect(value)}
      className={cn(
        'group relative flex flex-col gap-2 rounded-lg border-2 p-2 text-left transition-colors',
        selected
          ? 'border-brand bg-brand/5'
          : 'border-border hover:border-brand/40 hover:bg-muted/40',
      )}
      // 滚动容器外的卡片跳过排版渲染（50 份预览同开时的主要优化）。
      style={{ contentVisibility: 'auto', containIntrinsicSize: 'auto 260px' }}
    >
      <div ref={boxRef} className="w-full">
        {boxWidth > 0 && (
          <TemplateThumbnail
            template={value}
            width={boxWidth}
            className={cn(
              'transition-shadow group-hover:shadow-md',
              selected && 'ring-2 ring-brand/60',
            )}
          />
        )}
      </div>
      <span
        className={cn(
          'flex items-center justify-center gap-1.5 text-xs font-medium',
          selected ? 'text-brand' : 'text-foreground',
        )}
      >
        {accent && (
          <span
            className="inline-block size-2 shrink-0 rounded-full"
            style={{ backgroundColor: accent }}
          />
        )}
        {TEMPLATE_LABELS[value] ?? value}
      </span>
      {selected && (
        <span className="absolute -right-1.5 -top-1.5 flex size-5 items-center justify-center rounded-full bg-brand text-brand-foreground shadow">
          <Check className="size-3" />
        </span>
      )}
    </button>
  );
}

/* ---------------------------------------------------------------------------
 * 模板画廊：按 TEMPLATE_GROUPS 分组展示全部 6 个模板。
 * ------------------------------------------------------------------------ */
function TemplateGallery({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="space-y-5">
      {TEMPLATE_GROUPS.map((group) => (
        <section key={group.id}>
          <h4 className="mb-2 text-sm font-medium text-muted-foreground">{group.label}</h4>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {group.items.map((t) => (
              <TemplateCard
                key={t}
                value={t}
                selected={value === t}
                onSelect={onChange}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

function CreateResumeDialogContent({
  onOpenChange,
  onCreated,
}: Omit<CreateResumeDialogProps, 'open'>) {
  const navigate = useNavigate();

  const [tab, setTab] = useState<CreateTab>('template');
  const [title, setTitle] = useState('');
  const [template, setTemplate] = useState('classic');
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const close = () => {
    if (submitting) return;
    onOpenChange(false);
  };

  const pickPdf = (file: File | undefined | null) => {
    if (!file) return;
    if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
      setError('请上传 PDF 格式文件');
      return;
    }
    setError('');
    setUploadFile(file);
  };

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    pickPdf(e.target.files?.[0]);
  };

  const handleDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    pickPdf(e.dataTransfer.files?.[0]);
  };

  // 创建成功后：有 id 直接进编辑页；拿不到 id 则关弹窗并兜底刷新列表。
  const finish = (id?: string | number) => {
    if (id) {
      onOpenChange(false);
      navigate(`/resumes/${id}`);
    } else {
      onOpenChange(false);
      onCreated?.();
    }
  };

  const extractMessage = (err: unknown, fallback: string) =>
    (err as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback;

  const handleCreateFromTemplate = async () => {
    setSubmitting(true);
    setError('');
    try {
      const res = await resumeApi.create({ title: title || undefined, template });
      finish(res.data.data?.id);
    } catch (err) {
      setError(extractMessage(err, '创建失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleUpload = async () => {
    if (!uploadFile) return;
    setSubmitting(true);
    setError('');
    try {
      const res = await resumeApi.upload(uploadFile, template);
      finish(res.data.data?.id);
    } catch (err) {
      setError(extractMessage(err, '上传失败'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open onOpenChange={(o) => { if (!o) close(); }}>
      <DialogContent className="gap-0 overflow-hidden p-0 sm:max-w-4xl">
        <DialogHeader className="px-6 pb-0 pt-6">
          <DialogTitle>新建简历</DialogTitle>
          <DialogDescription>选择一种方式创建你的简历，模板效果见下方缩略图</DialogDescription>
        </DialogHeader>

        <Tabs
          value={tab}
          onValueChange={(v) => {
            setTab(v as CreateTab);
            setError('');
          }}
        >
          <div className="px-6 pt-4">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="template">从模板创建</TabsTrigger>
              <TabsTrigger value="upload">从文件上传</TabsTrigger>
            </TabsList>
          </div>

          <div className="max-h-[62vh] overflow-y-auto px-6 py-4">
            <TabsContent value="template" className="mt-0 space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="create-resume-title">简历标题（可选）</Label>
                <Input
                  id="create-resume-title"
                  type="text"
                  placeholder="如：我的简历"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <p className="text-sm font-medium text-muted-foreground">选择模板</p>
                <TemplateGallery value={template} onChange={setTemplate} />
              </div>
            </TabsContent>

            <TabsContent value="upload" className="mt-0 space-y-4">
              <div
                className={cn(
                  'flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-8 text-center transition-colors',
                  dragging ? 'border-brand bg-brand/5' : 'border-border',
                  uploadFile && 'border-solid',
                )}
                onClick={() => fileInputRef.current?.click()}
                onDrop={handleDrop}
                onDragOver={(e) => {
                  e.preventDefault();
                  setDragging(true);
                }}
                onDragLeave={(e) => {
                  e.preventDefault();
                  setDragging(false);
                }}
              >
                {uploadFile ? (
                  <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                    <FileUp className="size-5 text-brand" />
                    <span className="font-medium">{uploadFile.name}</span>
                    <span className="text-xs text-muted-foreground">
                      {(uploadFile.size / 1024).toFixed(0)} KB
                    </span>
                    <button
                      type="button"
                      className="ml-1 flex size-6 items-center justify-center rounded-full bg-muted text-lg leading-none"
                      onClick={(e) => {
                        e.stopPropagation();
                        setUploadFile(null);
                        setDragging(false);
                        if (fileInputRef.current) fileInputRef.current.value = '';
                      }}
                      aria-label="移除文件"
                    >
                      ×
                    </button>
                  </div>
                ) : (
                  <>
                    <FileUp className="mb-2 size-8 text-muted-foreground" />
                    <div className="font-medium">点击或拖拽 PDF 到此处</div>
                    <div className="mt-1 text-xs text-muted-foreground">支持 PDF 格式，最大 10MB</div>
                  </>
                )}
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="application/pdf,.pdf"
                  className="hidden"
                  onChange={handleFileChange}
                />
              </div>

              <div className="space-y-2">
                <p className="text-sm font-medium text-muted-foreground">
                  选择模板<span className="ml-1 font-normal">（解析后的内容将套用该模板）</span>
                </p>
                <TemplateGallery value={template} onChange={setTemplate} />
              </div>
            </TabsContent>
          </div>
        </Tabs>

        {error && <p className="px-6 pb-1 text-sm text-destructive">{error}</p>}

        <div className="flex justify-end gap-2 border-t border-border px-6 py-4">
          <Button type="button" variant="outline" onClick={close} disabled={submitting}>
            取消
          </Button>
          {tab === 'template' ? (
            <Button
              type="button"
              onClick={handleCreateFromTemplate}
              disabled={submitting}
              className="bg-brand text-brand-foreground hover:bg-brand/90"
            >
              {submitting && <Loader2 className="mr-1.5 size-4 animate-spin" />}
              {submitting ? '创建中...' : '创建'}
            </Button>
          ) : (
            <Button
              type="button"
              onClick={handleUpload}
              disabled={!uploadFile || submitting}
              className="bg-brand text-brand-foreground hover:bg-brand/90"
            >
              {submitting && <Loader2 className="mr-1.5 size-4 animate-spin" />}
              {submitting ? '解析中...' : '上传并解析'}
            </Button>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
