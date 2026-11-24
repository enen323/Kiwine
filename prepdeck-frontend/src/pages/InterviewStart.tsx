import { useState, useEffect } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { resumeApi, normalizeResumeList } from '../api/resume';
import { interviewApi } from '../api/interview';
import { userApi } from '../api/user';
import { paymentApi } from '../api/payment';
import { requestWechatPayment } from '@/lib/payment';
import { toast } from 'sonner';
import { QuotaPaymentDialog } from '@/components/quota/quota-payment-dialog';
import {
  CompanyEnum, PositionEnum, SERVICE_PRICE, QUOTA_ERROR_CODES, INTERVIEW_ERROR_CODES,
  type CompanyType, type PositionType, type ResumeVO,
} from '../types';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { TourOverlay } from '@/components/tour/tour-overlay';
import { useTourTrigger } from '@/hooks/use-tour';
import { TOUR_IDS, INTERVIEW_START_TOUR_STEPS } from '@/lib/tour-steps';

/**
 * 把后端业务错误（HTTP 200 + code != 0）转换成用户可读的中文提示，
 * 避免把 QUOTA_EXHAUSTED 之类的原始英文码值直接暴露到页面上。
 * 若后端 message 本身是中文可读文案则直接展示，否则按错误码给出对应中文说明。
 */
function toUserMessage(code: number, rawMessage?: string): string {
  if (code === QUOTA_ERROR_CODES.FREE_EXHAUSTED) {
    return '本月免费额度已用完，可充值后继续使用';
  }
  // Sentinel 熔断：quota-service 不可用时后端返回 90001
  if (code === INTERVIEW_ERROR_CODES.SERVICE_UNAVAILABLE) {
    return '服务繁忙，请稍后重试';
  }
  // AI 调用熔断：DeepSeek 不可用时后端返回 50003
  if (code === INTERVIEW_ERROR_CODES.LLM_SERVICE_ERROR) {
    return 'AI 服务异常，请稍后重试';
  }
  // 仅当 message 为可读文本（非纯大写英文/数字码）时才直接展示，避免泄露原始码值
  if (rawMessage && !/^[A-Z0-9_]+$/.test(rawMessage)) return rawMessage;
  return '创建失败，请稍后重试';
}

