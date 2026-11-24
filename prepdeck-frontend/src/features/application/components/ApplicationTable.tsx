import { StatusBadge } from './StatusBadge';
import { ApplicationRowActions } from './ApplicationRowActions';
import { formatDateTime } from '../utils';
import type { JobApplication } from '../types';

interface ApplicationTableProps {
  items: JobApplication[];
  loading: boolean;
  onView: (a: JobApplication) => void;
  onEdit: (a: JobApplication) => void;
  onDelete: (a: JobApplication) => void;
}

const DASH = <span className="text-muted-foreground/60">—</span>;

/** 桌面端表格（≥768px 显示）。空状态与加载态统一文案「暂无投递记录」/「加载中...」。 */
export function ApplicationTable({ items, loading, onView, onEdit, onDelete }: ApplicationTableProps) {
  return (
    <div className="overflow-x-auto rounded-lg border">
      <table className="w-full min-w-[900px] caption-bottom text-sm">
        <thead>
          <tr className="border-b bg-muted/40">
            <th className="h-11 w-[140px] px-4 text-left align-middle font-medium text-muted-foreground">公司</th>
            <th className="h-11 w-[160px] px-4 text-left align-middle font-medium text-muted-foreground">职位</th>
            <th className="h-11 w-[110px] px-4 text-left align-middle font-medium text-muted-foreground">状态</th>
            <th className="h-11 w-[120px] px-4 text-left align-middle font-medium text-muted-foreground">渠道</th>
            <th className="h-11 w-[160px] px-4 text-left align-middle font-medium text-muted-foreground">关联简历</th>
            <th className="h-11 w-[150px] px-4 text-left align-middle font-medium text-muted-foreground">投递时间</th>
            <th className="h-11 w-[200px] px-4 text-right align-middle font-medium text-muted-foreground">操作</th>
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td colSpan={7} className="h-40 text-center text-muted-foreground">
                加载中...
              </td>
            </tr>
          ) : items.length === 0 ? (
            <tr>
              <td colSpan={7} className="h-40 text-center text-muted-foreground">
                暂无投递记录
              </td>
            </tr>
          ) : (
            items.map((a) => (
              <tr key={a.id} className="group border-b transition-colors hover:bg-muted/40">
                <td className="max-w-[140px] truncate px-4 py-3 font-medium" title={a.company}>
                  {a.company}
                </td>
                <td className="max-w-[160px] truncate px-4 py-3" title={a.position}>
                  {a.position}
                </td>
                <td className="px-4 py-3">
                  <StatusBadge status={a.status} />
                </td>
                <td className="px-4 py-3 text-muted-foreground">{a.channel || DASH}</td>
                <td className="max-w-[160px] truncate px-4 py-3 text-muted-foreground" title={a.resumeTitle ?? ''}>
                  {a.resumeTitle || DASH}
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-muted-foreground">
                  {formatDateTime(a.appliedAt) || DASH}
                </td>
                <td className="sticky right-0 bg-background px-4 py-3 text-right group-hover:bg-muted/40">
                  <ApplicationRowActions
                    application={a}
                    onView={onView}
                    onEdit={onEdit}
                    onDelete={onDelete}
                  />
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
