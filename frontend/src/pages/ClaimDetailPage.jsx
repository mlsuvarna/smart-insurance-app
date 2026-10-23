import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import client from '../api/client';
import { useAuth } from '../context/AuthContext';
import { Status } from './DashboardPage';

export default function ClaimDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const [claim, setClaim] = useState(null);
  const [history, setHistory] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [notes, setNotes] = useState([]);
  const [comments, setComments] = useState([]);
  const [commentText, setCommentText] = useState('');
  const [officers, setOfficers] = useState([]);
  const [settlements, setSettlements] = useState([]);
  const [targetOfficer, setTargetOfficer] = useState('');
  const [remarks, setRemarks] = useState('');
  const [amount, setAmount] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const isOfficer = ['CLAIMS_OFFICER', 'ADMIN'].includes(user?.role);
  const isCustomer = user?.role === 'CUSTOMER';
  const isAgent = user?.role === 'AGENT';

  async function refresh() {
    const [claimResponse, historyResponse, documentResponse, notesResponse, settlementResponse, commentsResponse] = await Promise.all([
      client.get(`/claims/${id}`), client.get(`/claims/${id}/history`), client.get(`/claims/${id}/documents`),
      user?.role === 'CUSTOMER' ? Promise.resolve({ data: [] }) : client.get(`/claims/${id}/notes`),
      client.get(`/claims/${id}/settlements`),
      client.get(`/claims/${id}/comments`),
    ]);
    setClaim(claimResponse.data); setHistory(historyResponse.data); setDocuments(documentResponse.data);
    setNotes(notesResponse.data); setSettlements(settlementResponse.data); setComments(commentsResponse.data);
  }
  useEffect(() => { refresh().catch((e) => setMessage(e.response?.data?.message || 'Could not load claim details.')); }, [id, user]);
  useEffect(() => {
    if (isOfficer && claim?.queueCode) client.get('/claims/officers', { params: { size: 100, queueCode: claim.queueCode } }).then(({ data }) => setOfficers(data.content || []))
      .catch((e) => setMessage(e.response?.data?.message || 'Unable to load claims officers.'));
  }, [isOfficer, claim?.queueCode]);

  async function action(path, body) {
    setBusy(true); setMessage('');
    try { await client.post(`/claims/${id}/${path}`, body); setRemarks(''); setAmount(''); await refresh(); setMessage('Claim updated. The customer timeline has been recorded.'); }
    catch (e) { setMessage(e.response?.data?.message || 'Claim action failed.'); }
    finally { setBusy(false); }
  }
  async function upload(event) {
    event.preventDefault(); const formElement = event.currentTarget; const file = new FormData(formElement).get('file');
    if (!file?.size) return;
    const data = new FormData(); data.append('file', file); setBusy(true);
    try { await client.post(`/claims/${id}/documents`, data, { headers: { 'Content-Type': 'multipart/form-data' } }); await refresh(); setMessage('Document uploaded.'); formElement.reset(); }
    catch (e) { setMessage(e.response?.data?.message || 'Upload failed.'); }
    finally { setBusy(false); }
  }
  async function addComment(event) {
    event.preventDefault(); setBusy(true);
    try {
      await client.post(`/claims/${id}/comments`, { comment: commentText });
      setCommentText(''); await refresh(); setMessage('Comment added.');
    } catch (e) { setMessage(e.response?.data?.message || 'Comment could not be added.'); }
    finally { setBusy(false); }
  }
  async function download(document) {
    try {
      const { data } = await client.get(`/claims/${id}/documents/${document.id}/download`, { responseType: 'blob' });
      const url = URL.createObjectURL(data); const anchor = window.document.createElement('a');
      anchor.href = url; anchor.download = document.filename; anchor.click(); URL.revokeObjectURL(url);
    } catch (e) { setMessage(e.response?.data?.message || 'Document download failed.'); }
  }
  if (!claim) return <p className="muted">Loading claim…</p>;
  const openForResponse = claim.status === 'INFO_REQUESTED';
  return <>
    <section className="welcome-row"><div><Link to="/claims" className="back-link">← Back to claims</Link><span className="eyebrow">CLAIM {claim.claimNumber}</span><h1>{claim.claimType}</h1><p className="muted">Submitted {new Date(claim.createdAt).toLocaleDateString()}</p></div><Status status={claim.status} /></section>
    {message && <p className={message.includes('updated') || message.includes('uploaded') ? 'success' : 'error'} role="status">{message}</p>}
    <div className="detail-grid"><section className="panel"><div className="section-heading"><div><span className="eyebrow">CASE DETAILS</span><h2>Claim overview</h2></div></div>{!isCustomer && <p>Customer: {claim.customerName} (account {claim.customerId})</p>}<dl className="detail-list"><div><dt>Policy</dt><dd>{claim.policyId}</dd></div><div><dt>Incident date</dt><dd>{claim.incidentDate}</dd></div><div><dt>Claimed amount</dt><dd>${Number(claim.claimedAmount).toLocaleString()}</dd></div><div><dt>Policy coverage</dt><dd>${Number(claim.policyCoverage).toLocaleString()}</dd></div><div><dt>Approved amount</dt><dd>{claim.approvedAmount == null ? '—' : `$${Number(claim.approvedAmount).toLocaleString()}`}</dd></div><div><dt>SLA due</dt><dd>{new Date(claim.slaDueAt).toLocaleString()} {claim.overdue && <span className="error">Overdue</span>}</dd></div></dl><h3>Incident description</h3><p>{claim.description}</p></section>
      <section className="panel"><span className="eyebrow">SUPPORTING FILES</span><h2>Documents</h2>      <ul className="document-list">{documents.map((doc) => <li key={doc.id}><span><strong>{doc.filename}</strong><small>{doc.scanStatus} ({doc.scanEngine}) · {(doc.sizeBytes / 1024).toFixed(0)} KB</small></span>{doc.scanStatus === 'CLEAN' && <button className="text-button" onClick={() => download(doc)}>Download</button>}</li>)}{documents.length === 0 && <li className="muted">No documents attached.</li>}</ul>
        {((isCustomer && (claim.status === 'SUBMITTED' || openForResponse)) || (isAgent && claim.status === 'SUBMITTED')) && <form className="upload-row" onSubmit={upload}><label className="sr-only" htmlFor="claim-file">Add a document</label><input id="claim-file" name="file" type="file" accept=".pdf,.jpg,.jpeg,.png,.docx" required /><button className="button button-secondary" disabled={busy}>Upload</button></form>}
      </section>
    </div>
    <section className="panel action-panel"><span className="eyebrow">CUSTOMER COMMUNICATION</span><h2>Customer comments</h2><p className="muted">Visible to the customer and authorized staff; internal team notes are kept separate.</p><ul className="document-list">{comments.map((comment, index) => <li key={`${comment.createdAt}-${index}`}><span><strong>{comment.author}</strong><small>{new Date(comment.createdAt).toLocaleString()}</small><p>{comment.comment}</p></span></li>)}{comments.length === 0 && <li className="muted">No customer comments.</li>}</ul>{isCustomer && !['REJECTED', 'SETTLED', 'WITHDRAWN'].includes(claim.status) && <form className="form-stack" onSubmit={addComment}><label>Add a comment<textarea value={commentText} onChange={(event) => setCommentText(event.target.value)} maxLength="4000" required /></label><button className="button button-secondary" disabled={busy || !commentText.trim()}>Add comment</button></form>}</section>
    {isCustomer && openForResponse && <section className="panel action-panel"><h2>Additional information requested</h2><p>Upload the requested files above, then send a response to return the claim to review.</p><label>Your response<textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} maxLength="2000" required /></label><button className="button button-primary" disabled={busy || !remarks.trim()} onClick={() => action('response', { remarks })}>Send response</button></section>}
    {isCustomer && claim.status === 'SUBMITTED' && <section className="panel action-panel"><h2>Withdraw claim</h2><p>You can withdraw this claim until formal review begins.</p><button className="button button-danger" disabled={busy} onClick={() => action('withdraw')}>Withdraw claim</button></section>}
    <section className="panel action-panel"><h2>Claim summary</h2><p>Download a copy of this claim and its policy references.</p><button className="button button-secondary" onClick={async () => { try { const { data } = await client.get(`/claims/${id}/summary.pdf`, { responseType: 'blob' }); const url = URL.createObjectURL(data); const link = document.createElement('a'); link.href = url; link.download = `${claim.claimNumber}-summary.pdf`; link.click(); URL.revokeObjectURL(url); } catch (e) { setMessage(e.response?.data?.message || 'Summary download failed.'); } }}>Download claim summary (PDF)</button></section>
    {isOfficer && <section className="panel action-panel"><span className="eyebrow">ADJUDICATION</span><h2>Review actions</h2>{claim.status === 'SUBMITTED' && <button className="button button-primary" disabled={busy} onClick={() => action('review')}>Start review</button>}
      {claim.status === 'UNDER_REVIEW' && <><label>Review note or decision reason<textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} maxLength="2000" /></label><div className="form-row"><label>Amount (for approval or settlement)<input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} /></label><span className="muted">Required for approval; settlement defaults to approved amount.</span></div><div className="button-row"><button className="button button-secondary" disabled={busy || !remarks.trim()} onClick={() => action('information-request', { remarks })}>Request information</button><button className="button button-primary" disabled={busy || !remarks.trim() || !amount} onClick={() => action('approve', { remarks, amount: Number(amount) })}>Approve</button><button className="button button-danger" disabled={busy || !remarks.trim()} onClick={() => action('reject', { remarks })}>Reject</button></div></>}
      {claim.status === 'APPROVED' && <><label>Settlement reference<input value={remarks} maxLength="100" required onChange={(e) => setRemarks(e.target.value)} /></label><button className="button button-primary" disabled={busy || !remarks.trim()} onClick={() => action('settle', { remarks, amount: amount ? Number(amount) : undefined })}>Record settlement</button></>}
    </section>}
    {(isAgent || isOfficer) && <section className="panel action-panel"><span className="eyebrow">INTERNAL COMMUNICATION</span><h2>Team notes</h2><ul className="document-list">{notes.map((note, index) => <li key={`${note.createdAt}-${index}`}><span><strong>{note.author}</strong><small>{new Date(note.createdAt).toLocaleString()}</small><p>{note.note}</p></span></li>)}{notes.length === 0 && <li className="muted">No internal notes.</li>}</ul><label>Add a note<textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} maxLength="4000" /></label><button className="button button-secondary" disabled={busy || !remarks.trim()} onClick={async () => { const endpoint = isAgent ? `/agent/claims/${id}/notes` : `/claims/${id}/notes`; try { await client.post(endpoint, isAgent ? { note: remarks } : { note: remarks }); setRemarks(''); await refresh(); } catch (e) { setMessage(e.response?.data?.message || 'Note could not be saved.'); } }}>Add internal note</button></section>}
    {isOfficer && claim.assignedOfficerId && <section className="panel action-panel"><span className="eyebrow">ASSIGNMENT</span><h2>Reassign claim</h2><div className="form-row"><label>Claims officer<select value={targetOfficer} onChange={(event) => setTargetOfficer(event.target.value)}><option value="">Select an officer</option>{officers.map((officer) => <option key={officer.id} value={officer.id}>{officer.fullName}</option>)}</select></label><button className="button button-secondary" disabled={busy || !targetOfficer} onClick={() => action('reassign', { officerId: Number(targetOfficer) })}>Reassign</button></div></section>}
    <section className="panel"><span className="eyebrow">AUDIT TRAIL</span><h2>Claim timeline</h2><ol className="timeline">{history.map((entry, index) => <li key={`${entry.createdAt}-${index}`}><span className="timeline-dot" /><div><strong>{entry.to?.replaceAll('_', ' ') || entry.action.replaceAll('_', ' ')}</strong><p>{entry.remarks || 'Claim event recorded'} · {entry.actorRole} · {new Date(entry.createdAt).toLocaleString()}</p>{(entry.previousAmount != null || entry.newAmount != null) && <p>Amount changed: {entry.previousAmount ?? '—'} → {entry.newAmount ?? '—'}</p>}{(entry.previousOfficerId != null || entry.newOfficerId != null) && <p>Officer reassignment: {entry.previousOfficerId ?? 'Unassigned'} → {entry.newOfficerId ?? 'Unassigned'}</p>}</div></li>)}</ol></section>
    {settlements.length > 0 && <section className="panel"><span className="eyebrow">SETTLEMENT LEDGER</span><h2>Claim settlement records</h2><div className="table-wrap"><table><thead><tr><th>Reference</th><th>Amount</th><th>Recorded</th></tr></thead><tbody>{settlements.map((item) => <tr key={item.id}><td>{item.reference}</td><td>{item.amount}</td><td>{new Date(item.createdAt).toLocaleString()}</td></tr>)}</tbody></table></div></section>}
  </>;
}
