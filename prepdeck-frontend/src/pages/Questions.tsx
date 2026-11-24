"use client";

import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Search,
  Heart,
  Eye,
  MessageSquare,
  Bookmark,
  CalendarDays,
  Sparkles,
  Loader2,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { toast } from "sonner";
import { questionApi, normalizeQuestionError } from "@/api/question";
import { Reveal } from "@/components/Reveal";
import { TypeBadge, DifficultyBadge, MistakeBadge } from "@/components/questions/QuestionBadges";
import type {
  QuestionDetailVO,
  QuestionTag,
  DailyQuestion,
  QuestionType,
  QuestionDifficulty,
  QuestionListParams,
  QuestionRecommendation,
} from "@/types";
import {
  QUESTION_TYPE_OPTIONS,
  QUESTION_DIFFICULTY_OPTIONS,
  QUESTION_SORT_OPTIONS,
  recommendStrategyLabel,
} from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 12;

const TYPE_OPTIONS = QUESTION_TYPE_OPTIONS;
const DIFFICULTY_OPTIONS = QUESTION_DIFFICULTY_OPTIONS;

type Tab = "all" | "favorites";

export default function Questions() {
  const navigate = useNavigate();

  const [tab, setTab] = useState<Tab>("all");
  const [keyword, setKeyword] = useState("");
  const [debounced, setDebounced] = useState("");

  const [typeFilter, setTypeFilter] = useState<QuestionType | "">("");
  const [difficultyFilter, setDifficultyFilter] = useState<QuestionDifficulty | "">("");
  const [sortBy, setSortBy] = useState<NonNullable<QuestionListParams["sortBy"]>>("heat");
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([]);

  const [list, setList] = useState<QuestionDetailVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);

  const [tags, setTags] = useState<QuestionTag[]>([]);
  const [daily, setDaily] = useState<DailyQuestion | null>(null);
  const [favoriting, setFavoriting] = useState<Set<string | number>>(new Set());

  const [recommendation, setRecommendation] = useState<QuestionRecommendation | null>(null);

  // 关键词防抖：停止输入 350ms 后更新搜索词并回到第一页（均在 setTimeout 回调中执行）
  useEffect(() => {
    const t = setTimeout(() => {
      setDebounced(keyword.trim());
      setPage(1);
    }, 350);
    return () => clearTimeout(t);
  }, [keyword]);

  // 拉取标签（筛选用）
  useEffect(() => {
    questionApi
      .tags()
      .then((res) => {
        if (res.data.code === 0 && Array.isArray(res.data.data)) setTags(res.data.data);
      })
      .catch(() => {
        /* 标签拉取失败不阻断题目加载 */
      });
  }, []);

  // 拉取每日一题
  useEffect(() => {
    questionApi
      .daily()
      .then((res) => {
        if (res.data.code === 0 && res.data.data) setDaily(res.data.data);
      })
      .catch(() => {
        /* 每日一题拉取失败不阻断 */
      });
  }, []);

  // 拉取智能推荐（§3.1）。后端 80006 为「数据不足、已回退热门」的降级成功，
  // 仍返回有效 questions，故 code === 0 或 80006 都按成功处理。
  useEffect(() => {
    questionApi
      .recommendations(10)
      .then((res) => {
        const body = res.data;
        if (body.code === 0 || body.code === 80006) setRecommendation(body.data);
      })
      .catch(() => {
        /* 推荐拉取失败不阻断题目加载 */
      });
  }, []);

  // 拉取题目列表（全部 / 搜索 / 收藏）。
  // 所有 setState 置于 .then/.catch/.finally 或微任务中，避免 effect 体内同步 setState。
  useEffect(() => {
    let active = true;
    // 微任务中置 loading，既给出加载态又不触发「effect 内同步 setState」告警
    Promise.resolve().then(() => {
      if (active) {
        setLoading(true);
        setError("");
      }
    });

    const p =
      tab === "favorites"
        ? questionApi.favorites(page, PAGE_SIZE)
        : debounced
          ? questionApi.search(debounced, page, PAGE_SIZE)
          : questionApi.list({
              page,
              pageSize: PAGE_SIZE,
              sortBy,
              order: "desc",
              type: typeFilter || undefined,
              difficulty: difficultyFilter || undefined,
              tagIds: selectedTagIds.length ? selectedTagIds : undefined,
            });

    p.then((res) => {
        if (!active) return;
        const body = res.data;
        if (body.code !== 0) throw new Error(normalizeQuestionError(body.code, body.message));
        // 后端返回 Result<PageResult<T>>：body.data 为内嵌 PageResult，数组在 body.data.data
        setList(body.data?.data ?? []);
        setTotal(body.data?.total ?? 0);
      })
      .catch((err) => {
        if (!active) return;
        const msg = err instanceof Error && err.message ? err.message : "加载失败，请稍后重试";
        setError(msg);
        toast.error(msg);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [tab, debounced, typeFilter, difficultyFilter, sortBy, selectedTagIds, page, reloadKey]);

  const toggleFavorite = async (q: QuestionDetailVO, e?: React.MouseEvent) => {
    e?.stopPropagation();
    if (favoriting.has(q.id)) return;
    const next = !q.isFavorited;
    // 乐观更新
    setList((prev) =>
      prev.map((it) =>
        it.id === q.id
          ? { ...it, isFavorited: next, favorites: it.favorites + (next ? 1 : -1) }
          : it,
      ),
    );
    setFavoriting((s) => new Set(s).add(q.id));
    try {
      if (next) {
        const res = await questionApi.addFavorite(q.id);
        if (res.data.code !== 0) throw new Error(normalizeQuestionError(res.data.code, res.data.message));
      } else {
        const res = await questionApi.removeFavorite(q.id);
        // 80002 视作已成功（原本就未收藏）
        if (res.data.code !== 0 && res.data.code !== 80002)
          throw new Error(normalizeQuestionError(res.data.code, res.data.message));
        // 收藏列表中取消收藏 → 直接移除该项
        if (tab === "favorites") {
          setList((prev) => prev.filter((it) => it.id !== q.id));
          setTotal((t) => Math.max(0, t - 1));
        }
      }
      toast.success(next ? "已加入收藏" : "已取消收藏");
    } catch (err) {
      // 回滚
      setList((prev) =>
        prev.map((it) =>
          it.id === q.id
            ? { ...it, isFavorited: !next, favorites: it.favorites + (next ? -1 : 1) }
            : it,
        ),
      );
      const msg = err instanceof Error && err.message ? err.message : "操作失败，请稍后重试";
      toast.error(msg);
    } finally {
      setFavoriting((s) => {
        const n = new Set(s);
        n.delete(q.id);
        return n;
      });
    }
  };

  const openDetail = (id: string | number) => navigate(`/questions/${id}`);

  const totalPages = Math.ceil(total / PAGE_SIZE);

  const clearFilters = () => {
    setTypeFilter("");
    setDifficultyFilter("");
    setSelectedTagIds([]);
    setKeyword("");
    setDebounced("");
    setPage(1);
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <div className="mb-6">
        <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight text-foreground">
          <Bookmark className="size-5 text-brand" />
          题库
        </h1>
        <p className="mt-1 text-muted-foreground">
          精选面试题库，按热度、难度与题型检索，每日一题持续精进
        </p>
      </div>

      {/* 每日一题 */}
      {tab === "all" && daily && (
        <Reveal className="mb-6">
          <div className="relative overflow-hidden rounded-2xl border bg-gradient-to-br from-brand-muted to-background p-5 sm:p-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <div className="flex items-center gap-2 text-brand">
                  <CalendarDays className="size-4" />
                  <span className="text-sm font-semibold">每日一题</span>
                  {daily.completed && (
                    <span className="rounded-full border border-brand/30 bg-brand-muted px-2 py-0.5 text-xs font-medium text-brand">
                      今日已完成
                    </span>
                  )}
                </div>
                <h2 className="mt-2 truncate text-lg font-semibold text-foreground">
                  {daily.question.title}
                </h2>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <TypeBadge type={daily.question.type} />
                  <DifficultyBadge difficulty={daily.question.difficulty} />
                </div>
              </div>
              <Button onClick={() => openDetail(daily.question.id)} className="shrink-0">
                <Sparkles className="mr-2 size-4" />
                开始练习
              </Button>
            </div>
          </div>
        </Reveal>
      )}

      {/* 为你推荐 */}
      {tab === "all" && recommendation && recommendation.questions.length > 0 && (
        <Reveal className="mb-6">
          <div className="rounded-2xl border bg-card p-5">
            <div className="mb-1 flex flex-wrap items-center gap-2">
              <span className="flex items-center gap-1.5 text-sm font-semibold text-foreground">
                <Sparkles className="size-4 text-brand" />
                为你推荐
              </span>
              <span className="rounded-full bg-brand-muted px-2 py-0.5 text-xs font-medium text-brand">
                {recommendStrategyLabel(recommendation.strategy)}
              </span>
            </div>
            <p className="mb-4 text-sm text-muted-foreground">{recommendation.message}</p>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {recommendation.questions.slice(0, 6).map((rq) => (
                <button
                  key={rq.id}
                  type="button"
                  onClick={() => openDetail(rq.id)}
                  className="group flex flex-col gap-2 rounded-xl border bg-background p-3 text-left transition-shadow hover:shadow-md"
                >
                  <h3 className="line-clamp-2 text-sm font-medium text-foreground">{rq.title}</h3>
                  <div className="flex flex-wrap items-center gap-1.5">
                    <TypeBadge type={rq.type} />
                    <DifficultyBadge difficulty={rq.difficulty} />
                  </div>
                  <div className="mt-auto flex items-center gap-3 pt-1 text-xs text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <MessageSquare className="size-3.5" />
                      {rq.interviewCount}
                    </span>
                    <span className="flex items-center gap-1">
                      <Eye className="size-3.5" />
                      {rq.views}
                    </span>
                  </div>
                </button>
              ))}
            </div>
          </div>
        </Reveal>
      )}

      {/* 标签页：全部 / 收藏 */}
      <div className="mb-4 inline-flex rounded-lg bg-muted p-1">
        {(["all", "favorites"] as const).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => {
              setTab(t);
              setPage(1);
            }}
            className={cn(
              "rounded-md px-4 py-1.5 text-sm font-medium transition-colors",
              tab === t
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground",
            )}
          >
            {t === "all" ? "全部题目" : "我的收藏"}
          </button>
        ))}
      </div>

      {/* 搜索 + 筛选（仅全部题目） */}
      {tab === "all" && (
        <div className="mb-6 space-y-3">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="搜索题目关键词，支持「Java AND 并发」「Java -Spring」语法"
              className="pl-9"
            />
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Select
              value={typeFilter || "__all__"}
              onValueChange={(v) => {
                setTypeFilter(v === "__all__" ? "" : (v as QuestionType));
                setPage(1);
              }}
            >
              <SelectTrigger className="w-[140px] rounded-md">
                <SelectValue placeholder="全部题型" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">全部题型</SelectItem>
                {TYPE_OPTIONS.map((o) => (
                  <SelectItem key={o.value} value={o.value}>
                    {o.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              value={difficultyFilter || "__all__"}
              onValueChange={(v) => {
                setDifficultyFilter(v === "__all__" ? "" : (v as QuestionDifficulty));
                setPage(1);
              }}
            >
              <SelectTrigger className="w-[140px] rounded-md">
                <SelectValue placeholder="全部难度" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">全部难度</SelectItem>
                {DIFFICULTY_OPTIONS.map((o) => (
                  <SelectItem key={o.value} value={o.value}>
                    {o.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              value={sortBy}
              onValueChange={(v) => {
                setSortBy(v as NonNullable<QuestionListParams["sortBy"]>);
                setPage(1);
              }}
            >
              <SelectTrigger className="w-[140px] rounded-md">
                <SelectValue placeholder="排序" />
              </SelectTrigger>
              <SelectContent>
                {QUESTION_SORT_OPTIONS.map((o) => (
                  <SelectItem key={o.value} value={o.value}>
                    {o.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {tags.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {tags.map((tag) => {
                const active = selectedTagIds.includes(tag.id);
                return (
                  <button
                    key={tag.id}
                    type="button"
                    onClick={() => {
                      setSelectedTagIds((ids) =>
                        active ? ids.filter((id) => id !== tag.id) : [...ids, tag.id],
                      );
                      setPage(1);
                    }}
                    className={cn(
                      "rounded-full border px-3 py-1 text-xs transition-colors",
                      active
                        ? "border-brand bg-brand-muted text-brand"
                        : "border-border text-muted-foreground hover:text-foreground",
                    )}
                  >
                    {tag.name}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* 加载骨架 */}
      {loading && list.length === 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: PAGE_SIZE }).map((_, i) => (
            <Skeleton key={i} className="h-40 rounded-xl" />
          ))}
        </div>
      )}

      {/* 错误态 */}
      {error && list.length === 0 && (
        <div className="flex flex-col items-center gap-4 rounded-2xl border border-destructive/30 bg-destructive/5 py-16 text-center">
          <AlertTriangle className="size-8 text-destructive" />
          <p className="text-foreground">{error}</p>
          <Button variant="outline" onClick={() => setReloadKey((k) => k + 1)}>
            <Loader2 className="mr-2 size-4" />
            重试
          </Button>
        </div>
      )}

      {/* 空态 */}
      {!loading && !error && list.length === 0 && (
        <div className="flex flex-col items-center gap-4 py-16 text-center">
          {tab === "favorites" ? (
            <>
              <Bookmark className="size-10 text-muted-foreground" />
              <p className="text-lg font-medium">还没有收藏任何题目</p>
              <p className="max-w-sm text-sm text-muted-foreground">
                在题库中遇到好题，点击心形图标即可收藏，方便日后复习。
              </p>
              <Button onClick={() => { setTab("all"); setPage(1); }}>去题库发现好题</Button>
            </>
          ) : debounced ? (
            <>
              <Search className="size-10 text-muted-foreground" />
              <p className="text-lg font-medium">没有找到相关题目</p>
              <p className="max-w-sm text-sm text-muted-foreground">
                没有与「{debounced}」匹配的题目，试试更换关键词或减少筛选条件。
              </p>
              <Button variant="outline" onClick={clearFilters}>
                清除筛选
              </Button>
            </>
          ) : (
            <>
              <Bookmark className="size-10 text-muted-foreground" />
              <p className="text-lg font-medium">暂无题目</p>
              <p className="max-w-sm text-sm text-muted-foreground">
                题库还是空的，敬请期待更多面试题上线。
              </p>
            </>
          )}
        </div>
      )}

      {/* 题目列表 */}
      {!loading && !error && list.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((q) => (
            <QuestionCard
              key={q.id}
              q={q}
              pending={favoriting.has(q.id)}
              onToggle={toggleFavorite}
              onOpen={openDetail}
            />
          ))}
        </div>
      )}

      {/* 分页 */}
      {!loading && !error && total > PAGE_SIZE && (
        <div className="mt-8 flex items-center justify-center gap-3">
          <Button variant="ghost" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
            <ChevronLeft className="mr-1 size-4" />
            上一页
          </Button>
          <span className="text-sm text-muted-foreground">
            {page} / {totalPages}
          </span>
          <Button
            variant="ghost"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            下一页
            <ChevronRight className="ml-1 size-4" />
          </Button>
        </div>
      )}
    </div>
  );
}

function QuestionCard({
  q,
  pending,
  onToggle,
  onOpen,
}: {
  q: QuestionDetailVO;
  pending: boolean;
  onToggle: (q: QuestionDetailVO, e?: React.MouseEvent) => void;
  onOpen: (id: string | number) => void;
}) {
  return (
    <Card
      className="group flex cursor-pointer flex-col transition-shadow hover:shadow-md"
      onClick={() => onOpen(q.id)}
    >
      <CardContent className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex items-start justify-between gap-2">
          <div className="flex flex-wrap items-center gap-1.5">
            <TypeBadge type={q.type} />
            <DifficultyBadge difficulty={q.difficulty} />
            {q.easyMistake && q.easyMistake !== "NORMAL" && <MistakeBadge mistake={q.easyMistake} />}
          </div>
          <button
            type="button"
            onClick={(e) => onToggle(q, e)}
            aria-label={q.isFavorited ? "取消收藏" : "收藏"}
            className={cn(
              "shrink-0 rounded-full p-1.5 transition-colors",
              q.isFavorited ? "text-rose-500" : "text-muted-foreground hover:text-foreground",
            )}
          >
            {pending ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Heart className={cn("size-4", q.isFavorited && "fill-rose-500")} />
            )}
          </button>
        </div>

        <h3 className="line-clamp-2 font-medium text-foreground">{q.title}</h3>

        {q.tags.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {q.tags.slice(0, 3).map((t) => (
              <span
                key={t.id}
                className="rounded-md bg-muted px-2 py-0.5 text-xs text-muted-foreground"
              >
                {t.name}
              </span>
            ))}
          </div>
        )}

        <div className="mt-auto flex items-center gap-4 pt-2 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <MessageSquare className="size-3.5" />
            {q.interviewCount}
          </span>
          <span className="flex items-center gap-1">
            <Eye className="size-3.5" />
            {q.views}
          </span>
          <span className="flex items-center gap-1">
            <Heart className="size-3.5" />
            {q.favorites}
          </span>
        </div>
      </CardContent>
    </Card>
  );
}
