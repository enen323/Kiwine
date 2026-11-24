import * as React from 'react';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from '@/components/ui/alert-dialog';
import type { JobApplication } from '../types';

interface ApplicationRowActionsProps {
  application: JobApplication;
  onView: (a: JobApplication) => void;
  onEdit: (a: JobApplication) => void;
  onDelete: (a: JobApplication) => void;
  align?: 'start' | 'end';
}

/** 表格列 / 移动卡片共用的「查看 / 编辑 / 删除」三按钮，删除带二次确认。 */
export function ApplicationRowActions({
  application,
  onView,
  onEdit,
  onDelete,
  align = 'end',
}: ApplicationRowActionsProps) {
  const [confirmOpen, setConfirmOpen] = React.useState(false);

  return (
    <div className={`flex items-center gap-3 ${align === 'end' ? 'justify-end' : 'justify-start'}`}>
      <button
        type="button"
        onClick={() => onView(application)}
        className="text-sm font-medium text-brand hover:underline"
      >
        查看
      </button>
      <button
        type="button"
        onClick={() => onEdit(application)}
        className="text-sm font-medium text-brand hover:underline"
      >
        编辑
      </button>
      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogTrigger asChild>
          <button type="button" className="text-sm font-medium text-destructive hover:underline">
            删除
          </button>
        </AlertDialogTrigger>
        <AlertDialogContent size="sm">
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              删除后该投递记录将不可恢复，是否继续？
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={() => {
                setConfirmOpen(false);
                onDelete(application);
              }}
            >
              确定
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
