"use client";

import { useState, useEffect, useCallback } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { motion, useReducedMotion } from "framer-motion";
import {
  TrendingUp,
  TrendingDown,
  BarChart3,
  Trophy,
  Clock,
  FileSearch,
  Lightbulb,
  RefreshCw,
  Loader2,
  Target,
  AlertTriangle,
} from "lucide-react";
import { toast } from "sonner";
import { reportApi, normalizeReportError } from "@/api/report";
import { userApi } from "@/api/user";
import { RadarChart } from "@/components/reports/RadarChart";
import { TrendChart } from "@/components/reports/TrendChart";
import { InterviewMultiSelect } from "@/components/reports/InterviewMultiSelect";
import type { InterviewReport, InterviewOption } from "@/types";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

function formatDuration(sec: number): string {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  if (h > 0) return `${h} 小时 ${m} 分`;
  return `${m} 分钟`;
}

/** 进入视口时淡入上移；尊重 prefers-reduced-motion */
function Reveal({
  children,
  className,
  delay = 0,
}: {
  children: React.ReactNode;
  className?: string;
  delay?: number;
}) {
  const reduce = useReducedMotion();
  if (reduce) return <div className={className}>{children}</div>;
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: 16 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-60px" }}
      transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1], delay }}
    >
      {children}
    </motion.div>
  );
}

type Mode = "all" | "selected";

