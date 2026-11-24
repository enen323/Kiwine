import { motion } from "framer-motion";
import {
  Building2,
  Workflow,
  FileText,
  Gauge,
  type LucideIcon,
} from "lucide-react";
import { Reveal } from "@/components/motion/Reveal";

interface Feature {
  icon: LucideIcon;
  title: string;
  desc: string;
  /** 在 bento 网格中的跨列。lg 下网格为 6 列。 */
  span: string;
  /** 强调态：深色反色卡 */
  emphasis?: boolean;
}

const FEATURES: Feature[] = [
  {
    icon: Building2,
    title: "多公司风格适配",
    desc: "字节重底层原理、阿里看项目深度、腾讯考工程化——针对每家大厂调整提问策略与考察侧重，而非通用模板。",
    span: "lg:col-span-3",
  },
  {
    icon: Workflow,
    title: "全流程模拟",
    desc: "自我介绍 → 项目深挖 → 技术考核 → 反问环节，完整还原真实面试节奏与追问链路。",
    span: "lg:col-span-3",
    emphasis: true,
  },
  {
    icon: FileText,
    title: "智能简历解析",
    desc: "粘贴文本或上传 PDF，AI 自动结构化提取项目与技术栈，面试官基于你的真实经历提问。",
    span: "lg:col-span-2",
  },
  {
    icon: Gauge,
    title: "实时反馈评分",
    desc: "结束即生成多维度评估报告，定位薄弱环节并给出改进建议。",
    span: "lg:col-span-2",
  },
  {
    icon: Building2,
    title: "可复用面试档案",
    desc: "每次面试自动归档，按公司/岗位/时间检索复盘，追踪能力成长曲线。",
    span: "lg:col-span-2",
  },
];

/**
 * 核心能力 Bento Grid。
 *
 * 取舍：用非对称 6 列网格（3+3 / 2+2+2）替代原页面等宽 3 列卡片，
 * 通过尺寸差异建立视觉重心。强调卡用深色反色锚定节奏。
 */
export default function FeaturesBento() {
  return (
    <section className="mx-auto max-w-7xl px-4 py-20 md:py-28">
      <Reveal className="mb-12 max-w-2xl">
        <p className="mb-3 text-sm font-semibold uppercase tracking-wider text-brand">
          核心能力
        </p>
        <h2 className="text-3xl font-bold tracking-tight text-foreground sm:text-4xl md:text-5xl">
          不止是问答，而是完整的面试演练
        </h2>
        <p className="mt-4 text-base leading-relaxed text-muted-foreground">
          从简历解析到反馈评分，覆盖面试全链路的每一个关键环节。
        </p>
      </Reveal>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-6">
        {FEATURES.map((f, i) => (
          <FeatureCard key={f.title} feature={f} index={i} />
        ))}
      </div>
    </section>
  );
}

function FeatureCard({ feature, index }: { feature: Feature; index: number }) {
  const { icon: Icon, title, desc, span, emphasis } = feature;
  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-60px" }}
      transition={{
        duration: 0.5,
        delay: index * 0.08,
        ease: [0.16, 1, 0.3, 1],
      }}
      whileHover={{ y: -4 }}
      className={[
        "group relative overflow-hidden rounded-2xl border p-6 md:p-7",
        span,
        emphasis
          ? "border-brand/30 bg-zinc-950 text-zinc-50"
          : "border-border bg-card hover:border-brand/40 hover:shadow-lg hover:shadow-brand/5",
      ].join(" ")}
    >
      {/* 悬浮光斑 */}
      <div
        aria-hidden
        className="pointer-events-none absolute -right-12 -top-12 size-32 rounded-full bg-brand/10 opacity-0 blur-2xl transition-opacity duration-500 group-hover:opacity-100"
      />

      <div
        className={[
          "mb-4 inline-flex size-11 items-center justify-center rounded-xl transition-colors",
          emphasis
            ? "bg-brand/20 text-brand"
            : "bg-brand-muted text-brand group-hover:bg-brand group-hover:text-brand-foreground",
        ].join(" ")}
      >
        <Icon className="size-5" />
      </div>

      <h3
        className={[
          "mb-2 text-lg font-semibold tracking-tight",
          emphasis ? "text-zinc-50" : "text-foreground",
        ].join(" ")}
      >
        {title}
      </h3>
      <p
        className={[
          "text-sm leading-relaxed",
          emphasis ? "text-zinc-400" : "text-muted-foreground",
        ].join(" ")}
      >
        {desc}
      </p>
    </motion.div>
  );
}
