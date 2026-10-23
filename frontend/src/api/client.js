import axios from 'axios';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1',
  timeout: 15000,
});

client.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('smart-insurance-token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem('smart-insurance-token');
      sessionStorage.removeItem('smart-insurance-user');
      if (!['/login', '/register'].includes(window.location.pathname)) window.location.assign('/login');
    }
    return Promise.reject(error);
  },
);

export default client;
