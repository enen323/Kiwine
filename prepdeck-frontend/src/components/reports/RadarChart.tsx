"use client";

import { motion, useReducedMotion } from "framer-motion";
import { DIMENSION_ORDER } from "@/types";
import { cn } from "@/lib/utils";

interface RadarDatum {
  name: string;
  score: number;
}

interface RadarChartProps {
  /** 五维数据；缺失维度按 0 处理，轴向顺序固定为 DIMENSION_ORDER */
  data: RadarDatum[];
  className?: string;
}

const SIZE = 320;
const CENTER = SIZE / 2;
const PAD = 56;
const RADIUS = CENTER - PAD;
const RINGS = 4;

export function RadarChart({ data, className }: RadarChartProps) {
  const reduce = useReducedMotion();

  // 固定轴向顺序，保证多边形形状稳定（不随排名波动而旋转）
  const ordered = DIMENSION_ORDER.map((name) => {
    const found = data.find((d) => d.name === name);
    return { name, score: found ? found.score : 0 };
  });
  const n = ordered.length;
  const max = Math.max(...ordered.map((d) => d.score), 1) * 1.15;

  const angle = (i: number) => -Math.PI / 2 + (i * 2 * Math.PI) / n;
  const at = (i: number, r: number): [number, number] => {
    const a = angle(i);
    return [CENTER + Math.cos(a) * r, CENTER + Math.sin(a) * r];
  };

  const ringPolys = Array.from({ length: RINGS }, (_, ri) => {
    const r = (RADIUS * (ri + 1)) / RINGS;
    return ordered.map((_, i) => at(i, r).join(",")).join(" ");
  });

  const dataPoints = ordered.map((d, i) =>
    at(i, (Math.max(d.score, 0) / max) * RADIUS),
  );
  const dataPoly = dataPoints.map((p) => p.join(",")).join(" ");

  const motionProps = reduce
    ? {}
    : {
        initial: { scale: 0, opacity: 0 },
        animate: { scale: 1, opacity: 1 },
        transition: { type: "spring" as const, stiffness: 120, damping: 18 },
        style: { transformBox: "view-box" as const, transformOrigin: `${CENTER}px ${CENTER}px` },
      };

  return (
    <svg
      viewBox={`0 0 ${SIZE} ${SIZE}`}
      className={cn("h-full w-full", className)}
      role="img"
      aria-label="五维能力雷达图"
    >
      {/* 网格环 */}
      {ringPolys.map((pts, i) => (
        <polygon
          key={i}
          points={pts}
          fill="none"
          stroke="var(--border)"
          strokeWidth={1}
        />
      ))}

      {/* 轴向 + 维度名称 */}
      {ordered.map((d, i) => {
        const [ex, ey] = at(i, RADIUS);
        const [lx, ly] = at(i, RADIUS + 22);
        const a = angle(i);
        const anchor =
          Math.abs(Math.cos(a)) < 0.3 ? "middle" : Math.cos(a) > 0 ? "start" : "end";
        return (
          <g key={d.name}>
            <line
              x1={CENTER}
              y1={CENTER}
              x2={ex}
              y2={ey}
              stroke="var(--border)"
              strokeWidth={1}
            />
            <text
              x={lx}
              y={ly}
              textAnchor={anchor}
              dominantBaseline="middle"
              className="fill-muted-foreground text-[11px]"
            >
              {d.name}
            </text>
          </g>
        );
      })}

      {/* 数据多边形 + 顶点 */}
      <motion.g {...motionProps}>
        <polygon
          points={dataPoly}
          fill="var(--brand)"
          fillOpacity={0.14}
          stroke="var(--brand)"
          strokeWidth={2}
          strokeLinejoin="round"
        />
        {dataPoints.map(([x, y], i) => (
          <circle key={i} cx={x} cy={y} r={3.5} fill="var(--brand)" />
        ))}
      </motion.g>

      {/* 分值标签（位于顶点外侧） */}
      {ordered.map((d, i) => {
        const a = angle(i);
        const [vx, vy] = at(i, (Math.max(d.score, 0) / max) * RADIUS);
        const lx = vx + Math.cos(a) * 14;
        const ly = vy + Math.sin(a) * 14;
        const anchor =
          Math.abs(Math.cos(a)) < 0.3 ? "middle" : Math.cos(a) > 0 ? "start" : "end";
        return (
          <text
            key={d.name}
            x={lx}
            y={ly}
            textAnchor={anchor}
            dominantBaseline="middle"
            className="fill-foreground text-[11px] font-medium tabular-nums"
          >
            {d.score.toFixed(1)}
          </text>
        );
      })}
    </svg>
  );
}
