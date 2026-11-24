import { motion } from "framer-motion";
import { useNavigate } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Reveal } from "@/components/motion/Reveal";

/**
 * 最终 CTA：深色反色区块，呼应 Hero。
 * 品牌绿作为按钮强调色，承接全篇叙事收束。
 */
export default function FinalCta() {
  const navigate = useNavigate();
  const token = localStorage.getItem("accessToken");

  return (
    <section className="mx-auto max-w-7xl px-4 py-20 md:py-28">
      <Reveal>
        <div className="relative overflow-hidden rounded-3xl bg-zinc-950 px-6 py-16 text-center md:px-12 md:py-24">
          {/* 装饰光晕 */}
          <div
            aria-hidden
            className="pointer-events-none absolute inset-0"
          >
            <div className="absolute left-1/2 top-0 h-72 w-[640px] -translate-x-1/2 rounded-full bg-brand/20 blur-3xl" />
            <div
              className="absolute inset-0 opacity-[0.05]"
              style={{
                backgroundImage:
                  "radial-gradient(circle, rgba(255,255,255,0.6) 1px, transparent 1px)",
                backgroundSize: "32px 32px",
              }}
            />
          </div>

          <div className="relative">
            <motion.h2
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6 }}
              className="text-3xl font-bold tracking-tight text-zinc-50 sm:text-4xl md:text-5xl"
            >
              每一次模拟，都离 offer 更近一步
            </motion.h2>
            <p className="mx-auto mt-5 max-w-xl text-base leading-relaxed text-zinc-400 md:text-lg">
              无需预约，无需等待。现在就开始你的第一次 AI 模拟面试。
            </p>
            <div className="mt-9 flex justify-center">
              <Button
                size="lg"
                className="h-12 bg-brand px-8 text-base text-brand-foreground hover:bg-brand-hover"
                onClick={() => navigate(token ? "/interviews/start" : "/login")}
              >
                开始模拟面试
                <ArrowRight className="size-4" />
              </Button>
            </div>
          </div>
        </div>
      </Reveal>
    </section>
  );
}
