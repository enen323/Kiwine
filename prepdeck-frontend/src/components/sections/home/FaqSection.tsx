import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Plus } from "lucide-react";
import { Reveal } from "@/components/motion/Reveal";

interface QA {
  q: string;
  a: string;
}

const FAQS: QA[] = [
  {
    q: "AI 面试官的提问真的贴近大厂风格吗？",
    a: "我们针对字节、阿里、腾讯等公司梳理了各自的考察侧重——例如字节偏底层原理与算法、阿里重项目深度与业务思考、腾讯考工程化与协作。AI 会根据你选择的公司调整提问策略与难度档位，而非套用统一模板。",
  },
  {
    q: "上传的简历会被如何处理？",
    a: "简历内容仅用于本次面试的提问依据，在你完成模拟后可随时从面试档案中删除。我们不将简历用于模型训练，解析过程在加密通道内完成。",
  },
  {
    q: "面试结束后我能得到什么反馈？",
    a: "每次模拟结束会生成一份多维度评估报告，覆盖表达结构、项目深度、技术准确度与反问质量，并标注可改进的具体环节，便于针对性复盘。",
  },
  {
    q: "支持哪些岗位和经验层级？",
    a: "目前覆盖前端、后端、算法、产品等常见技术岗位，并支持校招到社招高级的经验层级调节。你可以在开始面试时选择目标岗位与档位。",
  },
  {
    q: "一次模拟大概需要多长时间？",
    a: "完整四阶段流程通常在 15-25 分钟。你也可以中途暂停，下次从面试档案继续，AI 会保留上下文。",
  },
];

/**
 * FAQ 手风琴：展开/收起，用 AnimatePresence 做高度过渡。
 * 替代原先缺失的常见问题区，处理用户核心疑虑。
 */
export default function FaqSection() {
  const [open, setOpen] = useState<number | null>(0);

  return (
    <section className="border-t border-border bg-muted/30">
      <div className="mx-auto max-w-3xl px-4 py-20 md:py-28">
        <Reveal className="mb-10 text-center">
          <p className="mb-3 text-sm font-semibold uppercase tracking-wider text-brand">
            常见问题
          </p>
          <h2 className="text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
            你可能想问的
          </h2>
        </Reveal>

        <div className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card">
          {FAQS.map((item, i) => {
            const isOpen = open === i;
            return (
              <div key={item.q}>
                <button
                  type="button"
                  onClick={() => setOpen(isOpen ? null : i)}
                  className="flex w-full items-center justify-between gap-4 px-5 py-4 text-left transition-colors hover:bg-accent/50 md:px-6"
                  aria-expanded={isOpen}
                >
                  <span className="text-sm font-medium text-foreground md:text-base">
                    {item.q}
                  </span>
                  <motion.span
                    animate={{ rotate: isOpen ? 45 : 0 }}
                    transition={{ duration: 0.2 }}
                    className="flex size-7 shrink-0 items-center justify-center rounded-full bg-brand-muted text-brand"
                  >
                    <Plus className="size-4" />
                  </motion.span>
                </button>
                <AnimatePresence initial={false}>
                  {isOpen && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: "auto", opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
                      className="overflow-hidden"
                    >
                      <p className="px-5 pb-5 text-sm leading-relaxed text-muted-foreground md:px-6 md:text-[15px]">
                        {item.a}
                      </p>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
