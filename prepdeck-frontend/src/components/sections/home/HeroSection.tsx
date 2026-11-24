import { motion } from "framer-motion";
import { useNavigate } from "react-router-dom";
import { ArrowRight, MessageSquare, Sparkles, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Hero 主视觉区。
 *
 * 设计取舍：
 * - 左右分屏而非居中堆叠（DESIGN_VARIANCE > 4 时禁用居中 Hero）
 * - 右侧用 CSS/SVG 抽象图形代替图片资产，体现"AI 对话"的产品本质且零外部依赖
 * - 品牌绿作为唯一强调色，替换原页面与品牌割裂的蓝色块
 */
export default function HeroSection() {
  const navigate = useNavigate();
  const token = localStorage.getItem("accessToken");

  return (
    <section className="relative overflow-hidden">
      {/* 背景装饰：品牌绿径向光晕 + 细网格 */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10"
      >
        <div className="absolute -top-32 left-1/2 h-[480px] w-[820px] -translate-x-1/2 rounded-full bg-brand/10 blur-3xl" />
        <div
          className="absolute inset-0 opacity-[0.04]"
          style={{
            backgroundImage:
              "linear-gradient(to right, currentColor 1px, transparent 1px), linear-gradient(to bottom, currentColor 1px, transparent 1px)",
            backgroundSize: "56px 56px",
            color: "var(--foreground)",
          }}
        />
      </div>

      <div className="mx-auto grid max-w-7xl items-center gap-12 px-4 py-20 md:py-28 lg:grid-cols-[1.05fr_0.95fr] lg:gap-16">
        {/* 左：文案 */}
        <div>
          <motion.span
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            className="inline-flex items-center gap-2 rounded-full border border-brand/30 bg-brand-muted/50 px-3 py-1 text-xs font-medium text-brand"
          >
            <Sparkles className="size-3.5" />
            AI 驱动 · 真实大厂面试官
          </motion.span>

          <motion.h1
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.05 }}
            className="mt-5 text-4xl font-bold leading-[1.08] tracking-tight text-foreground sm:text-5xl md:text-6xl"
          >
            像在真正的面试现场，
            <br className="hidden sm:block" />
            <span className="text-brand">被 AI 面试官追问。</span>
          </motion.h1>

          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.12 }}
            className="mt-6 max-w-xl text-base leading-relaxed text-muted-foreground md:text-lg"
          >
            上传简历，选择目标公司与岗位，AI 面试官将基于你的真实经历展开
            自我介绍、项目深挖、技术考核与反问环节的全流程模拟，结束即给出针对性反馈。
          </motion.p>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.19 }}
            className="mt-8 flex flex-wrap items-center gap-3"
          >
            <Button
              size="lg"
              className="h-11 px-6 text-base"
              onClick={() => navigate(token ? "/interviews/start" : "/login")}
            >
              开始模拟面试
              <ArrowRight className="size-4" />
            </Button>
            <Button
              size="lg"
              variant="outline"
              className="h-11 px-6 text-base"
              onClick={() => navigate("/interviews")}
            >
              查看面试记录
            </Button>
          </motion.div>

          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.3 }}
            className="mt-7 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-muted-foreground"
          >
            <span className="inline-flex items-center gap-1.5">
              <ShieldCheck className="size-4 text-brand" />
              简历本地加密
            </span>
            <span className="inline-flex items-center gap-1.5">
              <MessageSquare className="size-4 text-brand" />
              实时智能追问
            </span>
            <span className="inline-flex items-center gap-1.5">
              <Sparkles className="size-4 text-brand" />
              覆盖 6+ 头部大厂
            </span>
          </motion.div>
        </div>

        {/* 右：AI 对话可视化卡片 */}
        <motion.div
          initial={{ opacity: 0, scale: 0.96, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          transition={{ duration: 0.7, delay: 0.15, ease: [0.16, 1, 0.3, 1] }}
          className="relative"
        >
          <InterviewCardVisual />
        </motion.div>
      </div>
    </section>
  );
}

/**
 * 右侧可视化：模拟一段 AI 面试官与候选人的对话。
 * 纯 CSS/SVG 实现，无外部图片依赖。
 */
