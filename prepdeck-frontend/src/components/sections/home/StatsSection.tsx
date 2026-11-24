import { useEffect, useRef, useState } from "react";
import { motion, useInView, useMotionValue, animate } from "framer-motion";
import { Reveal } from "@/components/motion/Reveal";

interface Stat {
  value: number;
  suffix: string;
  label: string;
  desc: string;
}

const STATS: Stat[] = [
  {
    value: 6,
    suffix: "+",
    label: "覆盖大厂",
    desc: "字节、阿里、腾讯等头部公司面试风格",
  },
  {
    value: 4,
    suffix: "阶段",
    label: "完整流程",
    desc: "从自我介绍到反问环节全覆盖",
  },
  {
    value: 24,
    suffix: "h",
    label: "随时开练",
    desc: "无需预约，随时随地开始模拟",
  },
];

/**
 * 数据证明区：关键数字在进入视口时滚动到目标值。
 * 用 useInView 触发一次，animate 驱动数值变化。
 */
export default function StatsSection() {
  return (
    <section className="mx-auto max-w-7xl px-4 py-20 md:py-28">
      <Reveal className="mb-12 text-center">
        <h2 className="text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          为每一场真实面试做准备
        </h2>
        <p className="mx-auto mt-4 max-w-xl text-base leading-relaxed text-muted-foreground">
          模拟越接近真实，临场越从容。
        </p>
      </Reveal>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {STATS.map((stat, i) => (
          <StatCard key={stat.label} stat={stat} index={i} />
        ))}
      </div>
    </section>
  );
}

function StatCard({ stat, index }: { stat: Stat; index: number }) {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref, { once: true, margin: "-60px" });
  const count = useMotionValue(0);
  const [display, setDisplay] = useState(0);

  useEffect(() => {
    if (!inView) return;
    const controls = animate(count, stat.value, {
      duration: 1.2,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (v) => setDisplay(Math.round(v)),
    });
    return () => controls.stop();
  }, [inView, stat.value, count]);

  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.5, delay: index * 0.1 }}
      className="rounded-2xl border border-border bg-card p-7 text-center"
    >
      <div className="text-4xl font-bold tracking-tight text-brand md:text-5xl">
        {display}
        <span className="text-2xl md:text-3xl">{stat.suffix}</span>
      </div>
      <div className="mt-2 text-base font-semibold text-foreground">
        {stat.label}
      </div>
      <p className="mt-1 text-sm text-muted-foreground">{stat.desc}</p>
    </motion.div>
  );
}
