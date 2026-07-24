import axios from 'axios';

export const apiClient = axios.create({
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  const method = (config.method || 'GET').toUpperCase();
  const url = config.url || '';
  const payload = config.data ? config.data : (config.params ? config.params : '');

  console.log(
    `%c[HTTP REQ] %c${method} %c${url}`,
    'color: #1E88E5; font-weight: bold;',
    'color: #0D47A1; font-weight: bold;',
    'color: #333333;',
    payload
  );

  return config;
});

apiClient.interceptors.response.use(
  (response) => {
    const method = (response.config.method || 'GET').toUpperCase();
    const url = response.config.url || '';

    console.log(
      `%c[HTTP RESP] %c${method} %c${url} %c(${response.status})`,
      'color: #43A047; font-weight: bold;',
      'color: #1B5E20; font-weight: bold;',
      'color: #333333;',
      'color: #2E7D32; font-weight: bold;',
      response.data
    );

    return response;
  },
  (error) => {
    const method = (error.config?.method || 'REQ').toUpperCase();
    const url = error.config?.url || '';
    const status = error.response?.status || 'ERR';

    console.error(
      `%c[HTTP ERR] %c${method} %c${url} %c(Status: ${status})`,
      'color: #E53935; font-weight: bold;',
      'color: #B71C1C; font-weight: bold;',
      'color: #333333;',
      'color: #D32F2F; font-weight: bold;',
      error.response?.data || error.message
    );

    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);
