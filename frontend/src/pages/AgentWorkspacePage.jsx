import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import client from '../api/client';
import { Empty, Status } from './DashboardPage';

function contentOf(data) {
  return Array.isArray(data?.content) ? data.content : [];
}

export default function AgentWorkspacePage() {
  const [customers, setCustomers] = useState([]);
  const [customerPage, setCustomerPage] = useState(0);
  const [customerHasNext, setCustomerHasNext] = useState(false);
  const [customersLoading, setCustomersLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const [profile, setProfile] = useState(null);
  const [policies, setPolicies] = useState([]);
  const [eligiblePolicies, setEligiblePolicies] = useState([]);
  const [policyPage, setPolicyPage] = useState(0);
  const [policyHasNext, setPolicyHasNext] = useState(false);
  const [claims, setClaims] = useState([]);
  const [claimPage, setClaimPage] = useState(0);
  const [claimHasNext, setClaimHasNext] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [customerLoading, setCustomerLoading] = useState(false);
  const [busy, setBusy] = useState(false);

  async function loadCustomers() {
    setCustomersLoading(true);
    try {
      const { data } = await client.get('/agent/customers', {
        params: { page: customerPage, size: 20 },
      });
      setCustomers(contentOf(data));
      setCustomerHasNext(Boolean(data && !data.last));
      setError('');
    } catch (failure) {
      setError(failure.response?.data?.message || 'Unable to load assigned customers.');
    } finally {
      setCustomersLoading(false);
    }
  }

  useEffect(() => { loadCustomers(); }, [customerPage]);

  async function selectCustomer(customer) {
    if (!customer?.id) {
      setError('Unable to open this customer workspace.');
      return;
    }
    setSelected(customer);
    setMessage('');
    setError('');
    setCustomerLoading(true);
    setProfile(null);
    setPolicies([]);
    setEligiblePolicies([]);
    setClaims([]);
    setPolicyPage(0);
    setClaimPage(0);
    try {
      const [profileResult, policyResult, eligibleResult, claimResult] = await Promise.all([
        client.get(`/customers/${customer.id}`),
        client.get(`/agent/customers/${customer.id}/policies`, { params: { page: 0, size: 10 } }),
        client.get(`/agent/customers/${customer.id}/policies`, { params: { page: 0, size: 100 } }),
        client.get(`/agent/customers/${customer.id}/claims`, { params: { page: 0, size: 10 } }),
      ]);
      setProfile(profileResult.data || null);
      setPolicies(contentOf(policyResult.data));
      setPolicyHasNext(Boolean(policyResult.data && !policyResult.data.last));
      setEligiblePolicies(contentOf(eligibleResult.data).filter((policy) => policy?.status === 'ACTIVE'));
      setClaims(contentOf(claimResult.data));
      setClaimHasNext(Boolean(claimResult.data && !claimResult.data.last));
    } catch (failure) {
      setError(failure.response?.data?.message || 'Unable to load assigned-customer details.');
    } finally {
      setCustomerLoading(false);
    }
  }

  async function pageAssets(type, nextPage) {
    if (!selected?.id) return;
    try {
      const endpoint = type === 'policies'
        ? `/agent/customers/${selected.id}/policies`
        : `/agent/customers/${selected.id}/claims`;
      const { data } = await client.get(endpoint, { params: { page: nextPage, size: 10 } });
      if (type === 'policies') {
        setPolicies(contentOf(data));
        setPolicyPage(nextPage);
        setPolicyHasNext(Boolean(data && !data.last));
      } else {
        setClaims(contentOf(data));
        setClaimPage(nextPage);
        setClaimHasNext(Boolean(data && !data.last));
      }
      setError('');
    } catch (failure) {
      setError(failure.response?.data?.message || 'Unable to load the next customer page.');
    }
  }

  async function submitAssisted(event) {
    event.preventDefault();
    if (!selected?.id) return;
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setBusy(true);
    setMessage('');
    setError('');
    try {
      const { data } = await client.post(`/agent/customers/${selected.id}/claims`, {
        policyId: Number(form.get('policyId')),
        claimType: form.get('claimType'),
        incidentDate: form.get('incidentDate'),
        description: form.get('description'),
        claimedAmount: Number(form.get('claimedAmount')),
      });
      const file = form.get('document');
      let uploadFailure = '';
      if (file?.size) {
        const upload = new FormData();
        upload.append('file', file);
        try {
          await client.post(`/claims/${data.id}/documents`, upload, {
            headers: { 'Content-Type': 'multipart/form-data' },
          });
        } catch (failure) {
          uploadFailure = failure.response?.data?.message || 'please open the claim and retry.';
        }
      }
      formElement.reset();
      await selectCustomer(selected);
      setMessage(uploadFailure
        ? `Claim ${data.claimNumber} was submitted, but the document upload failed: ${uploadFailure}`
        : `Claim ${data.claimNumber} submitted for ${selected.fullName || 'the customer'}.`);
    } catch (failure) {
      setError(failure.response?.data?.message || 'Assisted claim submission failed.');
    } finally {
      setBusy(false);
    }
  }

  return <>
    <section className="welcome-row">
      <div>
        <span className="eyebrow">AGENT WORKSPACE</span>
        <h1>Assigned customers</h1>
        <p className="muted">Customer and claim information is limited to your active assignments.</p>
      </div>
    </section>
    {error && <p role="alert" className="error">{error}</p>}
    {message && <p role="status" className="success">{message}</p>}
    <section className="panel">
      <h2>My customers</h2>
      {customersLoading ? <p role="status">Loading assigned customers…</p>
        : customers.length ? <div className="table-wrap"><table>
          <thead><tr><th>Customer</th><th>Email</th><th>Action</th></tr></thead>
          <tbody>{customers.map((customer) => <tr key={customer?.id}>
            <td>{customer?.fullName || '—'}</td>
            <td>{customer?.email || '—'}</td>
            <td><button className="text-button" disabled={!customer?.id}
              onClick={() => selectCustomer(customer)}>Open workspace</button></td>
          </tr>)}</tbody>
        </table></div> : <Empty title="No assigned customers"
          detail="Ask an administrator to assign customers to your account." />}
      <Pager page={customerPage} hasNext={customerHasNext}
        onPage={setCustomerPage} />
    </section>

    {selected && <>
      {customerLoading ? <section className="panel" role="status">Loading workspace for {selected.fullName || 'customer'}…</section>
        : <>
          <section className="panel">
            <span className="eyebrow">CUSTOMER PROFILE</span>
            <h2>{profile?.fullName || selected.fullName || 'Customer profile'}</h2>
            <p>{profile?.email || selected.email || '—'} · DOB {profile?.dateOfBirth || '—'} ·
              {' '}phone {profile?.maskedPhone || '—'} · KYC {profile?.kycStatus || '—'}</p>
            <p className="muted">Address and full identifiers are not disclosed in the agent view.</p>
          </section>
          <section className="panel">
            <h2>Assigned policies</h2>
            {policies.length ? <div className="table-wrap"><table>
              <thead><tr><th>Policy</th><th>Product</th><th>Period</th><th>Status</th><th>Coverage</th></tr></thead>
              <tbody>{policies.map((policy) => <tr key={policy?.id}>
                <td>{policy?.policyNumber || '—'}</td>
                <td>{policy?.productName || '—'}</td>
                <td>{policy?.startDate || '—'} — {policy?.endDate || '—'}</td>
                <td><Status status={policy?.status} /></td>
                <td>{policy?.coverageAmount ?? '—'}</td>
              </tr>)}</tbody>
            </table></div> : <Empty title="No policies" detail="This customer has no policies." />}
            <Pager page={policyPage} hasNext={policyHasNext}
              onPage={(value) => pageAssets('policies', value)} />
          </section>
          <section className="panel">
            <h2>Assigned claims</h2>
            {claims.length ? <div className="table-wrap"><table>
              <thead><tr><th>Claim</th><th>Type</th><th>Status</th><th>Open</th></tr></thead>
              <tbody>{claims.map((claim) => <tr key={claim?.id}>
                <td>{claim?.claimNumber || '—'}</td>
                <td>{claim?.claimType || '—'}</td>
                <td><Status status={claim?.status} /></td>
                <td>{claim?.id ? <Link to={`/claims/${claim.id}`}>Open</Link> : '—'}</td>
              </tr>)}</tbody>
            </table></div> : <Empty title="No claims" detail="This customer's claims will appear here." />}
            <Pager page={claimPage} hasNext={claimHasNext}
              onPage={(value) => pageAssets('claims', value)} />
          </section>
          <AssistedClaimForm policies={eligiblePolicies} submit={submitAssisted} busy={busy} />
        </>}
    </>}
  </>;
}

function Pager({ page, hasNext, onPage }) {
  return <div className="button-row pagination">
    <button className="button button-quiet" disabled={page === 0}
      onClick={() => onPage(page - 1)}>Previous</button>
    <span>Page {page + 1}</span>
    <button className="button button-quiet" disabled={!hasNext}
      onClick={() => onPage(page + 1)}>Next</button>
  </div>;
}

function AssistedClaimForm({ policies, submit, busy }) {
  const [types, setTypes] = useState([]);
  const [selectedPolicy, setSelectedPolicy] = useState('');
  const [typeError, setTypeError] = useState('');

  async function loadTypes(policyId) {
    setSelectedPolicy(policyId);
    setTypes([]);
    setTypeError('');
    const policy = policies.find((item) => String(item?.id) === policyId);
    if (!policy) return;
    try {
      const { data } = await client.get('/claim-types', { params: { productId: policy.productId } });
      setTypes(Array.isArray(data) ? data : []);
    } catch (failure) {
      setTypeError(failure.response?.data?.message || 'Unable to load claim types for this policy.');
    }
  }

  return <section className="panel">
    <h2>Assist with claim submission</h2>
    {typeError && <p role="alert" className="error">{typeError}</p>}
    <form className="form-stack claim-form" onSubmit={submit}>
      <label>Active policy
        <select name="policyId" required value={selectedPolicy}
          onChange={(event) => loadTypes(event.target.value)}>
          <option value="">Choose policy</option>
          {policies.map((policy) => <option key={policy?.id} value={policy?.id}>
            {policy?.policyNumber || 'Policy'} · {policy?.productName || '—'}
          </option>)}
        </select>
      </label>
      <label>Claim type
        <select name="claimType" required disabled={!types.length}>
          <option value="">Choose type</option>
          {types.map((type) => <option key={type?.id}>{type?.name || '—'}</option>)}
        </select>
      </label>
      <div className="form-row">
        <label>Incident date<input name="incidentDate" type="date"
          max={new Date().toISOString().slice(0, 10)} required /></label>
        <label>Claimed amount<input name="claimedAmount" type="number" min="0.01"
          step="0.01" required /></label>
      </div>
      <label>Description<textarea name="description" maxLength="4000" required /></label>
      <label>Supporting document (PDF, JPG, PNG, DOCX; up to 10 MB)
        <input name="document" type="file" accept=".pdf,.jpg,.jpeg,.png,.docx" />
      </label>
      <button className="button button-primary" disabled={busy || !policies.length}>
        {busy ? 'Submitting…' : 'Submit assisted claim'}
      </button>
    </form>
  </section>;
}