export default function InterviewStart() {
  const navigate = useNavigate();
  const [resumes, setResumes] = useState<ResumeVO[]>([]);
  const [company, setCompany] = useState<CompanyType>('BYTE_DANCE');
  const [position, setPosition] = useState<PositionType>('BACKEND');
  const [resumeId, setResumeId] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState('');
  // 简历列表加载失败提示（原来 .catch 静默吞掉，导致「选择器不显示」却无任何线索）
  const [resumeError, setResumeError] = useState('');
  // 当前用户 ID（用于支付充值扣费）
  const [userId, setUserId] = useState<string | number | null>(null);
  // 配额不足时的充值弹窗受控状态
  const [recharging, setRecharging] = useState<{
    open: boolean;
    loading: boolean;
    error: string | null;
  }>({ open: false, loading: false, error: null });

  useEffect(() => {
    resumeApi.list()
      .then(res => {
        const { list } = normalizeResumeList(res.data);
        setResumes(list);
        const active = list.find(r => r.isActive);
        if (active) setResumeId(String(active.id));
      })
      .catch((e) => {
        console.error('简历列表加载失败', e);
        setResumeError('简历列表加载失败，请刷新页面重试');
      })
      .finally(() => setLoading(false));
  }, []);

  // 获取当前用户 ID，供配额充值使用
  useEffect(() => {
    userApi.getMe()
      .then(res => setUserId(res.data.data.id))
      .catch(() => {});
  }, []);

  // 新手引导：表单渲染完成后触发，讲清「关联简历」的价值与额度消耗
  useTourTrigger(TOUR_IDS.interviewStart, INTERVIEW_START_TOUR_STEPS.length, { ready: !loading });

  /** 免费额度不足时弹出充值询问框 */
  const openRecharge = () => {
    setRecharging({ open: true, loading: false, error: null });
  };

  const closeRecharge = () => {
    setRecharging({ open: false, loading: false, error: null });
  };

  /** 用户在充值弹窗点击「去支付」：发起微信支付 */
  const handleRechargePay = async () => {
    if (userId == null) {
      setRecharging(r => ({ ...r, error: '未获取到用户身份，请重新登录' }));
      return;
    }
    setRecharging(r => ({ ...r, loading: true, error: null }));
    try {
      const chargeRes = await paymentApi.charge({ userId, serviceType: 'INTERVIEW' });
      const payParams = chargeRes.data.data;
      if (!payParams) throw new Error('获取支付参数失败');
      await requestWechatPayment(payParams);
      // 支付成功：后端回调已增加一次付费配额，关闭弹窗并提示用户重新创建面试
      closeRecharge();
      toast.success('支付成功，可重新点击「开始面试」');
    } catch (err) {
      const msg = err instanceof Error ? err.message : '支付未完成';
      setRecharging(r => ({ ...r, loading: false, error: msg }));
    }
  };

  const startInterview = async () => {
    try {
      // 配额检查与扣减由后端在 POST /interviews 内部完成，前端只需发送这一个请求
      const res = await interviewApi.start({ companyType: company, position, resumeId: resumeId || undefined });
      const apiRes = res.data;

      // 业务错误：HTTP 200 但 code != 0（响应拦截器不会因业务码抛错）。
      // 例如免费额度用尽时后端返回 { code: 70000, message: '本月免费额度已用完', data: null }
      if (apiRes.code !== 0) {
        // 业务错误：统一转成中文提示，不暴露原始错误码/英文 reason
        const userMessage = toUserMessage(apiRes.code, apiRes.message);
        setError(userMessage);
        if (apiRes.code === QUOTA_ERROR_CODES.FREE_EXHAUSTED) {
          // 免费额度不足：额外弹出充值询问框
          openRecharge();
        }
        return;
      }

      const body = (apiRes.data ?? {}) as unknown as Record<string, unknown>;
      const interviewId = (body.interview as Record<string, unknown> | undefined)?.id ?? body.id;
      if (!interviewId) { setError('创建失败：未返回有效的面试ID'); return; }
      navigate(`/interviews/${interviewId}`, { state: { interview: body.interview, firstQuestion: body.firstQuestion } });
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '启动失败';
      if (msg.includes('进行中')) {
        // redirect to ongoing interview
        try {
          const listRes = await interviewApi.list({ status: 'IN_PROGRESS' });
          const ongoing = listRes.data.data.data?.[0];
          if (ongoing) { navigate(`/interviews/${ongoing.id}`); return; }
        } catch {
          /* 查找进行中面试失败则忽略，沿用顶层错误信息 */
        }
      }
      setError(msg);
    }
  };

  const handleStart = (e: FormEvent) => {
    e.preventDefault();
    setStarting(true);
    setError('');
    startInterview().finally(() => setStarting(false));
  };

  const companies = Object.entries(CompanyEnum) as [CompanyType, string][];
  const positions = Object.entries(PositionEnum) as [PositionType, string][];

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <Card>
        <CardHeader>
          <CardTitle className="text-2xl">开始面试</CardTitle>
          <CardDescription>选择目标公司和岗位，AI 面试官将为你量身定制面试问题</CardDescription>
        </CardHeader>
        <CardContent>
          {error && <p className="mb-4 text-sm text-destructive">{error}</p>}

          {loading ? (
            <div className="py-16 text-center text-muted-foreground">加载中...</div>
          ) : (
            <form onSubmit={handleStart} className="space-y-4">
              <div data-tour="interview-config" className="space-y-2">
                <Label htmlFor="company">目标公司</Label>
                <Select value={company} onValueChange={(v) => setCompany(v as CompanyType)}>
                  <SelectTrigger id="company" className="rounded-md">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {companies.map(([val, label]) => (
                      <SelectItem key={val} value={val}>{label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="position">目标岗位</Label>
                <Select value={position} onValueChange={(v) => setPosition(v as PositionType)}>
                  <SelectTrigger id="position" className="rounded-md">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {positions.map(([val, label]) => (
                      <SelectItem key={val} value={val}>{label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {resumes.length > 0 && (
                <div data-tour="interview-resume" className="space-y-2">
                  <Label htmlFor="resume">关联简历（可选）</Label>
                  <Select
                    value={resumeId || '__none__'}
                    onValueChange={(v) => setResumeId(v === '__none__' ? '' : v)}
                  >
                    <SelectTrigger id="resume" className="rounded-md">
                      <SelectValue placeholder="不使用简历" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__none__">不使用简历</SelectItem>
                      {resumes.map(r => (
                      <SelectItem key={String(r.id)} value={String(r.id)}>
                        {r.title || r.fileName || '未命名简历'} {r.isActive ? '(使用中)' : ''}
                      </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}

              {resumeError && (
                <p className="text-sm text-destructive">{resumeError}</p>
              )}

              <Button data-tour="interview-submit" type="submit" className="w-full" disabled={starting}>
                {starting ? '创建中...' : '开始面试'}
              </Button>
            </form>
          )}
        </CardContent>
      </Card>

      {/* 免费额度不足时询问是否充值 */}
      <QuotaPaymentDialog
        open={recharging.open}
        amount={SERVICE_PRICE.INTERVIEW}
        serviceType="INTERVIEW"
        loading={recharging.loading}
        error={recharging.error}
        onPay={handleRechargePay}
        onClose={closeRecharge}
      />

      {/* 新手引导 */}
      <TourOverlay tourId={TOUR_IDS.interviewStart} steps={INTERVIEW_START_TOUR_STEPS} />
    </div>
  );
}
