import { useEffect, useState } from 'react';
import client from '../api/client';

export default function AdminPaymentsPage() {
  const [payments, setPayments] = useState([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  async function load() {
    const { data } = await client.get('/admin/payments', { params: { page, size: 20 } });
    setPayments(data.content || []);
    setHasNext(!data.last);
  }
  useEffect(() => { load().catch((e) => setError(e.response?.data?.message || 'Unable to load payment records.')); }, [page]);
  async function process(reference, outcome) {
    try {
      await client.post(`/admin/payments/${encodeURIComponent(reference)}/mock-processing`, null, { params: { outcome } });
      await load(); setMessage(`Mock processor recorded ${outcome}.`); setError('');
    } catch (e) { setError(e.response?.data?.message || 'Payment processing failed.'); }
  }
  async function refund(reference) {
    try {
      await client.post(`/admin/payments/${encodeURIComponent(reference)}/refund`);
      await load(); setMessage('Mock refund recorded.'); setError('');
    } catch (e) { setError(e.response?.data?.message || 'Payment refund failed.'); }
  }
  return <>
    <section className="welcome-row"><div><span className="eyebrow">NON-PRODUCTION PAYMENT OPERATIONS</span><h1>Mock premium payments</h1><p className="muted">This internal simulator is not a payment gateway and must not be used to confirm real funds. Only administrators can record mock outcomes.</p></div></section>
    {error && <p className="error" role="alert">{error}</p>}{message && <p className="success" role="status">{message}</p>}
    <section className="panel"><div className="table-wrap"><table><thead><tr><th>Reference</th><th>Policy</th><th>Amount</th><th>Status</th><th>Mode</th><th>Updated</th><th>Operations</th></tr></thead><tbody>{payments.map((payment) => <tr key={payment.reference}><td>{payment.reference}</td><td>{payment.policyNumber}</td><td>{Number(payment.amount).toFixed(2)}</td><td>{payment.status}</td><td>{payment.processingMode}</td><td>{new Date(payment.updatedAt).toLocaleString()}</td><td>{payment.status === 'PENDING' && <><button className="text-button" onClick={() => process(payment.reference, 'SUCCESS')}>Mock success</button> · <button className="text-button" onClick={() => process(payment.reference, 'FAILED')}>Mock failure</button></>}{payment.status === 'SUCCESS' && <button className="text-button" onClick={() => refund(payment.reference)}>Mock refund</button>}</td></tr>)}</tbody></table></div>
      <div className="button-row pagination"><button className="button button-quiet" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button><span>Page {page + 1}</span><button className="button button-quiet" disabled={!hasNext} onClick={() => setPage((value) => value + 1)}>Next</button></div>
    </section>
  </>;
}
