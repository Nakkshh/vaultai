import axios from 'axios';
import Cookies from 'js-cookie';

const api = axios.create({
  baseURL: 'http://localhost:8085',
});

api.interceptors.request.use((config) => {
  const token = Cookies.get('vault_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export default api;