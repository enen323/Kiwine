import { Link, useNavigate } from 'react-router-dom';
import { Sun, Moon, Send } from 'lucide-react';
import { authApi } from '../api/auth';
import { Button } from '@/components/ui/button';
import { useTheme } from '@/lib/theme';

export default function GlobalNav() {
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const token = localStorage.getItem('accessToken');
  const nickname = localStorage.getItem('nickname');

  const handleLogout = async () => {
    try { await authApi.logout(); } catch { /* 忽略登出异常 */ }
    localStorage.clear();
    navigate('/login');
  };

  return (
    <header className="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4">
        <div className="flex items-center gap-6">
        <Link to="/" className="text-lg font-semibold tracking-tight text-brand">
          PrepDeck
        </Link>
          {token && (
            <nav className="hidden items-center gap-6 text-sm md:flex">
              <Link to="/" className="text-muted-foreground transition-colors hover:text-foreground">首页</Link>
              <Link data-tour="nav-resumes" to="/resumes" className="text-muted-foreground transition-colors hover:text-foreground">我的简历</Link>
              <Link to="/templates" className="text-muted-foreground transition-colors hover:text-foreground">模板</Link>
              <Link data-tour="nav-interviews" to="/interviews" className="text-muted-foreground transition-colors hover:text-foreground">面试记录</Link>
              <Link data-tour="nav-reports" to="/reports" className="text-muted-foreground transition-colors hover:text-foreground">成长报告</Link>
              <Link to="/questions" className="text-muted-foreground transition-colors hover:text-foreground">题库</Link>
              <Link data-tour="nav-applications" to="/app/applications" className="flex items-center gap-1 text-muted-foreground transition-colors hover:text-foreground">
                <Send className="size-4" />
                投递台
              </Link>
            </nav>
          )}
        </div>

        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon" onClick={toggleTheme} aria-label="切换主题">
            {theme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
          </Button>
          {token ? (
            <>
              <Button variant="ghost" asChild>
                <Link data-tour="nav-profile" to="/profile">{nickname || '个人中心'}</Link>
              </Button>
              <Button variant="outline" onClick={handleLogout}>退出</Button>
            </>
          ) : (
            <>
              <Button variant="ghost" asChild>
                <Link to="/login">登录</Link>
              </Button>
              <Button asChild>
                <Link to="/register">注册</Link>
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
