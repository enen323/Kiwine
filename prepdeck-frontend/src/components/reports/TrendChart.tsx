"use client";

import { motion, useReducedMotion } from "framer-motion";
import type { TrendPoint } from "@/types";
import { cn } from "@/lib/utils";

interface TrendChartProps {
  points: TrendPoint[];
  className?: string;
}

const W = 640;
const H = 260;
const PAD_L = 28;
const PAD_R = 20;
const PAD_T = 24;
const PAD_B = 40;
const PLOT_W = W - PAD_L - PAD_R;
const PLOT_H = H - PAD_T - PAD_B;

function formatDate(iso: string): string {
  // 输入格式 yyyy-MM-dd，输出 MM-DD
  const m = iso.match(/(\d{4})-(\d{2})-(\d{2})/);
  if (!m) return iso;
  return `${m[2]}-${m[3]}`;
}

export function TrendChart({ points, className }: TrendChartProps) {
  const reduce = useReducedMotion();

  if (points.length === 0) {
    return (
      <div className={cn("flex h-full items-center justify-center text-sm text-muted-foreground", className)}>
        暂无趋势数据
      </div>
    );
  }

  const scores = points.map((p) => p.score);
  const rawMin = Math.min(...scores);
  const rawMax = Math.max(...scores);
  const pad = Math.ceil((rawMax - rawMin || 10) * 0.2);
  // 给 y 轴留白，避免折线贴边
  const min = Math.max(0, rawMin - pad);
  const max = rawMax + pad;
  const span = max - min || 1;

  const xFor = (i: number) =>
    points.length === 1
      ? PAD_L + PLOT_W / 2
      : PAD_L + (i / (points.length - 1)) * PLOT_W;
  const yFor = (s: number) => PAD_T + (1 - (s - min) / span) * PLOT_H;

  const coords = points.map((p, i) => ({
    x: xFor(i),
    y: yFor(p.score),
    score: p.score,
    date: p.date,
  }));

  const linePath =
    points.length === 1
      ? `M ${coords[0].x} ${coords[0].y}`
      : coords.map((c, i) => `${i === 0 ? "M" : "L"} ${c.x} ${c.y}`).join(" ");

  const areaPath =
    `M ${coords[0].x} ${PAD_T + PLOT_H} ` +
    coords.map((c) => `L ${c.x} ${c.y}`).join(" ") +
    ` L ${coords[coords.length - 1].x} ${PAD_T + PLOT_H} Z`;

  const lineMotion = reduce
    ? {}
    : {
        initial: { pathLength: 0 },
        animate: { pathLength: 1 },
        transition: { duration: 0.9, ease: [0.16, 1, 0.3, 1] as const },
      };
  const dotMotion = reduce
    ? {}
    : {
        initial: { scale: 0, opacity: 0 },
        animate: { scale: 1, opacity: 1 },
        transition: { type: "spring" as const, stiffness: 200, damping: 20 },
      };

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      className={cn("h-full w-full", className)}
      role="img"
      aria-label="面试总分趋势图"
    >
      <defs>
        <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--brand)" stopOpacity={0.22} />
          <stop offset="100%" stopColor="var(--brand)" stopOpacity={0} />
        </linearGradient>
      </defs>

      {/* 基线 */}
      <line
        x1={PAD_L}
        y1={PAD_T + PLOT_H}
        x2={W - PAD_R}
        y2={PAD_T + PLOT_H}
        stroke="var(--border)"
        strokeWidth={1}
      />
      {/* 顶部参考线 */}
      <line
        x1={PAD_L}
        y1={PAD_T}
        x2={W - PAD_R}
        y2={PAD_T}
        stroke="var(--border)"
        strokeWidth={1}
        strokeDasharray="3 4"
        opacity={0.6}
      />

      {/* 面积 */}
      {points.length > 1 && <path d={areaPath} fill="url(#trendFill)" stroke="none" />}

      {/* 折线 */}
      <motion.path
        d={linePath}
        fill="none"
        stroke="var(--brand)"
        strokeWidth={2.5}
        strokeLinecap="round"
        strokeLinejoin="round"
        {...lineMotion}
      />

      {/* 数据点 + 分值 + 日期 */}
      {coords.map((c, i) => (
        <motion.g
          key={i}
          {...dotMotion}
          style={{ transformOrigin: `${c.x}px ${c.y}px`, transformBox: "view-box" }}
        >
          <circle
            cx={c.x}
            cy={c.y}
            r={4}
            fill="var(--brand)"
            stroke="var(--background)"
            strokeWidth={2}
          />
          <text
            x={c.x}
            y={c.y - 12}
            textAnchor="middle"
            className="fill-foreground text-[11px] font-semibold tabular-nums"
          >
            {c.score}
          </text>
          <text
            x={c.x}
            y={PAD_T + PLOT_H + 20}
            textAnchor="middle"
            className="fill-muted-foreground text-[10px] tabular-nums"
          >
            {formatDate(c.date)}
          </text>
        </motion.g>
      ))}
    </svg>
  );
}