export default function Report() {
  const navigate = useNavigate();
  const location = useLocation();
  // 从面试结束卡片「查看报告」跳转而来时携带本场面试 id，自动预选并生成报告
  const prefillInterviewId = (location.state as { prefillInterviewId?: string } | null)
    ?.prefillInterviewId;
  const [userId, setUserId] = useState<string | number | null>(null);

  const [available, setAvailable] = useState<InterviewOption[]>([]);
  const [availableLoading, setAvailableLoading] = useState(true);

  const [mode, setMode] = useState<Mode>("all");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const [report, setReport] = useState<InterviewReport | null>(null);
  const [loading, setLoading] = useState(true); // 首屏报告加载
  const [generating, setGenerating] = useState(false); // 点击"生成报告"
  const [error, setError] = useState("");
  const [isEmpty, setIsEmpty] = useState(false);

  // 解析当前用户身份（报告接口需要 X-User-Id）
  useEffect(() => {
    userApi
      .getMe()
      .then((res) => setUserId(res.data.data.id))
      .catch(() => {
        setError("登录状态已失效，请重新登录");
        setLoading(false);
      });
  }, []);

  const runGenerate = useCallback(
    async (uid: string | number, m: Mode, ids: string[]) => {
      setGenerating(true);
      setError("");
      setIsEmpty(false);
      try {
        const body =
          m === "selected" && ids.length > 0 ? { interviewIds: ids } : {};
        const res = await reportApi.generate(body, uid);
        const bodyRes = res.data;
        if (bodyRes.code !== 0) {
          if (bodyRes.code === 60000) {
            setIsEmpty(true);
            setReport(null);
            return;
          }
          throw new Error(normalizeReportError(bodyRes.code, bodyRes.message));
        }
        if (!bodyRes.data) {
          setIsEmpty(true);
          setReport(null);
          return;
        }
        setReport(bodyRes.data);
      } catch (err) {
        const msg =
          err instanceof Error && err.message
            ? err.message
            : "生成报告失败，请稍后重试";
        setError(msg);
        toast.error(msg);
      } finally {
        setGenerating(false);
      }
    },
    [],
  );

  // userId 就绪后：拉取可用面试列表，再生成报告。
  // 若从面试结束卡片携带 prefillInterviewId 跳转而来，且该面试在可选列表中，
  // 则自动切换到「selected」模式并仅生成该场报告；否则生成全量报告。
  // 所有 setState 均置于 .then/.finally 回调中，避免 effect 体内同步 setState。
  useEffect(() => {
    if (userId == null) return;
    reportApi
      .availableInterviews(userId)
      .then((res) => {
        const b = res.data;
        const avail = b.code === 0 && Array.isArray(b.data) ? b.data : [];
        if (b.code === 0 && Array.isArray(b.data)) setAvailable(b.data);

        // 预选本场面试：在可选列表中则切换到 selected 模式并生成该场报告
        if (prefillInterviewId != null) {
          const pid = String(prefillInterviewId);
          const found = avail.find((o) => String(o.id) === pid);
          if (found) {
            setMode("selected");
            setSelectedIds([pid]);
            runGenerate(userId, "selected", [pid]).finally(() => setLoading(false));
            return;
          }
        }
        // 默认：生成全量报告
        runGenerate(userId, "all", []).finally(() => setLoading(false));
      })
      .catch(() => {
        /* 可选列表拉取失败：降级生成全量报告 */
        runGenerate(userId, "all", []).finally(() => setLoading(false));
      })
      .finally(() => setAvailableLoading(false));
  }, [userId]);

  const handleGenerate = () => {
    if (userId == null) {
      setError("登录状态已失效，请重新登录");
      return;
    }
    runGenerate(userId, mode, selectedIds);
  };

  const handleRetry = () => {
    if (userId == null) {
      userApi
        .getMe()
        .then((res) => setUserId(res.data.data.id))
        .catch(() => setError("登录状态已失效，请重新登录"));
      return;
    }
    runGenerate(userId, mode, selectedIds);
  };

  // ===== 首屏加载骨架 =====
  if (loading) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-8">
        <Skeleton className="h-9 w-48" />
        <Skeleton className="mt-2 h-4 w-72" />
        <div className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
        <Skeleton className="mt-6 h-80 rounded-2xl" />
        <Skeleton className="mt-6 h-72 rounded-2xl" />
      </div>
    );
  }

  // ===== 错误态 =====
  if (error && !report) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-8">
        <div className="mb-8">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">
            面试成长报告
          </h1>
        </div>
        <div className="flex flex-col items-center gap-4 rounded-2xl border border-destructive/30 bg-destructive/5 py-16 text-center">
          <AlertTriangle className="size-8 text-destructive" />
          <p className="text-foreground">{error}</p>
          <Button onClick={handleRetry} variant="outline">
            <RefreshCw className="mr-2 size-4" />
            重试
          </Button>
        </div>
      </div>
    );
  }

  // ===== 空态（无已完成面试） =====
  if (isEmpty || !report) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-8">
        <div className="mb-8">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">
            面试成长报告
          </h1>
          <p className="mt-1 text-muted-foreground">
            聚合你的历史面试，生成五维能力画像与改进建议
          </p>
        </div>
        <div className="flex flex-col items-center gap-4 rounded-2xl border py-20 text-center">
          <Target className="size-10 text-muted-foreground" />
          <p className="text-lg font-medium">还没有可分析的面试数据</p>
          <p className="max-w-sm text-sm text-muted-foreground">
            完成至少一场面试后，这里会生成你的成长报告，包含能力雷达、强弱项与趋势分析。
          </p>
          <Button onClick={() => navigate("/interviews/start")}>
            开始一场面试
          </Button>
        </div>
      </div>
    );
  }

  const { summary, dimensionScores, strongAreas, weakAreas, trend, suggestions } =
    report;

  const stats = [
    { label: "纳入面试", value: String(summary.totalInterviews), icon: FileSearch },
    { label: "平均总分", value: summary.avgScore.toFixed(1), icon: BarChart3 },
    { label: "最高分", value: String(summary.highestScore), icon: Trophy },
    { label: "最低分", value: String(summary.lowestScore), icon: TrendingDown },
    {
      label: "面试总时长",
      value: formatDuration(summary.totalDurationSeconds),
      icon: Clock,
    },
  ];

  const sortedDims = [...dimensionScores.dimensions].sort((a, b) => a.rank - b.rank);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
          面试成长报告
        </h1>
        <p className="mt-1 text-muted-foreground">
          聚合你的历史面试，生成五维能力画像与改进建议
        </p>
      </div>

      {/* 控制面板 */}
      <Reveal className="mb-8 rounded-2xl border bg-card p-4">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex flex-wrap items-center gap-3">
            <div className="inline-flex rounded-lg bg-muted p-1">
              <button
                type="button"
                onClick={() => setMode("all")}
                className={cn(
                  "rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                  mode === "all"
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                全部面试
              </button>
              <button
                type="button"
                onClick={() => setMode("selected")}
                className={cn(
                  "rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                  mode === "selected"
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                指定面试
              </button>
            </div>

            {mode === "selected" && (
              <InterviewMultiSelect
                options={available}
                selected={selectedIds}
                onChange={setSelectedIds}
                loading={availableLoading}
              />
            )}
          </div>

          <div className="flex items-center gap-3">
            <span className="hidden text-xs text-muted-foreground sm:inline">
              报告实时生成 · 基于已完成面试
            </span>
            <Button onClick={handleGenerate} disabled={generating}>
              {generating ? (
                <Loader2 className="mr-2 size-4 animate-spin" />
              ) : (
                <RefreshCw className="mr-2 size-4" />
              )}
              生成报告
            </Button>
          </div>
        </div>
      </Reveal>

      {/* 概览统计 */}
      <Reveal className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {stats.map((s) => {
          const Icon = s.icon;
          return (
            <div
              key={s.label}
              className="rounded-xl border bg-card p-4 transition-shadow hover:shadow-sm"
            >
              <div className="mb-2 flex items-center gap-1.5 text-muted-foreground">
                <Icon className="size-4" />
                <span className="text-xs">{s.label}</span>
              </div>
              <div className="text-2xl font-semibold tracking-tight tabular-nums text-foreground">
                {s.value}
              </div>
            </div>
          );
        })}
      </Reveal>

      {/* 五维雷达 */}
      <Reveal className="mb-6" delay={0.05}>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Target className="size-4 text-brand" />
              五维能力画像
            </CardTitle>
            <CardDescription>各维度平均分（按排名由高到低）</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid gap-6 md:grid-cols-[320px_1fr] md:items-center">
              <div className="mx-auto h-[320px] w-[320px] max-w-full">
                <RadarChart
                  data={dimensionScores.dimensions.map((d) => ({
                    name: d.name,
                    score: d.score,
                  }))}
                />
              </div>
              <div className="space-y-2">
                {sortedDims.map((d) => (
                  <div
                    key={d.name}
                    className="flex items-center justify-between rounded-lg border p-3"
                  >
                    <div className="flex items-center gap-2">
                      <span className="flex size-6 items-center justify-center rounded-full bg-muted text-xs font-semibold tabular-nums">
                        {d.rank}
                      </span>
                      <span className="text-sm font-medium">{d.name}</span>
                    </div>
                    <span className="text-sm font-semibold tabular-nums text-brand">
                      {d.score.toFixed(1)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </CardContent>
        </Card>
      </Reveal>

      {/* 趋势 */}
      <Reveal className="mb-6" delay={0.1}>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrendingUp className="size-4 text-brand" />
              得分趋势
            </CardTitle>
            <CardDescription>每次面试的总分变化</CardDescription>
          </CardHeader>
          <CardContent>
            {trend.points.length < 2 && (
              <p className="mb-3 text-xs text-muted-foreground">
                至少需要 2 场面试才能形成趋势曲线，当前仅 {trend.points.length} 场。
              </p>
            )}
            <div className="h-[260px] w-full">
              <TrendChart points={trend.points} />
            </div>
          </CardContent>
        </Card>
      </Reveal>

      {/* 强项 / 弱项 */}
      <div className="mb-6 grid gap-6 lg:grid-cols-2">
        <Reveal delay={0.05}>
          <AreaCard
            title="优势维度"
            description="得分最高的两个维度及其代表题目"
            insights={strongAreas.insights}
            tone="strong"
          />
        </Reveal>
        <Reveal delay={0.1}>
          <AreaCard
            title="待提升维度"
            description="得分最低的两个维度及改进切入点"
            insights={weakAreas.insights}
            tone="weak"
          />
        </Reveal>
      </div>

      {/* 改进建议 */}
      <Reveal delay={0.05}>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Lightbulb className="size-4 text-brand" />
              改进建议
            </CardTitle>
            <CardDescription>基于薄弱维度给出的针对性提升方向</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {suggestions.items.length === 0 ? (
              <p className="text-sm text-muted-foreground">暂无改进建议</p>
            ) : (
              suggestions.items.map((s, i) => (
                <div key={i} className="flex gap-3">
                  <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-brand-muted text-xs font-semibold text-brand">
                    {i + 1}
                  </span>
                  <div>
                    <p className="text-sm font-medium">{s.dimension}</p>
                    <p className="mt-0.5 text-sm text-muted-foreground">
                      {s.suggestion}
                    </p>
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </Reveal>
    </div>
  );
}

/** 强弱项卡片（共用渲染） */
function AreaCard({
  title,
  description,
  insights,
  tone,
}: {
  title: string;
  description: string;
  insights: {
    dimension: string;
    avgScore: number;
    questions: { description: string; score: number; feedback: string }[];
  }[];
  tone: "strong" | "weak";
}) {
  const Icon = tone === "strong" ? TrendingUp : TrendingDown;
  const iconColor = tone === "strong" ? "text-brand" : "text-destructive";

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle className={cn("flex items-center gap-2", iconColor)}>
          <Icon className="size-4" />
          {title}
        </CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {insights.length === 0 ? (
          <p className="text-sm text-muted-foreground">暂无维度数据</p>
        ) : (
          insights.map((area, i) => (
            <div key={i}>
              <div className="flex items-center justify-between">
                <span className="font-medium">{area.dimension}</span>
                <Badge variant="secondary" className="tabular-nums">
                  均分 {area.avgScore.toFixed(1)}
                </Badge>
              </div>
              {area.questions.length === 0 ? (
                <p className="mt-2 text-xs text-muted-foreground">
                  题级评分尚未生成（面试刚结束可能存在延迟）
                </p>
              ) : (
                <ul className="mt-2 space-y-2">
                  {area.questions.map((q, qi) => (
                    <li key={qi} className="rounded-lg border bg-muted/40 p-3">
                      <p className="text-sm font-medium">{q.description}</p>
                      <div className="mt-1">
                        <Badge variant="outline" className="tabular-nums">
                          {q.score} 分
                        </Badge>
                      </div>
                      <p className="mt-1.5 text-sm text-muted-foreground">
                        {q.feedback}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
              {i < insights.length - 1 && <Separator className="mt-4" />}
            </div>
          ))
        )}
      </CardContent>
    </Card>
  );
}
