"use client";

import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  ArrowLeft,
  Heart,
  Eye,
  MessageSquare,
  CalendarDays,
  CalendarCheck,
  Copy,
  Check,
  Loader2,
  AlertTriangle,
  Sparkles,
} from "lucide-react";
import { toast } from "sonner";
import { questionApi, normalizeQuestionError } from "@/api/question";
import { TypeBadge, DifficultyBadge, MistakeBadge } from "@/components/questions/QuestionBadges";
import type { QuestionDetailVO } from "@/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString("zh-CN");
  } catch {
    return iso;
  }
}

export default function QuestionDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [q, setQ] = useState<QuestionDetailVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [favoriting, setFavoriting] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  // 今日每日一题状态：用于判断是否展示「完成今日一题」按钮
  const [todayDailyId, setTodayDailyId] = useState<string | number | null>(null);
  const [dailyCompleted, setDailyCompleted] = useState(false);
  const [completingDaily, setCompletingDaily] = useState(false);

  // 拉取题目详情（+1 浏览、标记每日一题完成）。
  // 所有 setState 置于 .then/.catch/.finally 或微任务中，避免 effect 体内同步 setState。
  useEffect(() => {
    if (!id) return;
    let active = true;
    Promise.resolve().then(() => {
      if (active) {
        setLoading(true);
        setError("");
      }
    });

    questionApi
      .detail(id)
      .then((res) => {
        if (!active) return;
        const body = res.data;
        if (body.code !== 0) throw new Error(normalizeQuestionError(body.code, body.message));
        setQ(body.data);
        // 记录做题行为（§3.2，幂等）。失败静默，不影响详情展示。
        questionApi.practice(id).catch(() => {});
      })
      .catch((err) => {
        if (!active) return;
        const msg = err instanceof Error && err.message ? err.message : "加载失败，请稍后重试";
        setError(msg);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [id, reloadKey]);

  // 拉取每日一题，判断当前题目是否为今日一题（用于展示完成按钮）。
  useEffect(() => {
    if (!q) return;
    questionApi
      .daily()
      .then((res) => {
        if (res.data.code === 0 && res.data.data) {
          setTodayDailyId(res.data.data.question.id);
          setDailyCompleted(res.data.data.completed);
        }
      })
      .catch(() => {
        /* 失败不阻断 */
      });
  }, [q?.id]);

  const isTodayDaily =
    q != null && todayDailyId != null && String(todayDailyId) === String(q.id);

  const handleCompleteDaily = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (completingDaily || dailyCompleted) return;
    setCompletingDaily(true);
    try {
      const res = await questionApi.completeDaily();
      if (res.data.code !== 0) throw new Error(normalizeQuestionError(res.data.code, res.data.message));
      setDailyCompleted(true);
      toast.success("今日一题已完成");
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : "操作失败，请稍后重试";
      toast.error(msg);
    } finally {
      setCompletingDaily(false);
    }
  };

  const handleFavorite = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!q || favoriting) return;
    const next = !q.isFavorited;
    // 乐观更新
    setQ({ ...q, isFavorited: next, favorites: q.favorites + (next ? 1 : -1) });
    setFavoriting(true);
    try {
      const res = next
        ? await questionApi.addFavorite(q.id)
        : await questionApi.removeFavorite(q.id);
      if (res.data.code !== 0 && !( !next && res.data.code === 80002))
        throw new Error(normalizeQuestionError(res.data.code, res.data.message));
      toast.success(next ? "已加入收藏" : "已取消收藏");
    } catch (err) {
      setQ({ ...q, isFavorited: !next, favorites: q.favorites + (next ? -1 : 1) });
      const msg = err instanceof Error && err.message ? err.message : "操作失败，请稍后重试";
      toast.error(msg);
    } finally {
      setFavoriting(false);
    }
  };

  // ===== 首屏骨架 =====
  if (loading) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-8">
        <Skeleton className="h-8 w-24" />
        <Skeleton className="mt-6 h-6 w-3/4" />
        <Skeleton className="mt-3 h-4 w-1/2" />
        <Skeleton className="mt-8 h-40 rounded-2xl" />
        <Skeleton className="mt-6 h-64 rounded-2xl" />
      </div>
    );
  }

  // ===== 错误态 =====
  if (error && !q) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-8">
        <Button variant="ghost" onClick={() => navigate(-1)} className="mb-6 -ml-2">
          <ArrowLeft className="mr-2 size-4" />
          返回
        </Button>
        <div className="flex flex-col items-center gap-4 rounded-2xl border border-destructive/30 bg-destructive/5 py-16 text-center">
          <AlertTriangle className="size-8 text-destructive" />
          <p className="text-foreground">{error}</p>
          <Button variant="outline" onClick={() => setReloadKey((k) => k + 1)}>
            <Loader2 className="mr-2 size-4" />
            重试
          </Button>
        </div>
      </div>
    );
  }

  if (!q) return null;

  const hasCode = !!q.answer.code;

  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <Button variant="ghost" onClick={() => navigate(-1)} className="mb-4 -ml-2">
        <ArrowLeft className="mr-2 size-4" />
        返回
      </Button>

      {/* 头部：标题 + 徽标 + 统计 */}
      <div className="mb-6">
        <div className="flex flex-wrap items-center gap-2">
          <TypeBadge type={q.type} />
          <DifficultyBadge difficulty={q.difficulty} />
          {q.easyMistake && q.easyMistake !== "NORMAL" && <MistakeBadge mistake={q.easyMistake} />}
          {q.isFavorited && (
            <span className="rounded-full border border-rose-500/30 bg-rose-500/10 px-2 py-0.5 text-xs font-medium text-rose-600 dark:text-rose-400">
              已收藏
            </span>
          )}
        </div>

        <h1 className="mt-3 text-2xl font-semibold tracking-tight text-foreground">{q.title}</h1>

        <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
          <span className="flex items-center gap-1">
            <Eye className="size-4" />
            {q.views} 浏览
          </span>
          <span className="flex items-center gap-1">
            <Heart className="size-4" />
            {q.favorites} 收藏
          </span>
          <span className="flex items-center gap-1">
            <MessageSquare className="size-4" />
            {q.interviewCount} 次面试
          </span>
          <span className="flex items-center gap-1">
            <CalendarDays className="size-4" />
            {formatDate(q.createTime)}
          </span>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-3">
          <Button onClick={handleFavorite} variant={q.isFavorited ? "outline" : "default"} disabled={favoriting}>
            {favoriting ? (
              <Loader2 className="mr-2 size-4 animate-spin" />
            ) : (
              <Heart className={cn("mr-2 size-4", q.isFavorited && "fill-rose-500 text-rose-500")} />
            )}
            {q.isFavorited ? "取消收藏" : "收藏"}
          </Button>
          {isTodayDaily && !dailyCompleted && (
            <Button onClick={handleCompleteDaily} variant="outline" disabled={completingDaily}>
              {completingDaily ? (
                <Loader2 className="mr-2 size-4 animate-spin" />
              ) : (
                <CalendarCheck className="mr-2 size-4" />
              )}
              完成今日一题
            </Button>
          )}
          {isTodayDaily && dailyCompleted && (
            <span className="inline-flex items-center gap-1 rounded-full border border-brand/30 bg-brand-muted px-3 py-1.5 text-sm font-medium text-brand">
              <CalendarCheck className="size-4" />
              今日一题已完成
            </span>
          )}
          {q.tags.length > 0 && (
            <div className="flex flex-wrap gap-1">
              {q.tags.map((t) => (
                <span key={t.id} className="rounded-md bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                  {t.name}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 题目描述 */}
      <Card className="mb-6">
        <CardHeader>
          <CardTitle>题目描述</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="whitespace-pre-wrap leading-relaxed text-foreground/90">{q.content}</p>
        </CardContent>
      </Card>

      {/* 参考答案 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Sparkles className="size-4 text-brand" />
            参考答案
          </CardTitle>
          <CardDescription>思路概要、详细解析与示例代码，助你彻底吃透本题</CardDescription>
        </CardHeader>
        <CardContent>
          <Tabs defaultValue="summary">
            <TabsList>
              <TabsTrigger value="summary">思路概要</TabsTrigger>
              <TabsTrigger value="analysis">详细解析</TabsTrigger>
              {hasCode && <TabsTrigger value="code">示例代码</TabsTrigger>}
            </TabsList>
            <TabsContent value="summary" className="mt-4">
              <p className="whitespace-pre-wrap leading-relaxed text-foreground/90">{q.answer.summary}</p>
            </TabsContent>
            <TabsContent value="analysis" className="mt-4">
              <p className="whitespace-pre-wrap leading-relaxed text-foreground/90">{q.answer.analysis}</p>
            </TabsContent>
            {hasCode && (
              <TabsContent value="code" className="mt-4">
                <CodeBlock code={q.answer.code!} />
              </TabsContent>
            )}
          </Tabs>
        </CardContent>
      </Card>
    </div>
  );
}

function CodeBlock({ code }: { code: string }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      toast.success("已复制到剪贴板");
      setTimeout(() => setCopied(false), 1500);
    } catch {
      toast.error("复制失败，请手动选择文本");
    }
  };

  return (
    <div className="relative">
      <Button variant="ghost" size="sm" className="absolute right-2 top-2 z-10" onClick={copy}>
        {copied ? <Check className="mr-1 size-3.5" /> : <Copy className="mr-1 size-3.5" />}
        {copied ? "已复制" : "复制"}
      </Button>
      <pre className="max-h-[480px] overflow-auto rounded-lg border bg-muted/50 p-4 text-sm leading-relaxed">
        <code className="font-mono">{code}</code>
      </pre>
    </div>
  );
}
