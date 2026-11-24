import { StatusBadge } from './StatusBadge';
import { formatDateTime } from '../utils';
import type { JobApplication } from '../types';

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[88px_1fr] gap-3 border-b py-2.5 last:border-b-0">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-sm text-foreground">{children}</dd>
    </div>
  );
}

const DASH = <span className="text-muted-foreground/60">—</span>;

/** 投递详情（只读描述列表）。空值统一渲染灰色「—」；备注保留换行。 */
export function ApplicationDetail({ application }: { application: JobApplication }) {
  return (
    <dl className="rounded-lg border px-4">
      <Row label="公司">{application.company || DASH}</Row>
      <Row label="职位">{application.position || DASH}</Row>
      <Row label="状态">
        <StatusBadge status={application.status} />
      </Row>
      <Row label="渠道">{application.channel || DASH}</Row>
      <Row label="关联简历">{application.resumeTitle || DASH}</Row>
      <Row label="投递时间">{formatDateTime(application.appliedAt) || DASH}</Row>
      <Row label="备注">
        {application.notes ? (
          <span className="block whitespace-pre-wrap break-words">{application.notes}</span>
        ) : (
          DASH
        )}
      </Row>
    </dl>
  );
}
