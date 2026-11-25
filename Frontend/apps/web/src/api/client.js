export const API_MODE = import.meta.env?.VITE_API_MODE || 'http';
const BASE_URL = (import.meta.env?.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '');
let accessToken = null,
  refreshing = null;
export const setAccessToken = (token) => {
  accessToken = token;
};
export class ApiError extends Error {
  constructor(problem, status) {
    super(problem.detail || 'Không thể hoàn tất yêu cầu.');
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}
async function transport(path, options) {
  if (API_MODE === 'mock')
    return (await import('../mocks/transport.js')).mockTransport.send(path, options);
  return (await import('./backendAdapter.js')).backendTransport.send(path, options, BASE_URL);
}
export async function request(
  path,
  { method = 'GET', body, headers = {}, signal, retryAuth = true, responseType = 'json' } = {},
) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12000),
    abort = () => controller.abort();
  if (signal?.aborted) controller.abort();
  signal?.addEventListener('abort', abort, { once: true });
  try {
    const isFormData = typeof FormData !== 'undefined' && body instanceof FormData;
    const accept =
      responseType === 'blob'
        ? 'image/jpeg, image/png, application/problem+json, application/json'
        : 'application/json, application/problem+json';
    const options = {
      method,
      signal: controller.signal,
      responseType,
      headers: {
        Accept: accept,
        ...(body !== undefined && !isFormData ? { 'Content-Type': 'application/json' } : {}),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...headers,
      },
      ...(body !== undefined ? { body } : {}),
    };
    const response = await transport(path, options);
    if (response.status === 401 && accessToken && retryAuth && !path.startsWith('/auth/')) {
      try {
        refreshing ||= request('/auth/refresh', { method: 'POST', retryAuth: false })
          .then((result) => {
            setAccessToken(result.data.accessToken);
            return result;
          })
          .finally(() => {
            refreshing = null;
          });
        await refreshing;
      } catch {
        setAccessToken(null);
        window.dispatchEvent(new Event('session-expired'));
        throw new ApiError({ detail: 'Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.' }, 401);
      }
      return request(path, { method, body, headers, signal, retryAuth: false, responseType });
    }
    if (response.status === 204) return null;
    const isJson = response.headers.get('Content-Type')?.includes('json');
    if (!response.ok) {
      const payload = isJson ? await response.json() : {};
      throw new ApiError(payload, response.status);
    }
    if (responseType === 'blob') return await response.blob();
    const payload = isJson ? await response.json() : {};
    if (!isJson) throw new Error('API trả về dữ liệu không đúng định dạng JSON.');
    return { ...payload, etag: response.headers.get('ETag') };
  } catch (error) {
    if (error.name === 'AbortError' && !signal?.aborted)
      throw new Error('Kết nối quá lâu. Vui lòng thử lại.');
    throw error;
  } finally {
    clearTimeout(timeout);
    signal?.removeEventListener('abort', abort);
  }
}
export function queryString(params = {}) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== '' && v !== undefined && v !== null) query.set(k, String(v));
  });
  return query.size ? `?${query}` : '';
}
