import { useState, useEffect } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { userApi } from '../api/user';
import type { UserVO } from '../types';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { QuotaSummary } from '@/components/quota/quota-summary';
import { TourOverlay } from '@/components/tour/tour-overlay';
import { useTourTrigger } from '@/hooks/use-tour';
import { TOUR_IDS, PROFILE_TOUR_STEPS } from '@/lib/tour-steps';

export default function Profile() {
  const navigate = useNavigate();
  const [user, setUser] = useState<UserVO | null>(null);
  const [nickname, setNickname] = useState('');
  const [targetPosition, setTargetPosition] = useState('');
  const [targetCompany, setTargetCompany] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    userApi.getMe()
      .then(res => {
        const u = res.data.data;
        setUser(u);
        setNickname(u.nickname);
        setTargetPosition(u.targetPosition || '');
        setTargetCompany(u.targetCompany || '');
      })
      .catch(() => navigate('/login'))
      .finally(() => setLoading(false));
  }, [navigate]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const res = await userApi.updateProfile({ nickname, targetPosition, targetCompany });
      setUser(res.data.data);
      localStorage.setItem('nickname', res.data.data.nickname);
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch { alert('保存失败'); }
    finally { setSaving(false); }
  };

  // 新手引导：解释免费额度口径与每月重置规则
  useTourTrigger(TOUR_IDS.profile, PROFILE_TOUR_STEPS.length, { ready: !loading && !!user });

  if (loading) return <div className="mx-auto max-w-2xl px-4 py-8"><div className="py-16 text-center text-muted-foreground">加载中...</div></div>;

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <Card>
        <CardHeader>
          <CardTitle className="text-2xl">个人中心</CardTitle>
          <CardDescription>管理你的个人信息和求职目标</CardDescription>
        </CardHeader>
        <CardContent>
          {saved && (
            <div className="mb-4 rounded-full bg-brand/10 px-4 py-2 text-center text-sm text-brand">保存成功</div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="phone">手机号</Label>
              <Input id="phone" type="text" value={user?.phone || ''} disabled className="opacity-50" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="nickname">昵称</Label>
              <Input
                id="nickname"
                type="text"
                value={nickname}
                onChange={e => setNickname(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="targetPosition">目标岗位</Label>
              <Input
                id="targetPosition"
                type="text"
                placeholder="如：后端开发"
                value={targetPosition}
                onChange={e => setTargetPosition(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="targetCompany">目标公司</Label>
              <Input
                id="targetCompany"
                type="text"
                placeholder="如：字节跳动"
                value={targetCompany}
                onChange={e => setTargetCompany(e.target.value)}
              />
            </div>
            <div className="flex gap-3">
              <Button type="button" variant="ghost" onClick={() => navigate('/settings')}>
                AI 设置
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? '保存中...' : '保存'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card data-tour="quota-summary" className="mt-6">
        <CardHeader>
          <CardTitle className="text-xl">我的配额</CardTitle>
          <CardDescription>每月免费次数与付费额度余额</CardDescription>
        </CardHeader>
        <CardContent>
          {user ? (
            <QuotaSummary userId={user.id} />
          ) : (
            <p className="text-sm text-muted-foreground">加载中…</p>
          )}
        </CardContent>
      </Card>

      {/* 新手引导 */}
      <TourOverlay tourId={TOUR_IDS.profile} steps={PROFILE_TOUR_STEPS} />
    </div>
  );
}
