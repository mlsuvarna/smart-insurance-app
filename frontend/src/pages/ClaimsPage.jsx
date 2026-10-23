import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import client from '../api/client';
import { useAuth } from '../context/AuthContext';
import { ClaimTable, Empty } from './DashboardPage';

export default function ClaimsPage() {
  const { user } = useAuth();
  const [claims, setClaims] = useState([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [status, setStatus] = useState('');
  const [overdue, setOverdue] = useState('');
  const [queues, setQueues] = useState([]);
  const [queueCode, setQueueCode] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    if (user?.role === 'CLAIMS_OFFICER' || user?.role === 'ADMIN') {
      client.get('/claims/queues').then(({ data }) => setQueues(data))
        .catch((failure) => setError(failure.response?.data?.message || 'Unable to load permitted queues.'));
    }
  }, [user]);
  useEffect(() => {
    const params = { page, size: 20 };
    if (user?.role === 'CLAIMS_OFFICER' || user?.role === 'ADMIN') {
      if (status) params.status = status;
      if (overdue) params.overdue = overdue;
      if (queueCode) params.queueCode = queueCode;
    }
    client.get(user?.role === 'CUSTOMER' ? '/claims' : user?.role === 'AGENT' ? '/agent/claims' : '/claims/queue', { params })
      .then(({ data }) => { setClaims(data.content || []); setHasNext(!data.last); })
      .catch((failure) => setError(failure.response?.data?.message || 'Unable to load claims.'));
  }, [user, page, status, overdue, queueCode]);
  return <>
    <section className="welcome-row"><div><span className="eyebrow">CLAIM MANAGEMENT</span><h1>{user?.role === 'CUSTOMER' ? 'Your claims' : 'Claim queue'}</h1><p className="muted">Track status, review documents and follow the case timeline.</p></div>{user?.role === 'CUSTOMER' && <Link to="/claims/new" className="button button-primary">Submit a claim</Link>}</section>
    {error && <p className="error" role="alert">{error}</p>}
    {(user?.role === 'CLAIMS_OFFICER' || user?.role === 'ADMIN') && <section className="panel form-row"><label>Queue<select value={queueCode} onChange={(e) => { setPage(0); setQueueCode(e.target.value); }}><option value="">All permitted queues</option>{queues.map((queue) => <option key={queue.code} value={queue.code}>{queue.name} ({queue.code})</option>)}</select></label><label>Status filter<select value={status} onChange={(e) => { setPage(0); setStatus(e.target.value); }}><option value="">All permitted statuses</option>{['SUBMITTED','UNDER_REVIEW','INFO_REQUESTED','APPROVED','REJECTED','SETTLED','WITHDRAWN'].map((value) => <option key={value}>{value}</option>)}</select></label><label>SLA filter<select value={overdue} onChange={(e) => { setPage(0); setOverdue(e.target.value); }}><option value="">All</option><option value="true">Overdue</option><option value="false">Not overdue / paused / closed</option></select></label></section>}
    <section className="panel">{claims.length ? <ClaimTable claims={claims} /> : <Empty title="No claims found" detail="Claims you can access will appear here." />}
      <div className="button-row pagination"><button className="button button-quiet" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button><span>Page {page + 1}</span><button className="button button-quiet" disabled={!hasNext} onClick={() => setPage((value) => value + 1)}>Next</button></div>
    </section>
  </>;
}

export function NewClaimPage() {
  const [policies, setPolicies] = useState([]);
  const [types, setTypes] = useState([]);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => { client.get('/policies', { params: { size: 100 } }).then(({ data }) => setPolicies((data.content || []).filter((p) => p.status === 'ACTIVE'))).catch((e) => setMessage(e.response?.data?.message || 'Unable to load policies.')); }, []);
  async function loadTypes(policyId) {
    const policy = policies.find((item) => String(item.id) === policyId);
    if (!policy) { setTypes([]); return; }
    try { const { data } = await client.get('/claim-types', { params: { productId: policy.productId } }); setTypes(data); }
    catch (e) { setMessage(e.response?.data?.message || 'Unable to load claim types.'); }
  }
  async function submit(event) {
    event.preventDefault(); setBusy(true); setMessage('');
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    try {
      const { data } = await client.post('/claims', {
        policyId: Number(form.get('policyId')), claimType: form.get('claimType'),
        incidentDate: form.get('incidentDate'), description: form.get('description'),
        claimedAmount: Number(form.get('claimedAmount')),
      });
      formElement.reset(); setTypes([]);
      const file = form.get('document');
      if (file?.size) {
        const body = new FormData(); body.append('file', file);
        try {
          await client.post(`/claims/${data.id}/documents`, body, { headers: { 'Content-Type': 'multipart/form-data' } });
        } catch (uploadError) {
          setMessage(`Claim ${data.claimNumber} was submitted, but the document upload failed: ${uploadError.response?.data?.message || 'please open the claim and retry.'}`);
          return;
        }
      }
      setMessage(`Claim ${data.claimNumber} submitted successfully.`);
    } catch (e) { setMessage(e.response?.data?.message || 'Claim submission failed.'); }
    finally { setBusy(false); }
  }
  return <>
    <section className="welcome-row"><div><span className="eyebrow">NEW CLAIM</span><h1>Tell us what happened</h1><p className="muted">Complete the details below. You can follow progress from your claims page.</p></div></section>
    <form className="panel form-stack claim-form" onSubmit={submit}>
      <label>Policy<select name="policyId" required onChange={(event) => loadTypes(event.target.value)}><option value="">Choose an active policy</option>{policies.map((p) => <option value={p.id} key={p.id}>{p.policyNumber} · {p.productName}</option>)}</select></label>
      <label>Claim type<select name="claimType" required disabled={!types.length}><option value="">Choose a claim type</option>{types.map((type) => <option key={type.id} value={type.name}>{type.name}</option>)}</select></label>
      <div className="form-row"><label>Incident date<input name="incidentDate" type="date" max={new Date().toISOString().slice(0, 10)} required /></label><label>Claimed amount<input name="claimedAmount" type="number" min="0.01" step="0.01" required /></label></div>
      <label>What happened?<textarea name="description" rows="5" maxLength="4000" required /></label>
      <label>Supporting document <span className="muted">(PDF, JPG, PNG, or DOCX; up to 10 MB)</span><input name="document" type="file" accept=".pdf,.jpg,.jpeg,.png,.docx" /></label>
      {message && <p className={message.includes('successfully') ? 'success' : 'error'} role="status">{message}</p>}
      <button className="button button-primary" disabled={busy || !policies.length}>{busy ? 'Submitting…' : 'Submit claim'}</button>
    </form>
  </>;
}
