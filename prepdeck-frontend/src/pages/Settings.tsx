import { useState, useEffect } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { userApi } from '../api/user';
import type { UserAISettings } from '../types';
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { toast } from 'sonner';
import { resetAllTours } from '@/stores/tour-store';

const PROVIDERS: { value: string; label: string }[] = [
  { value: 'openai', label: 'OpenAI' },
  { value: 'anthropic', label: 'Anthropic' },
  { value: 'gemini', label: 'Google Gemini' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'custom', label: '自定义' },
];

export default function Settings() {
  const navigate = useNavigate();
  const [provider, setProvider] = useState('openai');
  const [baseUrl, setBaseUrl] = useState('');
  const [model, setModel] = useState('');
  const [apiKey, setApiKey] = useState(''); // 仅本地暂存，不回传后端
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    userApi.getSettings()
      .then(res => {
        const s: UserAISettings = res.data.data ?? {};
        if (s.provider) setProvider(s.provider);
        if (s.baseUrl) setBaseUrl(s.baseUrl);
        if (s.model) setModel(s.model);
      })
      .catch(() => setError('加载设置失败'))
      .finally(() => setLoading(false));
  }, []);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      // 仅回传 provider/baseUrl/model；apiKey 不落库
      await userApi.updateSettings({ provider, baseUrl, model });
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '保存失败';
      setError(msg);
    } finally {
      setSaving(false);
    }
  };

  /** 清除所有引导的「已看过」标记，下次进入各页面会重新自动展示 */
  const handleReplayTours = () => {
    resetAllTours();
    toast.success('新手引导已重置，下次进入各页面会重新展示');
  };

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      {loading ? (
        <div className="py-16 text-center text-muted-foreground">加载中...</div>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle className="text-2xl">AI 设置</CardTitle>
            <CardDescription>配置你的 AI 服务商与模型。API Key 仅保存在本机，不会上传服务器。</CardDescription>
          </CardHeader>
          <CardContent>
            {error && <p className="mb-4 text-sm text-destructive">{error}</p>}
            {saved && (
              <div className="mb-4 rounded-full bg-brand/10 px-4 py-2 text-center text-sm text-brand">保存成功</div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="provider">服务商</Label>
                <Select value={provider} onValueChange={setProvider}>
                  <SelectTrigger id="provider" className="rounded-md">
                    <SelectValue placeholder="选择服务商" />
                  </SelectTrigger>
                  <SelectContent>
                    {PROVIDERS.map(p => (
                      <SelectItem key={p.value} value={p.value}>{p.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="baseUrl">Base URL</Label>
                <Input
                  id="baseUrl"
                  type="text"
                  placeholder="https://api.openai.com/v1"
                  value={baseUrl}
                  onChange={e => setBaseUrl(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="model">模型</Label>
                <Input
                  id="model"
                  type="text"
                  placeholder="如：gpt-4o"
                  value={model}
                  onChange={e => setModel(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="apikey">API Key（可选，仅本机保存）</Label>
                <Input
                  id="apikey"
                  type="password"
                  placeholder="sk-..."
                  value={apiKey}
                  onChange={e => setApiKey(e.target.value)}
                />
                <p className="text-xs text-muted-foreground">API Key 不会发送到简历后端，请放心填写。</p>
              </div>

              <div className="flex gap-3">
                <Button type="button" variant="ghost" onClick={() => navigate('/profile')}>
                  返回
                </Button>
                <Button type="submit" disabled={saving}>
                  {saving ? '保存中...' : '保存设置'}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {!loading && (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle className="text-xl">新手引导</CardTitle>
            <CardDescription>忘记功能怎么用了？重置后可以再看一遍分步引导。</CardDescription>
          </CardHeader>
          <CardContent>
            <Button type="button" variant="outline" onClick={handleReplayTours}>
              重新观看新手引导
            </Button>
            <p className="mt-2 text-xs text-muted-foreground">
              引导仅在桌面端（窗口宽度 ≥ 768px）展示。
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
