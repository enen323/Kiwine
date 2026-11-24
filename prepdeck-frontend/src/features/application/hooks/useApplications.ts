// 投递记录列表的状态编排 hook
// 职责：列表数据、筛选、分页，并把 status/keyword/page 同步到 URL searchParams（刷新/分享保持状态）。
// 请求带 AbortController，避免快速切换筛选时的竞态；关联简历下拉仅在挂载时拉取一次。
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { applicationApi, fetchResumeOptions } from '../api/applicationApi';
import { normalizeApplicationError } from '../utils';
import type { JobApplication, JobApplicationPage, ResumeOption } from '../types';

const PAGE_SIZE = 10;

export function useApplications() {
  const [searchParams, setSearchParams] = useSearchParams();

  const status = searchParams.get('status') ?? '';
  const keyword = searchParams.get('keyword') ?? '';
  const page = Math.max(1, Number(searchParams.get('page') ?? '1') || 1);
  const pageSize = Math.max(1, Number(searchParams.get('pageSize') ?? String(PAGE_SIZE)) || PAGE_SIZE);

  const [items, setItems] = useState<JobApplication[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [resumeOptions, setResumeOptions] = useState<ResumeOption[]>([]);

  // 合并式更新 URL：传入 patch；status/keyword 变化会重置页码到 1
  const updateParams = useCallback(
    (patch: Record<string, string>, resetPage = false) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          for (const [k, v] of Object.entries(patch)) {
            if (v) next.set(k, v);
            else next.delete(k);
          }
          if (resetPage) next.delete('page');
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  const setStatus = useCallback((s: string) => updateParams({ status: s }, true), [updateParams]);
  const setKeyword = useCallback((k: string) => updateParams({ keyword: k }, true), [updateParams]);
  const setPage = useCallback((p: number) => updateParams({ page: String(p) }), [updateParams]);
  const refetch = useCallback(() => setReloadKey((k) => k + 1), []);

  // 列表请求：筛选 / 分页变化触发；带 AbortController 取消竞态
  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    // 微任务置 loading，避免 effect 体内同步 setState
    Promise.resolve().then(() => {
      if (active) {
        setLoading(true);
        setError('');
      }
    });

    applicationApi
      .list({ status: status || undefined, keyword: keyword || undefined, page, pageSize, signal: controller.signal })
      .then((res) => {
        if (!active) return;
        const body = res.data;
        if (body.code !== 0) throw new Error(normalizeApplicationError(body.code, body.message));
        const pr = (body.data ?? {}) as JobApplicationPage;
        setItems(pr.items ?? []);
        setTotal(pr.total ?? 0);
      })
      .catch((err: unknown) => {
        if (!active || controller.signal.aborted) return;
        const msg = err instanceof Error && err.message ? err.message : '加载投递记录失败';
        setError(msg);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [status, keyword, page, pageSize, reloadKey]);

  // 关联简历下拉：仅挂载时拉取一次
  useEffect(() => {
    let active = true;
    fetchResumeOptions()
      .then((opts) => {
        if (active) setResumeOptions(opts);
      })
      .catch(() => {
        /* 简历下拉失败不阻断主流程 */
      });
    return () => {
      active = false;
    };
  }, []);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return {
    items,
    total,
    page,
    pageSize,
    totalPages,
    loading,
    error,
    filters: { status, keyword },
    setStatus,
    setKeyword,
    setPage,
    refetch,
    resumeOptions,
  };
}
