import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import AppLayout from './layouts/AppLayout';
import AuthPage from './pages/AuthPage';
import DashboardPage from './pages/DashboardPage';
import ClaimsPage, { NewClaimPage } from './pages/ClaimsPage';
import ClaimDetailPage from './pages/ClaimDetailPage';
import PoliciesPage from './pages/PoliciesPage';
import NotificationsPage from './pages/NotificationsPage';
import { AdminProductsPage, AdminReportsPage } from './pages/AdminPages';
import { AdminSlaPage, AdminUsersPage } from './pages/AdminPages';
import ProfilePage from './pages/ProfilePage';
import AgentWorkspacePage from './pages/AgentWorkspacePage';
import { AdminAssignmentsPage, AdminAuditPage, AdminClaimTypesPage, AdminKycPage } from './pages/AdminAdvancedPages';
import AdminQueuesPage from './pages/AdminQueuesPage';
import AdminPaymentsPage from './pages/AdminPaymentsPage';

function NotFound({ title = 'Page not found' }) { return <section className="panel"><span className="eyebrow">NORTHSTAR INSURANCE</span><h1>{title}</h1><p className="muted">This page is not available for your account.</p><a href="/">Return to dashboard</a></section>; }

export default function App() {
  return <Routes>
    <Route path="/login" element={<AuthPage mode="login" />} />
    <Route path="/register" element={<AuthPage mode="register" />} />
    <Route element={<ProtectedRoute />}><Route element={<AppLayout />}>
      <Route index element={<DashboardPage />} />
      <Route path="policies" element={<ProtectedRoute roles={['CUSTOMER']} />}><Route index element={<PoliciesPage />} /></Route>
      <Route path="claims" element={<ClaimsPage />} />
      <Route path="claims/new" element={<ProtectedRoute roles={['CUSTOMER']} />}><Route index element={<NewClaimPage />} /></Route>
      <Route path="claims/:id" element={<ClaimDetailPage />} />
      <Route path="agent" element={<ProtectedRoute roles={['AGENT']} />}><Route index element={<AgentWorkspacePage />} /></Route>
      <Route path="payments" element={<ProtectedRoute roles={['CUSTOMER']} />}><Route index element={<PoliciesPage />} /></Route>
      <Route path="notifications" element={<NotificationsPage />} />
      <Route path="profile" element={<ProtectedRoute roles={['CUSTOMER']} />}><Route index element={<ProfilePage />} /></Route>
      <Route path="admin/users" element={<ProtectedRoute roles={['ADMIN']} />}><Route index element={<AdminUsersPage />} /></Route>
      <Route path="admin/assignments" element={<ProtectedRoute roles={['ADMIN']} />}><Route index element={<AdminAssignmentsPage />} /></Route>
      <Route path="admin/queues" element={<ProtectedRoute roles={['ADMIN']} />}><Route index element={<AdminQueuesPage />} /></Route>
      <Route path="admin/payments" element={<ProtectedRoute roles={['ADMIN']} />}><Route index element={<AdminPaymentsPage />} /></Route>
      <Route path="admin/kyc" element={<ProtectedRoute roles={['ADMIN']} />}><Route index element={<AdminKycPage />} /></Route>
      <Route path="admin/claim-types" element={<ProtectedRoute roles={['ADMIN']} />}><Route index element={<AdminClaimTypesPage />} /></Route>
      <Route path="admin/audit" element={<ProtectedRoute roles={['ADMIN']} />}><Route index element={<AdminAuditPage />} /></Route>
      <Route path="admin/slas" element={<ProtectedRoute roles={['ADMIN']} />}><Route index element={<AdminSlaPage />} /></Route>
      <Route path="admin/products" element={<ProtectedRoute roles={['ADMIN']} />}><Route index element={<AdminProductsPage />} /></Route>
      <Route path="admin/reports" element={<ProtectedRoute roles={['ADMIN']} />}><Route index element={<AdminReportsPage />} /></Route>
      <Route path="unauthorized" element={<NotFound title="Access not permitted" />} />
    </Route></Route>
    <Route path="*" element={<NotFound />} />
  </Routes>;
}
