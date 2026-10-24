import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function AuthPage({ mode }) {
  const registering = mode === 'register';
  const { user, login, register } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/" replace />;

  async function submit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);
    const form = new FormData(event.currentTarget);
    try {
      if (registering) await register(form.get('fullName'), form.get('email'), form.get('password'));
      else await login(form.get('email'), form.get('password'));
      navigate('/');
    } catch (failure) {
      setError(failure.response?.data?.message || 'Unable to sign in. Check your details and try again.');
    } finally { setBusy(false); }
  }

  return (
    <main className="auth-page">
      <section className="auth-art"><div className="art-copy"><span className="eyebrow">HERE WHEN IT MATTERS</span><h1>Protection that moves forward with you.</h1><p>Manage policies and follow every claim update in one secure place.</p></div><div className="art-orbit" aria-hidden="true" /></section>
      <section className="auth-panel">
        <div className="auth-form-wrap">
          <NavBrand />
          <h2>{registering ? 'Create your account' : 'Welcome back'}</h2>
          <p className="muted">{registering ? 'Get started with your Smart Claim account.' : 'Sign in to continue to your insurance workspace.'}</p>
          <form className="form-stack" onSubmit={submit}>
            {registering && <label>Full name<input name="fullName" autoComplete="name" required maxLength="160" /></label>}
            <label>Email address<input name="email" type="email" autoComplete="email" required /></label>
            <label>Password<input name="password" type="password" autoComplete={registering ? 'new-password' : 'current-password'} minLength={registering ? 12 : 1} required /></label>
            {error && <p className="error" role="alert">{error}</p>}
            <button className="button button-primary button-wide" disabled={busy}>{busy ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}</button>
          </form>
          <p className="auth-switch">{registering ? 'Already registered?' : 'New to Smart Claim?'} <Link to={registering ? '/login' : '/register'}>{registering ? 'Sign in' : 'Create an account'}</Link></p>
        </div>
      </section>
    </main>
  );
}

function NavBrand() { return <div className="brand auth-brand"><span className="brand-mark">SC</span><span>Smart Claim</span></div>; }
