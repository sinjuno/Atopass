import { createBrowserRouter, Navigate, RouterProvider } from 'react-router';
import AppLayout from '@/components/common/AppLayout';
import RequireAuth from '@/components/common/RequireAuth';
import { RecordDraftProvider } from '@/hooks/useRecordDraft';
import AnalysisPage from '@/pages/analysis/AnalysisPage';
import ChatbotPage from '@/pages/chatbot/ChatbotPage';
import HomePage from '@/pages/home/HomePage';
import LoginPage from '@/pages/login/LoginPage';
import MyPage from '@/pages/mypage/MyPage';
import CalendarPage from '@/pages/record/CalendarPage';
import RecordSummaryPage from '@/pages/record/RecordSummaryPage';
import ReportPage from '@/pages/report/ReportPage';

// 화면 명세서 1. 화면 목록
const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <HomePage /> },
          { path: '/mypage', element: <MyPage /> },
          { path: '/chatbot', element: <ChatbotPage /> },
          { path: '/record/:date', element: <RecordSummaryPage /> },
          { path: '/calendar', element: <CalendarPage /> },
          { path: '/analysis', element: <AnalysisPage /> },
          { path: '/report', element: <ReportPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);

export default function App() {
  return (
    <RecordDraftProvider>
      <RouterProvider router={router} />
    </RecordDraftProvider>
  );
}
