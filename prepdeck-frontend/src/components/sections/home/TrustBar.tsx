import { Reveal } from "@/components/motion/Reveal";

const COMPANIES = ["字节跳动", "阿里巴巴", "腾讯", "百度", "美团", "拼多多"];

/**
 * 信任徽章条：简洁横排公司名，承接 Hero。
 * 用分隔点 + 灰度文字，避免与下方 Bento 抢焦点。
 */
export default function TrustBar() {
  return (
    <section className="border-y border-border bg-muted/30">
      <Reveal className="mx-auto max-w-7xl px-4 py-8">
        <p className="mb-5 text-center text-xs font-medium uppercase tracking-wider text-muted-foreground">
          覆盖头部互联网公司面试风格
        </p>
        <div className="flex flex-wrap items-center justify-center gap-x-8 gap-y-4">
          {COMPANIES.map((name) => (
            <span
              key={name}
              className="text-lg font-semibold text-muted-foreground/70 transition-colors hover:text-foreground md:text-xl"
            >
              {name}
            </span>
          ))}
        </div>
      </Reveal>
    </section>
  );
}
