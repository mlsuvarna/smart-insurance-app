import { useEffect, useState } from 'react';
import client from '../api/client';

export function AdminProductsPage() {
  const [message, setMessage] = useState('');
  const [products, setProducts] = useState([]);
  const [editing, setEditing] = useState(null);
  async function load() { const { data } = await client.get('/products/admin'); setProducts(data); }
  useEffect(() => { load().catch((e) => setMessage(e.response?.data?.message || 'Unable to load products.')); }, []);
  async function submit(event) {
    event.preventDefault(); const formElement = event.currentTarget; const form = new FormData(formElement);
    try {
      const body = { name: form.get('name'), category: form.get('category'), description: form.get('description'), annualPremium: Number(form.get('annualPremium')), coverageLimit: Number(form.get('coverageLimit')) };
      if (editing) await client.put(`/products/${editing.id}`, body);
      else await client.post('/products', body);
      formElement.reset(); setEditing(null); setMessage(editing ? 'Product updated.' : 'Product created.'); await load();
    } catch (e) { setMessage(e.response?.data?.message || 'Product could not be created.'); }
  }
  async function toggle(product) {
    try { await client.patch(`/products/${product.id}/availability`, null, { params: { active: !product.active } }); await load(); }
    catch (e) { setMessage(e.response?.data?.message || 'Product could not be updated.'); }
  }
  return <><section className="welcome-row"><div><span className="eyebrow">ADMINISTRATION</span><h1>Insurance products</h1></div></section>{message && <p className="muted" role="status">{message}</p>}<section className="panel"><h2>{editing ? 'Update product' : 'Create product'}</h2><form className="form-stack admin-form" key={editing?.id || 'new'} onSubmit={submit}><label>Name<input name="name" required maxLength="120" defaultValue={editing?.name || ''} /></label><label>Category<input name="category" required maxLength="40" defaultValue={editing?.category || ''} /></label><label>Description<textarea name="description" required maxLength="2000" defaultValue={editing?.description || ''} /></label><div className="form-row"><label>Annual premium<input name="annualPremium" type="number" min="0.01" step="0.01" required defaultValue={editing?.annualPremium || ''} /></label><label>Coverage limit<input name="coverageLimit" type="number" min="0.01" step="0.01" required defaultValue={editing?.coverageLimit || ''} /></label></div><div className="button-row"><button className="button button-primary">{editing ? 'Update product' : 'Save product'}</button>{editing && <button type="button" className="button button-quiet" onClick={() => setEditing(null)}>Cancel</button>}</div></form></section><section className="panel"><h2>Catalog</h2><div className="table-wrap"><table><thead><tr><th>Product</th><th>Category</th><th>Annual premium</th><th>Coverage</th><th>Available</th><th>Actions</th></tr></thead><tbody>{products.map((p) => <tr key={p.id}><td>{p.name}</td><td>{p.category}</td><td>${p.annualPremium}</td><td>${p.coverageLimit}</td><td>{p.active ? 'Yes' : 'No'}</td><td><button className="text-button" onClick={() => setEditing(p)}>Edit</button> · <button className="text-button" onClick={() => toggle(p)}>{p.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}</tbody></table></div></section></>;
}

export function AdminReportsPage() {
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [range, setRange] = useState(null);
  const [products, setProducts] = useState([]);
  const [claimTypes, setClaimTypes] = useState([]);
  const [extraReports, setExtraReports] = useState({});
  useEffect(() => { client.get('/products/admin').then(({ data }) => setProducts(data)).catch((e) => setError(e.response?.data?.message || 'Unable to load product filters.')); }, []);
  async function loadClaimTypes(productId) {
    try {
      const { data } = await client.get('/claim-types/admin');
      setClaimTypes(productId ? data.filter((type) => String(type.productId) === String(productId)) : data);
    }
    catch (e) { setError(e.response?.data?.message || 'Unable to load claim-type filters.'); }
  }
  useEffect(() => { loadClaimTypes(''); }, []);
  async function load(event) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    const selected = { from: form.get('from'), to: form.get('to'), productId: form.get('productId') || undefined,
      claimType: form.get('claimType') || undefined, status: form.get('status') || undefined };
    try {
      const [summary, topCustomers, monthly, productStats] = await Promise.all([
        client.get('/admin/reports/summary', { params: selected }),
        client.get('/admin/reports/top-customers', { params: selected }),
        client.get('/admin/reports/monthly-revenue', { params: selected }),
        client.get('/admin/reports/products', { params: selected }),
      ]);
      setResult(summary.data); setExtraReports({ topCustomers: topCustomers.data, monthly: monthly.data, products: productStats.data }); setRange(selected); setError('');
    }
    catch (e) { setError(e.response?.data?.message || 'Report could not be generated.'); }
  }
  async function download(path, filename) {
    try { const { data } = await client.get(path, { params: range, responseType: 'blob' }); const url = URL.createObjectURL(data); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000); }
    catch (e) { setError(e.response?.data?.message || 'Report download failed.'); }
  }
  return <><section className="welcome-row"><div><span className="eyebrow">BUSINESS INSIGHTS</span><h1>Claims report</h1><p className="muted">Metrics are calculated from live MySQL records. Claim summary supports date, product, claim type and status filters.</p></div></section><form className="panel form-row report-filter" onSubmit={load}><label>From<input type="date" name="from" required /></label><label>To<input type="date" name="to" required /></label><label>Product<select name="productId" defaultValue="" onChange={(e) => loadClaimTypes(e.target.value)}><option value="">All products</option>{products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label><label>Claim type<select name="claimType" defaultValue=""><option value="">All types</option>{claimTypes.map((type) => <option key={type.id} value={type.name}>{type.name}</option>)}</select></label><label>Status<select name="status" defaultValue=""><option value="">All statuses</option>{['SUBMITTED','UNDER_REVIEW','INFO_REQUESTED','APPROVED','REJECTED','SETTLED','WITHDRAWN'].map((status) => <option key={status}>{status}</option>)}</select></label><button className="button button-primary">Generate report</button></form>{error && <p className="error" role="alert">{error}</p>}{result && <><section className="panel"><div className="section-heading"><h2>Filtered claims summary</h2><div className="button-row"><button className="button button-secondary" disabled={!range} onClick={() => download('/admin/reports/summary.csv', 'claims-report.csv')}>CSV</button><button className="button button-secondary" disabled={!range} onClick={() => download('/admin/reports/summary.pdf', 'claims-report.pdf')}>PDF</button></div></div><dl className="detail-list">{Object.entries(result).map(([key, value]) => <div key={key}><dt>{key.replaceAll('_', ' ')}</dt><dd>{String(value)}</dd></div>)}</dl></section>
    <ReportTable title="Top 10 customers by premium" items={extraReports.topCustomers}
      onDownload={() => download('/admin/reports/top-customers.csv', 'top-customers.csv')} />
    <ReportTable title="Monthly premium revenue" items={extraReports.monthly}
      onDownload={() => download('/admin/reports/monthly-revenue.csv', 'monthly-revenue.csv')} />
    <ReportTable title="Product statistics (approximate margin)" items={extraReports.products}
      onDownload={() => download('/admin/reports/products.csv', 'product-statistics.csv')} />
  </>}</>;
}

function ReportTable({ title, items = [], onDownload }) {
  const columns = items.length ? Object.keys(items[0]) : [];
  return <section className="panel"><div className="section-heading"><h2>{title}</h2><button className="button button-secondary" onClick={onDownload}>CSV</button></div>{items.length ? <div className="table-wrap"><table><thead><tr>{columns.map((column) => <th key={column}>{column.replaceAll('_', ' ')}</th>)}</tr></thead><tbody>{items.map((item, index) => <tr key={index}>{columns.map((column) => <td key={column}>{String(item[column])}</td>)}</tr>)}</tbody></table></div> : <p className="muted">No records for the selected range.</p>}</section>;
}

export function AdminUsersPage() {
  const [users, setUsers] = useState([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [message, setMessage] = useState('');
  async function load() {
    const { data } = await client.get('/admin/users', { params: { page, size: 20 } });
    setUsers(data.content || []); setHasNext(!data.last);
  }
  useEffect(() => { load().catch((e) => setMessage(e.response?.data?.message || 'Unable to load users.')); }, [page]);
  async function updateRole(user, role) {
    try { await client.put(`/admin/users/${user.id}/role`, { role }); await load(); setMessage('Role updated.'); }
    catch (e) { setMessage(e.response?.data?.message || 'Role update failed.'); }
  }
  async function toggle(user) {
    try { await client.patch(`/admin/users/${user.id}/activation`, null, { params: { active: !user.active } }); await load(); }
    catch (e) { setMessage(e.response?.data?.message || 'Account update failed.'); }
  }
  return <><section className="welcome-row"><div><span className="eyebrow">ACCESS CONTROL</span><h1>User management</h1><p className="muted">Assign roles to verified accounts; public registration remains customer-only.</p></div></section>{message && <p className="muted" role="status">{message}</p>}<section className="panel"><div className="table-wrap"><table><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Account</th></tr></thead><tbody>{users.map((user) => <tr key={user.id}><td>{user.fullName}</td><td>{user.email}</td><td><select aria-label={`Role for ${user.email}`} value={user.role} onChange={(e) => updateRole(user, e.target.value)}>{['CUSTOMER', 'AGENT', 'CLAIMS_OFFICER', 'ADMIN'].map((role) => <option key={role}>{role}</option>)}</select></td><td>{user.active ? 'Active' : 'Inactive'}</td><td><button className="text-button" onClick={() => toggle(user)}>{user.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}</tbody></table></div><div className="button-row pagination"><button className="button button-quiet" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button><span>Page {page + 1}</span><button className="button button-quiet" disabled={!hasNext} onClick={() => setPage((value) => value + 1)}>Next</button></div></section></>;
}

export function AdminSlaPage() {
  const [items, setItems] = useState([]);
  const [message, setMessage] = useState('');
  async function load() { const { data } = await client.get('/admin/slas'); setItems(data); }
  useEffect(() => { load().catch((e) => setMessage(e.response?.data?.message || 'Unable to load SLA settings.')); }, []);
  async function submit(event) {
    event.preventDefault(); const formElement = event.currentTarget; const form = new FormData(formElement);
    try { await client.post('/admin/slas', { claimType: form.get('claimType'), durationDays: Number(form.get('durationDays')), active: true }); formElement.reset(); await load(); setMessage('SLA configuration saved.'); }
    catch (e) { setMessage(e.response?.data?.message || 'SLA configuration failed.'); }
  }
  return <><section className="welcome-row"><div><span className="eyebrow">SERVICE LEVELS</span><h1>Claim SLA configuration</h1><p className="muted">Calendar-day review windows pause while additional information is requested.</p></div></section>{message && <p className="muted" role="status">{message}</p>}<section className="panel"><h2>Add or update claim type SLA</h2><form className="form-row" onSubmit={submit}><label>Claim type<input name="claimType" required maxLength="80" /></label><label>Duration (calendar days)<input name="durationDays" type="number" min="1" required /></label><button className="button button-primary">Save SLA</button></form></section><section className="panel"><h2>Configured durations</h2><div className="table-wrap"><table><thead><tr><th>Claim type</th><th>Duration</th><th>Status</th></tr></thead><tbody>{items.map((item) => <tr key={item.id}><td>{item.claimType}</td><td>{item.durationDays} days</td><td>{item.active ? 'Active' : 'Inactive'}</td></tr>)}</tbody></table></div></section></>;
}
