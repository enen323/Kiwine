import { motion } from "framer-motion";
import { Reveal } from "@/components/motion/Reveal";

interface Stage {
  step: string;
  title: string;
  desc: string;
}

const STAGES: Stage[] = [
  {
    step: "01",
    title: "自我介绍",
    desc: "AI 面试官开场破冰，引导你简要介绍背景与方向，建立面试基调。",
  },
  {
    step: "02",
    title: "项目深挖",
    desc: "基于简历中提取的真实项目，逐层追问技术决策、难点与量化成果。",
  },
  {
    step: "03",
    title: "技术考核",
    desc: "围绕目标岗位考察基础知识、算法与系统设计，难度随公司档位调整。",
  },
  {
    step: "04",
    title: "反问环节",
    desc: "模拟候选人提问阶段，AI 也会评估你提出问题的质量与深度。",
  },
];

/**
 * 面试流程时间线：横向四阶段，节点带入场动效。
 * 移动端自动堆叠为纵向。
 */
export default function ProcessFlow() {
  return (
    <section className="border-y border-border bg-muted/30">
      <div className="mx-auto max-w-7xl px-4 py-20 md:py-28">
        <Reveal className="mb-14 max-w-2xl">
          <p className="mb-3 text-sm font-semibold uppercase tracking-wider text-brand">
            完整流程
          </p>
          <h2 className="text-3xl font-bold tracking-tight text-foreground sm:text-4xl md:text-5xl">
            四个阶段，还原真实面试节奏
          </h2>
          <p className="mt-4 text-base leading-relaxed text-muted-foreground">
            每一阶段都对应大厂面试的标准环节，环环相扣。
          </p>
        </Reveal>

        <div className="relative grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-4">
          {/* 连接线（桌面端） */}
          <div
            aria-hidden
            className="pointer-events-none absolute left-0 right-0 top-[2.25rem] hidden h-px bg-gradient-to-r from-transparent via-border to-transparent lg:block"
          />

          {STAGES.map((stage, i) => (
            <motion.div
              key={stage.step}
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-60px" }}
              transition={{
                duration: 0.5,
                delay: i * 0.12,
                ease: [0.16, 1, 0.3, 1],
              }}
              className="relative"
            >
              {/* 节点 */}
              <div className="mb-5 flex items-center gap-3">
                <div className="relative z-10 flex size-16 items-center justify-center rounded-2xl border border-border bg-card text-xl font-bold text-brand shadow-sm">
                  {stage.step}
                </div>
                {i < STAGES.length - 1 && (
                  <div
                    aria-hidden
                    className="h-px flex-1 bg-border sm:hidden lg:hidden"
                  />
                )}
              </div>

              <h3 className="mb-2 text-lg font-semibold tracking-tight text-foreground">
                {stage.title}
              </h3>
              <p className="text-sm leading-relaxed text-muted-foreground">
                {stage.desc}
              </p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
