import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import GlobalNav from './components/GlobalNav';
import Footer from './components/Footer';
import Home from './pages/Home';
import Login from './pages/Login';
import Register from './pages/Register';
import Resumes from './pages/Resumes';
import ResumeEdit from './pages/ResumeEdit';
import AiChat from './pages/AiChat';
import Preview from './pages/Preview';
import Templates from './pages/Templates';
import Settings from './pages/Settings';
import InterviewStart from './pages/InterviewStart';
import InterviewChat from './pages/InterviewChat';
import InterviewList from './pages/InterviewList';
import Report from './pages/Report';
import Profile from './pages/Profile';
import Questions from './pages/Questions';
import QuestionDetail from './pages/QuestionDetail';
import ApplicationsPage from './pages/ApplicationsPage';
import { Toaster } from '@/components/ui/sonner';
import './index.css';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem('accessToken');
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <GlobalNav />
      {children}
      <Footer />
    </>
  );
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<><GlobalNav /><Login /></>} />
        <Route path="/register" element={<><GlobalNav /><Register /></>} />
        <Route path="/" element={<AppLayout><Home /></AppLayout>} />
        <Route path="/resumes" element={<AppLayout><ProtectedRoute><Resumes /></ProtectedRoute></AppLayout>} />
        <Route path="/resumes/:id" element={<AppLayout><ProtectedRoute><ResumeEdit /></ProtectedRoute></AppLayout>} />
        {/* 独立 AI 对话生成简历页：全屏沉浸式，不套用 AppLayout */}
        <Route path="/resumes/:id/ai-chat" element={<ProtectedRoute><AiChat /></ProtectedRoute>} />
        {/* 别名路由：AI 生成简历成功后按 /editor/:id 跳转同样进入简历编辑器 */}
        <Route path="/editor/:id" element={<AppLayout><ProtectedRoute><ResumeEdit /></ProtectedRoute></AppLayout>} />
        <Route path="/preview/:id" element={<AppLayout><ProtectedRoute><Preview /></ProtectedRoute></AppLayout>} />
        <Route path="/templates" element={<AppLayout><ProtectedRoute><Templates /></ProtectedRoute></AppLayout>} />
        <Route path="/settings" element={<AppLayout><ProtectedRoute><Settings /></ProtectedRoute></AppLayout>} />
        <Route path="/interviews" element={<AppLayout><ProtectedRoute><InterviewList /></ProtectedRoute></AppLayout>} />
        <Route path="/reports" element={<AppLayout><ProtectedRoute><Report /></ProtectedRoute></AppLayout>} />
        <Route path="/interviews/start" element={<AppLayout><ProtectedRoute><InterviewStart /></ProtectedRoute></AppLayout>} />
        <Route path="/interviews/:id" element={<ProtectedRoute><InterviewChat /></ProtectedRoute>} />
        <Route path="/profile" element={<AppLayout><ProtectedRoute><Profile /></ProtectedRoute></AppLayout>} />
        <Route path="/questions" element={<AppLayout><ProtectedRoute><Questions /></ProtectedRoute></AppLayout>} />
        <Route path="/questions/:id" element={<AppLayout><ProtectedRoute><QuestionDetail /></ProtectedRoute></AppLayout>} />
        <Route path="/app/applications" element={<AppLayout><ProtectedRoute><ApplicationsPage /></ProtectedRoute></AppLayout>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <Toaster />
    </BrowserRouter>
  );
}

export default App;
