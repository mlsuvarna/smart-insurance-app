import { useEffect, useState } from 'react';
import client from '../api/client';

export function AdminClaimTypesPage() {
  const [types, setTypes] = useState([]);
  const [products, setProducts] = useState([]);
  const [queues, setQueues] = useState([]);
  const [message, setMessage] = useState('');
  const [editing, setEditing] = useState(null);
  async function load() {
    const [typeResult, productResult, queueResult] = await Promise.all([
      client.get('/claim-types/admin'), client.get('/products/admin'), client.get('/admin/queues', { params: { activeOnly: true } }),
    ]);
    setTypes(typeResult.data); setProducts(productResult.data); setQueues(queueResult.data);
  }
  useEffect(() => { load().catch((e) => setMessage(e.response?.data?.message || 'Unable to load claim types.')); }, []);
  async function save(event) {
    event.preventDefault(); const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const body = { productId: Number(form.get('productId')), name: form.get('name').trim(),
      queueCode: form.get('queueCode') };
    try {
      if (editing) await client.put(`/claim-types/${editing.id}`, body);
      else await client.post('/claim-types', body);
      setEditing(null); formElement.reset(); await load(); setMessage('Claim type saved.');
    } catch (e) { setMessage(e.response?.data?.message || 'Claim type could not be saved.'); }
  }
  async function toggle(type) {
    try { await client.patch(`/claim-types/${type.id}/availability`, null, { params: { active: !type.active } }); await load(); }
    catch (e) { setMessage(e.response?.data?.message || 'Claim type could not be updated.'); }
  }
  return <><section className="welcome-row"><div><span className="eyebrow">ADMINISTRATION</span><h1>Claim types</h1></div></section>{message && <p role="status" className="muted">{message}</p>}
    <form key={editing?.id || 'new'} className="panel form-row" onSubmit={save}><label>Product<select name="productId" required defaultValue={editing?.productId || ''}><option value="">Choose product</option>{products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label><label>Claim type<input name="name" required maxLength="80" defaultValue={editing?.name || ''} /></label><label>Destination queue<select name="queueCode" required defaultValue={editing?.queueCode || queues[0]?.code || ''}><option value="">Choose active queue</option>{queues.map((queue) => <option key={queue.code} value={queue.code}>{queue.name} ({queue.code})</option>)}</select></label><button className="button button-primary">{editing ? 'Update type' : 'Add type'}</button>{editing && <button type="button" className="button button-quiet" onClick={() => setEditing(null)}>Cancel edit</button>}</form>
    <section className="panel"><div className="table-wrap"><table><thead><tr><th>Type</th><th>Product</th><th>Queue</th><th>Status</th><th>Actions</th></tr></thead><tbody>{types.map((type) => <tr key={type.id}><td>{type.name}</td><td>{products.find((p) => p.id === type.productId)?.name || type.productId}</td><td>{type.queueCode}</td><td>{type.active ? 'Active' : 'Inactive'}</td><td><button className="text-button" onClick={() => setEditing(type)}>Edit</button> · <button className="text-button" onClick={() => toggle(type)}>{type.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}</tbody></table></div></section></>;
}

export function AdminKycPage() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [message, setMessage] = useState('');
  async function load() {
    const { data } = await client.get('/customers/admin/kyc', { params: { page, size: 20 } });
    setItems(data.content || []); setHasNext(!data.last);
  }
  useEffect(() => { load().catch((e) => setMessage(e.response?.data?.message || 'Unable to load KYC queue.')); }, [page]);
  async function review(item, status) {
    try { await client.post(`/customers/admin/${item.userId}/kyc-review`, null, { params: { status } }); await load(); }
    catch (e) { setMessage(e.response?.data?.message || 'KYC status could not be updated.'); }
  }
  return <><section className="welcome-row"><div><span className="eyebrow">CUSTOMER VERIFICATION</span><h1>KYC review</h1><p className="muted">Sensitive identifiers are masked; profile address is withheld from this queue.</p></div></section>{message && <p role="alert" className="error">{message}</p>}<section className="panel"><div className="table-wrap"><table><thead><tr><th>Customer</th><th>Email</th><th>Date of birth</th><th>Phone</th><th>ID</th><th>Status</th><th>Review</th></tr></thead><tbody>{items.map((item) => <tr key={item.userId}><td>{item.fullName}</td><td>{item.email}</td><td>{item.dateOfBirth || '—'}</td><td>{item.maskedPhone || '—'}</td><td>{item.maskedNationalId || '—'}</td><td>{item.kycStatus}</td><td><button className="text-button" onClick={() => review(item, 'VERIFIED')}>Verify</button> · <button className="text-button" onClick={() => review(item, 'REJECTED')}>Reject</button></td></tr>)}</tbody></table></div><Pager page={page} hasNext={hasNext} setPage={setPage} /></section></>;
}

export function AdminAssignmentsPage() {
  const [assignments, setAssignments] = useState([]);
  const [history, setHistory] = useState([]);
  const [agents, setAgents] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [message, setMessage] = useState('');
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  async function load() {
    const [assignmentResult, historyResult, agentResult, customerResult] = await Promise.all([
      client.get('/admin/assignments', { params: { page, size: 20 } }),
      client.get('/admin/assignment-history', { params: { page, size: 20 } }),
      client.get('/admin/users', { params: { role: 'AGENT', size: 100 } }),
      client.get('/admin/users', { params: { role: 'CUSTOMER', size: 100 } }),
    ]);
    setAssignments(assignmentResult.data.content || []); setHasNext(!assignmentResult.data.last);
    setHistory(historyResult.data.content || []);
    setAgents(agentResult.data.content || []); setCustomers(customerResult.data.content || []);
  }
  useEffect(() => { load().catch((e) => setMessage(e.response?.data?.message || 'Unable to load assignments.')); }, [page]);
  async function assign(event) {
    event.preventDefault(); const formElement = event.currentTarget; const form = new FormData(formElement);
    try {
      await client.post('/admin/assignments', { agentId: Number(form.get('agentId')), customerId: Number(form.get('customerId')) });
      formElement.reset(); await load(); setMessage('Assignment added.');
    } catch (e) { setMessage(e.response?.data?.message || 'Assignment could not be added.'); }
  }
  async function remove(item) {
    try { await client.delete('/admin/assignments', { params: { agentId: item.agentId, customerId: item.customerId } }); await load(); }
    catch (e) { setMessage(e.response?.data?.message || 'Assignment could not be removed.'); }
  }
  return <><section className="welcome-row"><div><span className="eyebrow">AGENT OPERATIONS</span><h1>Customer assignments</h1><p className="muted">Assignment additions and removals are retained in the audit history.</p></div></section>{message && <p role="status" className="muted">{message}</p>}<form className="panel form-row" onSubmit={assign}><label>Agent<select name="agentId" required><option value="">Select agent</option>{agents.map((a) => <option key={a.id} value={a.id}>{a.fullName} · {a.email}</option>)}</select></label><label>Customer<select name="customerId" required><option value="">Select customer</option>{customers.map((c) => <option key={c.id} value={c.id}>{c.fullName} · {c.email}</option>)}</select></label><button className="button button-primary">Assign customer</button></form><section className="panel"><h2>Current agent/customer assignments</h2><div className="table-wrap"><table><thead><tr><th>Agent</th><th>Customer</th><th>Email</th><th>Action</th></tr></thead><tbody>{assignments.map((item) => <tr key={`${item.agentId}-${item.customerId}`}><td>{item.agentName}</td><td>{item.customerName}</td><td>{item.customerEmail}</td><td><button className="text-button" onClick={() => remove(item)}>Remove assignment</button></td></tr>)}</tbody></table></div><Pager page={page} hasNext={hasNext} setPage={setPage} /></section><section className="panel"><h2>Agent/customer assignment history</h2><div className="table-wrap"><table><thead><tr><th>Time</th><th>Action</th><th>Agent</th><th>Customer</th><th>Actor ID</th></tr></thead><tbody>{history.map((item, index) => <tr key={`${item.createdAt}-${index}`}><td>{new Date(item.createdAt).toLocaleString()}</td><td>{item.action}</td><td>{item.agentName} ({item.agentId})</td><td>{item.customerName} ({item.customerId})</td><td>{item.actorId}</td></tr>)}</tbody></table></div><Pager page={page} hasNext={hasNext} setPage={setPage} /></section></>;
}

export function AdminAuditPage() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    client.get('/admin/audit', { params: { page, size: 50 } }).then(({ data }) => {
      setItems(data.content || []); setHasNext(!data.last);
    }).catch((e) => setError(e.response?.data?.message || 'Unable to load audit trail.'));
  }, [page]);
  return <><section className="welcome-row"><div><span className="eyebrow">SYSTEM AUDIT</span><h1>Audit trail</h1><p className="muted">Browse all retained audit events, newest first.</p></div></section>{error && <p className="error" role="alert">{error}</p>}<section className="panel"><div className="table-wrap"><table><thead><tr><th>Time</th><th>Actor</th><th>Event</th><th>Resource</th><th>Resource ID</th><th>Correlation</th></tr></thead><tbody>{items.map((item, index) => <tr key={`${item.createdAt}-${index}`}><td>{new Date(item.createdAt).toLocaleString()}</td><td>{item.actorId ?? 'System'}</td><td>{item.eventType}</td><td>{item.resourceType}</td><td>{item.resourceId}</td><td>{item.correlationId || '—'}</td></tr>)}</tbody></table></div><Pager page={page} hasNext={hasNext} setPage={setPage} /></section></>;
}

function Pager({ page, hasNext, setPage }) {
  return <div className="button-row pagination"><button className="button button-quiet" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button><span>Page {page + 1}</span><button className="button button-quiet" disabled={!hasNext} onClick={() => setPage((value) => value + 1)}>Next</button></div>;
}
