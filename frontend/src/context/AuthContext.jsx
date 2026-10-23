import { createContext, useContext, useMemo, useState } from 'react';
import client from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(sessionStorage.getItem('smart-insurance-user') || 'null'); }
    catch { return null; }
  });

  async function login(email, password) {
    const { data } = await client.post('/auth/login', { email, password });
    sessionStorage.setItem('smart-insurance-token', data.token);
    sessionStorage.setItem('smart-insurance-user', JSON.stringify(data.user));
    setUser(data.user);
  }

  async function register(fullName, email, password) {
    const { data } = await client.post('/auth/register', { fullName, email, password });
    sessionStorage.setItem('smart-insurance-token', data.token);
    sessionStorage.setItem('smart-insurance-user', JSON.stringify(data.user));
    setUser(data.user);
  }

  function logout() {
    sessionStorage.removeItem('smart-insurance-token');
    sessionStorage.removeItem('smart-insurance-user');
    setUser(null);
  }

  const value = useMemo(() => ({ user, login, register, logout }), [user]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
