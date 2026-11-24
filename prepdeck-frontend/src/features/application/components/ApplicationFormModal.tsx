import * as React from 'react';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
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
import { ApplicationDetail } from './ApplicationDetail';
import { CoverLetterPanel } from './CoverLetterPanel';
import { ChannelAutocomplete } from './ChannelAutocomplete';
import { SearchableSelect } from './SearchableSelect';
import { DateTimePicker } from './DateTimePicker';
import { DEFAULT_CHANNELS, STATUS_LABELS, STATUS_OPTIONS } from '../types';
import type { JobApplication, JobApplicationPayload, ResumeOption } from '../types';
import { toISO } from '../utils';

export type ModalMode = 'create' | 'edit' | 'view';

interface ApplicationFormModalProps {
  mode: ModalMode | null;
  application: JobApplication | null;
  resumeOptions: ResumeOption[];
  onSubmit: (payload: JobApplicationPayload) => Promise<void>;
  onClose: () => void;
}

const TITLES: Record<ModalMode, string> = {
  create: '新增投递记录',
  edit: '编辑投递记录',
  view: '投递详情',
};

function Field({
  label,
  required,
  error,
  children,
}: {
  label: string;
  required?: boolean;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <Label className="flex items-center gap-1">
        {label}
        {required && <span className="text-destructive">*</span>}
      </Label>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}

interface FormInitial {
  company?: string;
  position?: string;
  status?: string;
  channel?: string | null;
  resumeId?: string | null;
  appliedAt?: Date | null;
  notes?: string | null;
}

function ApplicationForm({
  initial,
  resumeOptions,
  submitting,
  onSubmit,
  onCancel,
}: {
  initial: FormInitial;
  resumeOptions: ResumeOption[];
  submitting: boolean;
  onSubmit: (payload: JobApplicationPayload) => Promise<void>;
  onCancel: () => void;
}) {
  const [company, setCompany] = React.useState(initial.company ?? '');
  const [position, setPosition] = React.useState(initial.position ?? '');
  const [status, setStatus] = React.useState(initial.status ?? 'applied');
  const [channel, setChannel] = React.useState<string | null>(initial.channel ?? null);
  const [resumeId, setResumeId] = React.useState<string | null>(initial.resumeId ?? null);
  const [appliedAt, setAppliedAt] = React.useState<Date | null>(initial.appliedAt ?? null);
  const [notes, setNotes] = React.useState(initial.notes ?? '');
  const [errors, setErrors] = React.useState<{ company?: string; position?: string }>({});

  const handleSave = () => {
    const next: { company?: string; position?: string } = {};
    if (!company.trim()) next.company = '请输入目标公司';
    if (!position.trim()) next.position = '请输入投递职位';
    setErrors(next);
    if (next.company || next.position) return;

    void onSubmit({
      company: company.trim(),
      position: position.trim(),
      status,
      channel: channel?.trim() || null,
      resumeId: resumeId || null,
      appliedAt: appliedAt ? toISO(appliedAt) : null,
      notes: notes.trim() || null,
    });
  };

  return (
    <div className="space-y-4">
      <Field label="目标公司" required error={errors.company}>
        <Input
          value={company}
          maxLength={255}
          placeholder="如：字节跳动"
          onChange={(e) => setCompany(e.target.value)}
        />
      </Field>
      <Field label="投递职位" required error={errors.position}>
        <Input
          value={position}
          maxLength={255}
          placeholder="如：前端工程师"
          onChange={(e) => setPosition(e.target.value)}
        />
      </Field>
      <Field label="投递状态" required>
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger>
            <SelectValue placeholder="选择状态" />
          </SelectTrigger>
          <SelectContent>
            {STATUS_OPTIONS.map((s) => (
              <SelectItem key={s} value={s}>
                {STATUS_LABELS[s]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>
      <Field label="投递渠道">
        <ChannelAutocomplete value={channel} onChange={setChannel} options={DEFAULT_CHANNELS} />
      </Field>
      <Field label="关联简历">
        <SearchableSelect
          value={resumeId}
          onChange={setResumeId}
          options={resumeOptions.map((r) => ({ value: r.id, label: r.title }))}
          placeholder="请选择简历"
          emptyText="暂无简历"
        />
      </Field>
      <Field label="投递时间">
        <DateTimePicker value={appliedAt} onChange={(d) => setAppliedAt(d ?? null)} />
      </Field>
      <Field label="备注">
        <Textarea
          value={notes}
          maxLength={2000}
          rows={3}
          placeholder="记录面试反馈、HR 信息等"
          onChange={(e) => setNotes(e.target.value)}
        />
      </Field>

      <div className="flex justify-end gap-2 pt-1">
        <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
          取消
        </Button>
        <Button type="button" onClick={handleSave} disabled={submitting}>
          {submitting ? '保存中...' : '保存'}
        </Button>
      </div>
    </div>
  );
}

export function ApplicationFormModal({
  mode,
  application,
  resumeOptions,
  onSubmit,
  onClose,
}: ApplicationFormModalProps) {
  const [submitting, setSubmitting] = React.useState(false);
  const open = mode !== null;
  const formKey = `${mode}:${application?.id ?? 'new'}`;

  const handleSubmit = async (payload: JobApplicationPayload) => {
    setSubmitting(true);
    try {
      await onSubmit(payload);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (!o) onClose();
      }}
    >
      <DialogContent className="w-full max-w-[95vw] overflow-y-auto sm:max-w-[620px] max-h-[85vh]">
        <DialogHeader>
          <DialogTitle>{mode ? TITLES[mode] : ''}</DialogTitle>
        </DialogHeader>

        {mode === 'view' && application ? (
          <>
            <ApplicationDetail application={application} />
            <CoverLetterPanel application={application} />
            <DialogFooter>
              <DialogClose asChild>
                <Button type="button">关闭</Button>
              </DialogClose>
            </DialogFooter>
          </>
        ) : (
          mode && (
            <ApplicationForm
              key={formKey}
              initial={
                application
                  ? {
                      company: application.company,
                      position: application.position,
                      status: application.status,
                      channel: application.channel,
                      resumeId: application.resumeId,
                      appliedAt: application.appliedAt ? new Date(application.appliedAt) : null,
                      notes: application.notes,
                    }
                  : {
                      // 新增：预填默认值
                      status: 'applied',
                      appliedAt: new Date(),
                    }
              }
              resumeOptions={resumeOptions}
              submitting={submitting}
              onSubmit={handleSubmit}
              onCancel={onClose}
            />
          )
        )}
      </DialogContent>
    </Dialog>
  );
}
