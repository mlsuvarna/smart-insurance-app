import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const isCustomer = user?.role === 'CUSTOMER';
  const isAdmin = user?.role === 'ADMIN';
  function signOut() { logout(); navigate('/login'); }

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink className="brand" to="/"><span className="brand-mark">SC</span><span>Smart Claim</span></NavLink>
        <nav aria-label="Main navigation" className="main-nav">
          {isCustomer && <><NavLink to="/">Overview</NavLink><NavLink to="/policies">Policies</NavLink><NavLink to="/claims">Claims</NavLink><NavLink to="/payments">Payments</NavLink></>}
          {user?.role === 'AGENT' && <><NavLink to="/">Overview</NavLink><NavLink to="/agent">Customers</NavLink><NavLink to="/claims">Claims</NavLink></>}
          {user?.role === 'CLAIMS_OFFICER' && <><NavLink to="/">Overview</NavLink><NavLink to="/claims">Claim queue</NavLink></>}
          {isAdmin && <><NavLink to="/">Overview</NavLink><NavLink to="/admin/users">Users</NavLink><NavLink to="/admin/assignments">Assignments</NavLink><NavLink to="/admin/queues">Claim queues</NavLink><NavLink to="/admin/payments">Mock payments</NavLink><NavLink to="/admin/kyc">KYC</NavLink><NavLink to="/admin/products">Products</NavLink><NavLink to="/admin/claim-types">Claim types</NavLink><NavLink to="/admin/slas">SLAs</NavLink><NavLink to="/admin/reports">Reports</NavLink><NavLink to="/admin/audit">Audit</NavLink></>}
          {isCustomer && <NavLink to="/profile">Profile</NavLink>}
          <NavLink to="/notifications">Notifications</NavLink>
        </nav>
        <div className="account"><span>{user?.fullName} <small>{user?.role?.replace('_', ' ')}</small></span><button className="button button-quiet" onClick={signOut}>Sign out</button></div>
      </header>
      <main className="content"><Outlet /></main>
      <footer className="footer">Smart Claim · Secure claims workspace</footer>
    </div>
  );
}
