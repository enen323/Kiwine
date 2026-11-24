import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Plus, Send, AlertTriangle } from 'lucide-react';
import { toast } from 'sonner';
import { useApplications } from '@/features/application/hooks/useApplications';
import { ApplicationFilters } from '@/features/application/components/ApplicationFilters';
import { ApplicationTable } from '@/features/application/components/ApplicationTable';
import { ApplicationCardList } from '@/features/application/components/ApplicationCardList';
import { ApplicationFormModal, type ModalMode } from '@/features/application/components/ApplicationFormModal';
import { applicationApi } from '@/features/application/api/applicationApi';
import type { JobApplication, JobApplicationPayload } from '@/features/application/types';
import { Button } from '@/components/ui/button';

export default function ApplicationsPage() {
  const {
    items,
    total,
    page,
    pageSize,
    totalPages,
    loading,
    error,
    filters,
    setStatus,
    setKeyword,
    setPage,
    refetch,
    resumeOptions,
  } = useApplications();

  const [modalMode, setModalMode] = useState<ModalMode | null>(null);
  const [editing, setEditing] = useState<JobApplication | null>(null);

  const openCreate = () => {
    setEditing(null);
    setModalMode('create');
  };
  const openEdit = (a: JobApplication) => {
    setEditing(a);
    setModalMode('edit');
  };
  const openView = (a: JobApplication) => {
    setEditing(a);
    setModalMode('view');
  };
  // 关闭时只清 mode，保留 editing 直到关闭动画结束，避免内容闪烁
  const closeModal = () => setModalMode(null);

  const handleSubmit = async (payload: JobApplicationPayload) => {
    try {
      if (modalMode === 'edit' && editing) {
        await applicationApi.update(editing.id, payload);
        toast.success('投递记录已更新');
      } else {
        await applicationApi.create(payload);
        toast.success('投递记录已创建');
      }
      setModalMode(null);
      refetch();
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : '保存失败';
      toast.error(msg);
      throw err; // 让弹窗保持打开并解除 submitting
    }
  };

  const handleDelete = async (a: JobApplication) => {
    try {
      await applicationApi.remove(a.id);
      toast.success('投递记录已删除');
      // 删除后的分页处理：当前页只剩这一条且页码 > 1 时回到上一页，否则重加载当前页
      if (items.length === 1 && page > 1) {
        setPage(page - 1);
      } else {
        refetch();
      }
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : '删除失败';
      toast.error(msg);
    }
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      {/* 头部 */}
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight text-foreground">
            <Send className="size-5 text-brand" />
            简历投递台
          </h1>
          <p className="mt-1 text-muted-foreground">
            记录每一次简历投递，追踪求职进度，让面试机会尽在掌握。
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" asChild>
            <Link to="/">
              <ArrowLeft className="mr-1 size-4" />
              返回工作台
            </Link>
          </Button>
          <Button onClick={openCreate}>
            <Plus className="mr-1 size-4" />
            新增投递
          </Button>
        </div>
      </div>

      {/* 筛选区 */}
      <div className="mb-4">
        <ApplicationFilters
          keyword={filters.keyword}
          status={filters.status}
          onKeywordSubmit={setKeyword}
          onStatusChange={setStatus}
        />
      </div>

      {/* 错误横幅 */}
      {error && (
        <div className="mb-4 flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertTriangle className="size-4 shrink-0" />
          <span>{error}</span>
          <Button variant="ghost" size="sm" onClick={refetch} className="ml-auto shrink-0">
            重试
          </Button>
        </div>
      )}

      {/* 桌面端表格（≥768px） */}
      <div className="hidden md:block">
        <ApplicationTable
          items={items}
          loading={loading}
          onView={openView}
          onEdit={openEdit}
          onDelete={handleDelete}
        />
      </div>

      {/* 移动端卡片（<768px） */}
      <div className="md:hidden">
        <ApplicationCardList
          items={items}
          loading={loading}
          onView={openView}
          onEdit={openEdit}
          onDelete={handleDelete}
        />
      </div>

      {/* 分页（独立在表格外，右对齐） */}
      {!loading && !error && total > pageSize && (
        <div className="mt-6 flex items-center justify-end gap-3">
          <Button variant="ghost" size="sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>
            上一页
          </Button>
          <span className="text-sm text-muted-foreground">
            {page} / {totalPages}
          </span>
          <Button variant="ghost" size="sm" disabled={page >= totalPages} onClick={() => setPage(page + 1)}>
            下一页
          </Button>
        </div>
      )}

      <ApplicationFormModal
        mode={modalMode}
        application={editing}
        resumeOptions={resumeOptions}
        onSubmit={handleSubmit}
        onClose={closeModal}
      />
    </div>
  );
}
