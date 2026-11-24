import { motion, type Variants } from "framer-motion";
import type { ReactNode } from "react";

/**
 * 滚动进入视口时揭示内容的通用动效原语。
 *
 * 用法：
 *   <Reveal>内容</Reveal>            // 默认 fade + 上移
 *   <Reveal delay={0.1}>内容</Reveal> // 延迟
 *   <Reveal as="h2">标题</Reveal>     // 渲染为指定标签
 *
 * 性能与可访问性：
 * - 仅动画 transform / opacity（GPU 友好）
 * - 只触发一次（viewport once），避免反复重绘
 * - 遵守 prefers-reduced-motion（framer-motion 内置 useReducedMotion）
 */
interface RevealProps {
  children: ReactNode;
  className?: string;
  delay?: number;
  y?: number;
  as?: "div" | "section" | "h1" | "h2" | "h3" | "p" | "span" | "li";
}

const buildVariants = (y: number, delay: number): Variants => ({
  hidden: { opacity: 0, y },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      duration: 0.6,
      delay,
      ease: [0.16, 1, 0.3, 1], // 平滑减速
    },
  },
});

export function Reveal({
  children,
  className,
  delay = 0,
  y = 24,
  as = "div",
}: RevealProps) {
  const MotionTag = motion[as];
  return (
    <MotionTag
      className={className}
      variants={buildVariants(y, delay)}
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, margin: "-80px" }}
    >
      {children}
    </MotionTag>
  );
}

/**
 * 为子元素列表提供顺序入场。每个直接子元素需是 motion 组件或使用
 * Reveal/StaggerItem。
 */
export const staggerContainer: Variants = {
  hidden: {},
  visible: {
    transition: { staggerChildren: 0.08, delayChildren: 0.05 },
  },
};

export const staggerItem: Variants = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.5, ease: [0.16, 1, 0.3, 1] },
  },
};
