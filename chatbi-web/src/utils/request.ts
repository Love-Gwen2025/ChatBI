import axios from 'axios';
import { authStore } from '../store/authStore';

const request = axios.create({
  baseURL: '/api',
});

request.interceptors.request.use((config) => {
  const token = authStore.getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

request.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      authStore.logout();
      window.location.href = '/login';
    }
    return Promise.reject(err);
  },
);

export default request;