function InterviewCardVisual() {
  return (
    <div className="relative">
      {/* 浮动徽章 */}
      <motion.div
        animate={{ y: [0, -8, 0] }}
        transition={{ duration: 4, repeat: Infinity, ease: "easeInOut" }}
        className="absolute -left-4 top-10 z-20 hidden rounded-xl border bg-background/90 px-3 py-2 text-xs shadow-lg backdrop-blur sm:block"
      >
        <div className="flex items-center gap-2">
          <span className="flex size-2 rounded-full bg-brand">
            <span className="size-2 animate-ping rounded-full bg-brand/70" />
          </span>
          <span className="font-medium text-foreground">面试进行中</span>
        </div>
      </motion.div>

      <motion.div
        animate={{ y: [0, 8, 0] }}
        transition={{ duration: 5, repeat: Infinity, ease: "easeInOut", delay: 1 }}
        className="absolute -right-3 bottom-16 z-20 hidden rounded-xl border bg-background/90 px-3 py-2 text-xs shadow-lg backdrop-blur sm:block"
      >
        <div className="flex items-center gap-2">
          <Sparkles className="size-3.5 text-brand" />
          <span className="font-medium text-foreground">智能追问中</span>
        </div>
      </motion.div>

      {/* 对话卡片主体 */}
      <div className="relative overflow-hidden rounded-3xl border border-border bg-card p-5 shadow-xl shadow-brand/5 md:p-6">
        {/* 卡片顶部 */}
        <div className="flex items-center justify-between border-b border-border pb-3">
          <div className="flex items-center gap-2.5">
            <div className="flex size-9 items-center justify-center rounded-full bg-brand text-brand-foreground">
              <Sparkles className="size-4" />
            </div>
            <div>
              <div className="text-sm font-semibold text-foreground">
                AI 面试官 · 字节跳动
              </div>
              <div className="text-xs text-muted-foreground">
                前端工程师 · 高级
              </div>
            </div>
          </div>
          <div className="flex gap-1.5">
            <span className="size-2 rounded-full bg-muted-foreground/30" />
            <span className="size-2 rounded-full bg-muted-foreground/30" />
            <span className="size-2 rounded-full bg-brand" />
          </div>
        </div>

        {/* 对话流 */}
        <div className="space-y-3 pt-4">
          {/* AI 气泡 */}
          <motion.div
            initial={{ opacity: 0, x: -12 }}
            whileInView={{ opacity: 1, x: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.4, delay: 0.4 }}
            className="flex gap-2"
          >
            <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-brand/10 text-brand">
              <Sparkles className="size-3" />
            </div>
            <div className="max-w-[85%] rounded-2xl rounded-tl-sm bg-muted px-3.5 py-2.5 text-sm text-foreground">
              先做个简单的自我介绍吧，重点讲讲你最近的项目经历。
            </div>
          </motion.div>

          {/* 候选人气泡 */}
          <motion.div
            initial={{ opacity: 0, x: 12 }}
            whileInView={{ opacity: 1, x: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.4, delay: 0.7 }}
            className="flex justify-end"
          >
            <div className="max-w-[80%] rounded-2xl rounded-tr-sm bg-brand px-3.5 py-2.5 text-sm text-brand-foreground">
              我三年前端经验，最近主导了一个低代码平台的可视化引擎重构…
            </div>
          </motion.div>

          {/* AI 追问气泡（带打字指示） */}
          <motion.div
            initial={{ opacity: 0, x: -12 }}
            whileInView={{ opacity: 1, x: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.4, delay: 1 }}
            className="flex gap-2"
          >
            <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-brand/10 text-brand">
              <Sparkles className="size-3" />
            </div>
            <div className="max-w-[88%] rounded-2xl rounded-tl-sm bg-muted px-3.5 py-2.5 text-sm text-foreground">
              你提到"重构可视化引擎"，具体解决了哪些性能瓶颈？有没有量化的指标？
              <span className="ml-0.5 inline-flex items-center gap-0.5 align-middle">
                <span className="size-1.5 animate-bounce rounded-full bg-brand [animation-delay:-0.3s]" />
                <span className="size-1.5 animate-bounce rounded-full bg-brand [animation-delay:-0.15s]" />
                <span className="size-1.5 animate-bounce rounded-full bg-brand" />
              </span>
            </div>
          </motion.div>
        </div>

        {/* 底部流程进度 */}
        <div className="mt-5 flex items-center gap-1.5 border-t border-border pt-3">
          {["自我介绍", "项目深挖", "技术考核", "反问环节"].map((s, i) => (
            <div
              key={s}
              className="flex flex-1 items-center gap-1.5"
            >
              <div
                className={`size-1.5 rounded-full ${i <= 1 ? "bg-brand" : "bg-muted-foreground/30"}`}
              />
              <span
                className={`truncate text-[10px] ${i <= 1 ? "font-medium text-foreground" : "text-muted-foreground"}`}
              >
                {s}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
