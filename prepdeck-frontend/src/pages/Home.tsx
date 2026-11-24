import HeroSection from "@/components/sections/home/HeroSection";
import TrustBar from "@/components/sections/home/TrustBar";
import FeaturesBento from "@/components/sections/home/FeaturesBento";
import ProcessFlow from "@/components/sections/home/ProcessFlow";
import StatsSection from "@/components/sections/home/StatsSection";
import FaqSection from "@/components/sections/home/FaqSection";
import FinalCta from "@/components/sections/home/FinalCta";
import { TourOverlay } from "@/components/tour/tour-overlay";
import { useTourTrigger } from "@/hooks/use-tour";
import { TOUR_IDS, WELCOME_TOUR_STEPS } from "@/lib/tour-steps";

/**
 * 首页：AI 模拟面试产品落地页。
 *
 * 叙事结构（AIDA + 转化收束）：
 *   Hero（注意/兴趣）→ TrustBar（信任）→ FeaturesBento（欲望）
 *   → ProcessFlow（具象化）→ Stats（量化证明）→ FAQ（消除顾虑）→ FinalCta（行动）
 *
 * 每个 section 自包含响应式与动效，此处仅负责顺序编排。
 *
 * 新用户登录后会落到本页，因此这里挂载全站欢迎引导：
 * 先讲清每月免费额度，再依次把用户引向「AI 写简历 → AI 模拟面试 → 成长报告」。
 */
export default function Home() {
  // 未登录时导航栏不渲染引导目标元素，因此只对已登录用户触发
  const loggedIn = typeof window !== 'undefined' && !!localStorage.getItem('accessToken');

  useTourTrigger(TOUR_IDS.welcome, WELCOME_TOUR_STEPS.length, { enabled: loggedIn });

  return (
    <main>
      <HeroSection />
      <TrustBar />
      <FeaturesBento />
      <ProcessFlow />
      <StatsSection />
      <FaqSection />
      <FinalCta />

      <TourOverlay tourId={TOUR_IDS.welcome} steps={WELCOME_TOUR_STEPS} />
    </main>
  );
}
