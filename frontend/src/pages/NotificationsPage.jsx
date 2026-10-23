import { useEffect, useState } from 'react';
import client from '../api/client';
import { Link } from 'react-router-dom';

export default function NotificationsPage() {
  const [items, setItems] = useState([]);
  const [error, setError] = useState('');
  async function load() { const { data } = await client.get('/notifications'); setItems(data); }
  useEffect(() => { load().catch((e) => setError(e.response?.data?.message || 'Unable to load notifications.')); }, []);
  async function read(id) { try { await client.patch(`/notifications/${id}/read`); await load(); } catch (e) { setError(e.response?.data?.message || 'Unable to update notification.'); } }
  return <><section className="welcome-row"><div><span className="eyebrow">UPDATES</span><h1>Notifications</h1><p className="muted">Important activity linked to your insurance account.</p></div></section>{error && <p className="error" role="alert">{error}</p>}<section className="panel notification-list">{items.length ? items.map((item) => <article className={`notification ${item.read ? '' : 'unread'}`} key={item.id}><div><strong>{item.eventType.replaceAll('_', ' ')}</strong><p>{item.message}</p><small>{new Date(item.createdAt).toLocaleString()}</small></div><div className="button-row">{item.claimId && <Link to={`/claims/${item.claimId}`}>View claim</Link>}{!item.read && <button className="text-button" onClick={() => read(item.id)}>Mark as read</button>}</div></article>) : <p className="muted">You’re all caught up.</p>}</section></>;
}
