import { useEffect, useState } from 'react';
import client from '../api/client';

export default function AdminQueuesPage() {
  const [queues, setQueues] = useState([]);
  const [officers, setOfficers] = useState([]);
  const [members, setMembers] = useState([]);
  const [selected, setSelected] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  async function loadQueues() {
    const { data } = await client.get('/admin/queues');
    setQueues(data);
    if (!selected && data.length) setSelected(data[0].code);
  }
  async function loadMembers(code = selected) {
    if (!code) return;
    const { data } = await client.get(`/admin/queues/${code}/members`, { params: { page: 0, size: 100 } });
    setMembers(data.content || []);
  }
  useEffect(() => {
    Promise.all([
      loadQueues(),
      client.get('/admin/users', { params: { role: 'CLAIMS_OFFICER', size: 100 } })
        .then(({ data }) => setOfficers(data.content || [])),
    ]).catch((e) => setError(e.response?.data?.message || 'Unable to load queue administration.'));
  }, []);
  useEffect(() => {
    if (selected) loadMembers(selected).catch((e) => setError(e.response?.data?.message || 'Unable to load queue members.'));
  }, [selected]);

  async function create(event) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    try {
      const { data } = await client.post('/admin/queues', {
        code: form.get('code'), name: form.get('name'),
      });
      await loadQueues();
      setSelected(data.code);
      setMessage('Queue created.');
      formElement.reset();
      setError('');
    } catch (e) { setError(e.response?.data?.message || 'Queue could not be created.'); }
  }
  async function update(queue) {
    const name = window.prompt('Queue name', queue.name);
    if (name === null) return;
    try {
      await client.put(`/admin/queues/${queue.code}`, { name, active: !queue.active });
      await loadQueues();
      setMessage(queue.active ? 'Queue deactivated.' : 'Queue activated.');
      setError('');
    } catch (e) { setError(e.response?.data?.message || 'Queue could not be updated.'); }
  }
  async function addMember(event) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const officerId = Number(new FormData(formElement).get('officerId'));
    try {
      await client.post(`/admin/queues/${selected}/members`, { officerId });
      await loadMembers();
      setMessage('Officer added to queue.');
      setError('');
      formElement.reset();
    } catch (e) { setError(e.response?.data?.message || 'Officer could not be assigned.'); }
  }
  async function removeMember(officerId) {
    try {
      await client.delete(`/admin/queues/${selected}/members/${officerId}`);
      await loadMembers();
      setMessage('Officer removed from queue.');
      setError('');
    } catch (e) { setError(e.response?.data?.message || 'Officer could not be removed.'); }
  }

  const queue = queues.find((item) => item.code === selected);
  const availableOfficers = officers.filter((officer) => officer.active
    && !members.some((member) => member.officerId === officer.id));
  return <>
    <section className="welcome-row"><div><span className="eyebrow">CLAIMS ROUTING</span><h1>Queue administration</h1><p className="muted">Route claim types to active queues and manage the officers authorized to process each queue.</p></div></section>
    {error && <p className="error" role="alert">{error}</p>}{message && <p className="success" role="status">{message}</p>}
    <form className="panel form-row" onSubmit={create}><label>Queue code<input name="code" required maxLength="40" pattern="[A-Za-z][A-Za-z0-9_-]{1,39}" /></label><label>Queue name<input name="name" required maxLength="100" /></label><button className="button button-primary">Create queue</button></form>
    <section className="panel"><h2>Configured queues</h2><div className="table-wrap"><table><thead><tr><th>Code</th><th>Name</th><th>Status</th><th>Created</th><th>Action</th></tr></thead><tbody>{queues.map((item) => <tr key={item.code}><td>{item.code}</td><td>{item.name}</td><td>{item.active ? 'Active' : 'Inactive'}</td><td>{new Date(item.createdAt).toLocaleString()}</td><td><button className="text-button" onClick={() => update(item)}>{item.active ? 'Edit / deactivate' : 'Edit / activate'}</button></td></tr>)}</tbody></table></div></section>
    <section className="panel"><h2>Officer membership</h2><label>Queue<select value={selected} onChange={(event) => setSelected(event.target.value)}><option value="">Select queue</option>{queues.map((item) => <option key={item.code} value={item.code}>{item.name} ({item.code}) · {item.active ? 'active' : 'inactive'}</option>)}</select></label>
      {queue?.active && <form className="form-row" onSubmit={addMember}><label>Claims officer<select name="officerId" required><option value="">Select officer</option>{availableOfficers.map((officer) => <option key={officer.id} value={officer.id}>{officer.fullName} · {officer.email}</option>)}</select></label><button className="button button-primary" disabled={!availableOfficers.length}>Add officer</button></form>}
      <div className="table-wrap"><table><thead><tr><th>Officer</th><th>Email</th><th>Added by</th><th>Added</th><th>Action</th></tr></thead><tbody>{members.map((member) => <tr key={member.officerId}><td>{member.officerName}</td><td>{member.email}</td><td>{member.assignedBy}</td><td>{new Date(member.createdAt).toLocaleString()}</td><td><button className="text-button" onClick={() => removeMember(member.officerId)}>Remove</button></td></tr>)}</tbody></table></div>
    </section>
  </>;
}
