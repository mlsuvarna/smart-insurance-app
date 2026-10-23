import { useEffect, useState } from 'react';
import client from '../api/client';
import { useAuth } from '../context/AuthContext';
import { Empty, Status } from './DashboardPage';

export default function PoliciesPage() {
  const { user } = useAuth();
  const [policies, setPolicies] = useState([]);
  const [products, setProducts] = useState([]);
  const [payments, setPayments] = useState([]);
  const [policyPage, setPolicyPage] = useState(0);
  const [policyHasNext, setPolicyHasNext] = useState(false);
  const [paymentPage, setPaymentPage] = useState(0);
  const [paymentHasNext, setPaymentHasNext] = useState(false);
  const [paymentHistory, setPaymentHistory] = useState({});
  const [invoice, setInvoice] = useState(null);
  const [error, setError] = useState('');
  async function load() {
    const [policyResult, productResult, paymentResult] = await Promise.all([
      client.get('/policies', { params: { page: policyPage, size: 10 } }), client.get('/products'),
      client.get('/payments', { params: { page: paymentPage, size: 10 } }),
    ]);
    setPolicies(policyResult.data.content || []); setPolicyHasNext(!policyResult.data.last);
    setProducts(productResult.data); setPayments(paymentResult.data.content || []);
    setPaymentHasNext(!paymentResult.data.last);
  }
  useEffect(() => { load().catch((e) => setError(e.response?.data?.message || 'Unable to load policy information.')); }, [policyPage, paymentPage]);
  async function purchase(productId) {
    setError('');
    try { await client.post('/policies', { productId }); await load(); }
    catch (e) { setError(e.response?.data?.message || 'Policy purchase failed.'); }
  }
  async function pay(policyId) {
    try { await client.post('/payments', null, { params: { policyId } }); await load(); setError('Mock payment request created as PENDING. An administrator must record a mock outcome; this is not a real payment.'); }
    catch (e) { setError(e.response?.data?.message || 'Payment could not be recorded.'); }
  }
  async function policyAction(id, action) {
    try { await client.post(`/policies/${id}/${action}`); await load(); }
    catch (e) { setError(e.response?.data?.message || `Unable to ${action} policy.`); }
  }
  async function receipt(reference) {
    const { data } = await client.get(`/payments/${reference}/receipt`, { responseType: 'blob' });
    const url = URL.createObjectURL(data);
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${reference}-receipt.pdf`; anchor.click(); URL.revokeObjectURL(url);
  }
  async function showPaymentHistory(reference) {
    try {
      const { data } = await client.get(`/payments/${encodeURIComponent(reference)}/history`);
      setPaymentHistory((current) => ({ ...current, [reference]: data }));
    } catch (e) { setError(e.response?.data?.message || 'Payment history could not be loaded.'); }
  }
  async function viewInvoice(reference) {
    try {
      const { data } = await client.get(`/payments/${encodeURIComponent(reference)}`);
      setInvoice(data);
    } catch (e) { setError(e.response?.data?.message || 'Invoice information could not be loaded.'); }
  }
  return <>
    <section className="welcome-row"><div><span className="eyebrow">COVER & PAYMENTS</span><h1>Policies</h1><p className="muted">Explore available cover and manage your existing policies.</p></div></section>
    {error && <p role="alert" className="error">{error}</p>}
    {user?.role === 'CUSTOMER' && <section className="panel"><div className="section-heading"><div><span className="eyebrow">AVAILABLE COVER</span><h2>Explore products</h2></div></div><div className="product-grid">{products.map((product) => <article className="product-card" key={product.id}><span className="product-category">{product.category}</span><h3>{product.name}</h3><p>{product.description}</p><div className="product-price">${Number(product.annualPremium).toLocaleString()} <small>/ year</small></div><p className="muted">Coverage up to ${Number(product.coverageLimit).toLocaleString()}</p><button className="button button-secondary" onClick={() => purchase(product.id)}>Purchase policy</button></article>)}</div></section>}
    <section className="panel"><span className="eyebrow">YOUR COVER</span><h2>My policies</h2>{policies.length ? <div className="table-wrap"><table><thead><tr><th>Policy</th><th>Product</th><th>Coverage</th><th>Period</th><th>Status</th><th>Created / updated</th><th>Actions</th></tr></thead><tbody>{policies.map((policy) => <tr key={policy.id}><td>{policy.policyNumber}</td><td>{policy.productName}</td><td>${Number(policy.coverageAmount).toLocaleString()}</td><td>{policy.startDate} — {policy.endDate}</td><td><Status status={policy.status} /></td><td>{new Date(policy.createdAt).toLocaleDateString()} / {new Date(policy.updatedAt).toLocaleDateString()}</td><td><div className="inline-actions"><button className="text-button" onClick={() => pay(policy.id)}>Record premium payment</button><button className="text-button" onClick={() => policyAction(policy.id, 'renew')}>Renew</button><button className="text-button" onClick={() => policyAction(policy.id, 'cancel')}>Cancel</button></div></td></tr>)}</tbody></table></div> : <Empty title="No policies yet" detail="Choose an available product above to get started." />}<div className="button-row pagination"><button className="button button-quiet" disabled={policyPage === 0} onClick={() => setPolicyPage((value) => value - 1)}>Previous</button><span>Page {policyPage + 1}</span><button className="button button-quiet" disabled={!policyHasNext} onClick={() => setPolicyPage((value) => value + 1)}>Next</button></div></section>
    <section className="panel"><span className="eyebrow">PAYMENT HISTORY</span><h2>Recent payments</h2><p className="muted">Payments are mock records only and do not transfer funds. New requests remain pending until an administrator records a simulated outcome.</p>{payments.length ? <div className="table-wrap"><table><thead><tr><th>Reference</th><th>Policy</th><th>Amount</th><th>Status / mode</th><th>Invoice / receipt</th><th>History</th></tr></thead><tbody>{payments.map((payment) => <tr key={payment.reference}><td>{payment.reference}</td><td>{payment.policyNumber}</td><td>${Number(payment.amount).toFixed(2)}</td><td>{payment.status}<small>{payment.processingMode}</small></td><td><button className="text-button" onClick={() => viewInvoice(payment.reference)}>View invoice</button><button className="text-button" onClick={() => receipt(payment.reference)}>Download receipt</button></td><td><button className="text-button" onClick={() => showPaymentHistory(payment.reference)}>View history</button>{paymentHistory[payment.reference]?.map((entry, index) => <small key={`${entry.createdAt}-${index}`}>{entry.previousStatus || 'NEW'} → {entry.newStatus} · {new Date(entry.createdAt).toLocaleString()}</small>)}</td></tr>)}</tbody></table></div> : <p className="muted">Recorded premium payments will appear here.</p>}{invoice && <section aria-labelledby="invoice-heading" className="detail-panel"><h3 id="invoice-heading">Invoice information</h3><dl className="detail-list"><div><dt>Payment reference</dt><dd>{invoice.reference}</dd></div><div><dt>Policy</dt><dd>{invoice.policyNumber}</dd></div><div><dt>Premium amount</dt><dd>${Number(invoice.amount).toFixed(2)}</dd></div><div><dt>Status</dt><dd>{invoice.status}</dd></div><div><dt>Recorded</dt><dd>{new Date(invoice.createdAt).toLocaleString()}</dd></div></dl></section>}<div className="button-row pagination"><button className="button button-quiet" disabled={paymentPage === 0} onClick={() => setPaymentPage((value) => value - 1)}>Previous</button><span>Page {paymentPage + 1}</span><button className="button button-quiet" disabled={!paymentHasNext} onClick={() => setPaymentPage((value) => value + 1)}>Next</button></div></section>
  </>;
}
