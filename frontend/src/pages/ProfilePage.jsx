import { useEffect, useState } from 'react';
import client from '../api/client';
import { useAuth } from '../context/AuthContext';

export default function ProfilePage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState(null);
  const [message, setMessage] = useState('');
  useEffect(() => {
    client.get(`/customers/${user.id}`).then(({ data }) => setProfile(data))
      .catch((e) => setMessage(e.response?.status === 404 ? 'Add your KYC details below.' : e.response?.data?.message || 'Profile could not be loaded.'));
  }, [user]);
  async function save(event) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    try {
      const { data } = await client.put('/customers/me/profile', {
        dateOfBirth: form.get('dateOfBirth'), address: form.get('address'),
        phone: form.get('phone'), nationalId: form.get('nationalId'),
      });
      setProfile(data); setMessage('Profile saved securely. Verification is pending.');
    } catch (e) { setMessage(e.response?.data?.message || 'Profile could not be saved.'); }
  }
  async function changePassword(event) {
    event.preventDefault(); const formElement = event.currentTarget; const form = new FormData(formElement);
    try { await client.put('/auth/password', { currentPassword: form.get('currentPassword'), newPassword: form.get('newPassword') }); formElement.reset(); setMessage('Password changed. Sign out and sign in again.'); }
    catch (e) { setMessage(e.response?.data?.message || 'Password could not be changed.'); }
  }
  return <><section className="welcome-row"><div><span className="eyebrow">ACCOUNT SETTINGS</span><h1>Your profile</h1><p className="muted">Sensitive KYC details are encrypted at rest; identifiers are masked in responses.</p></div></section>{message && <p className="muted" role="status">{message}</p>}<section className="panel"><h2>KYC profile</h2>{profile && <p className="muted">Status: {profile.kycStatus} · ID: {profile.maskedNationalId || 'Not provided'}</p>}<form className="form-stack admin-form" onSubmit={save}><label>Date of birth<input name="dateOfBirth" type="date" defaultValue={profile?.dateOfBirth || ''} required /></label><label>Address<textarea name="address" defaultValue={profile?.address || ''} required maxLength="500" /></label><label>Phone<input name="phone" defaultValue="" autoComplete="tel" required maxLength="40" /></label><label>National ID<input name="nationalId" autoComplete="off" required maxLength="100" /></label><button className="button button-primary">Save KYC profile</button></form></section><section className="panel"><h2>Change password</h2><form className="form-stack admin-form" onSubmit={changePassword}><label>Current password<input name="currentPassword" type="password" required /></label><label>New password<input name="newPassword" type="password" minLength="12" maxLength="72" required /></label><button className="button button-secondary">Change password</button></form></section></>;
}
