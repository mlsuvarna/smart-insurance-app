import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import client from '../api/client';
import { useAuth } from '../context/AuthContext';

export default function DashboardPage() {
  const { user } = useAuth();
  const [claims, setClaims] = useState([]);
  const [policies, setPolicies] = useState([]);
  const [error, setError] = useState('');
  useEffect(() => {
    const isCustomer = user?.role === 'CUSTOMER';
    Promise.all([
      client.get(isCustomer ? '/claims' : user?.role === 'AGENT' ? '/agent/claims' : '/claims/queue'),
      isCustomer ? client.get('/policies', { params: { size: 5 } }) : Promise.resolve({ data: { content: [] } }),
    ]).then(([claimResult, policyResult]) => {
      setClaims(claimResult.data.content || []);
      setPolicies(policyResult.data.content || []);
    }).catch((failure) => setError(failure.response?.data?.message || 'Could not load dashboard data.'));
  }, [user]);

  return (
    <>
      <section className="welcome-row">
        <div><span className="eyebrow">YOUR WORKSPACE</span><h1>Good day, {user?.fullName?.split(' ')[0]}</h1><p className="muted">{user?.role === 'CUSTOMER' ? 'Your cover and claims, all in one place.' : 'Review your assigned work and keep cases moving.'}</p></div>
        {user?.role === 'CUSTOMER' && <Link className="button button-primary" to="/claims/new">Start a claim <span aria-hidden="true">↗</span></Link>}
      </section>
      {error && <p className="error" role="alert">{error}</p>}
      <section className="stat-grid" aria-label="Account summary">
        <Stat label={user?.role === 'CUSTOMER' ? 'Active policies' : 'Assigned claims'} value={user?.role === 'CUSTOMER' ? policies.filter((p) => p.status === 'ACTIVE').length : claims.length} icon="◇" />
        <Stat label="Open claims" value={claims.filter((c) => !['SETTLED', 'REJECTED', 'WITHDRAWN'].includes(c.status)).length} icon="◷" />
        <Stat label="Needs attention" value={claims.filter((c) => c.status === 'INFO_REQUESTED' || c.overdue).length} icon="!" />
      </section>
      <section className="panel">
        <div className="section-heading"><div><span className="eyebrow">LATEST ACTIVITY</span><h2>{user?.role === 'CUSTOMER' ? 'Recent claims' : 'Your claim queue'}</h2></div><Link to="/claims">View all <span aria-hidden="true">→</span></Link></div>
        {claims.length === 0 ? <Empty title="No claims yet" detail={user?.role === 'CUSTOMER' ? 'When you submit a claim, it will appear here.' : 'There are no claims assigned to your queue.'} /> : <ClaimTable claims={claims.slice(0, 5)} />}
      </section>
    </>
  );
}

export function ClaimTable({ claims }) {
  return <div className="table-wrap"><table><thead><tr><th>Claim</th><th>Type</th><th>Submitted</th><th>Status</th><th /></tr></thead><tbody>{claims.map((claim) => <tr key={claim.id}><td><strong>{claim.claimNumber}</strong></td><td>{claim.claimType}</td><td>{new Date(claim.createdAt).toLocaleDateString()}</td><td><Status status={claim.status} /></td><td><Link to={`/claims/${claim.id}`}>Open</Link></td></tr>)}</tbody></table></div>;
}

export function Status({ status }) { return <span className={`status status-${status?.toLowerCase()}`}>{status?.replaceAll('_', ' ')}</span>; }
export function Stat({ label, value, icon }) { return <article className="stat-card"><span className="stat-icon" aria-hidden="true">{icon}</span><p>{label}</p><strong>{value}</strong></article>; }
export function Empty({ title, detail }) { return <div className="empty"><span aria-hidden="true">◇</span><strong>{title}</strong><p>{detail}</p></div>; }
