import { request, queryString } from './client.js';
const get = (path, params, signal) => request(`${path}${queryString(params)}`, { signal });
const post = (path, body, key) =>
  request(path, { method: 'POST', body, ...(key ? { headers: { 'Idempotency-Key': key } } : {}) });
const put = (path, body, etag) =>
  request(path, { method: 'PUT', body, ...(etag ? { headers: { 'If-Match': etag } } : {}) });
const remove = (path, etag) =>
  request(path, { method: 'DELETE', ...(etag ? { headers: { 'If-Match': etag } } : {}) });
export const identityApi = {
  register: (body) => post('/auth/register', body),
  login: (body) => post('/auth/login', body),
  refresh: () => post('/auth/refresh'),
  logout: () => post('/auth/logout'),
  me: (signal) => get('/users/me', {}, signal),
  update: (body) => put('/users/me', body),
};
export const sportApi = {
  list: (signal) => get('/sport-types', {}, signal),
  create: (body) => post('/sport-types', body),
  update: (id, body) => put(`/sport-types/${id}`, body),
  remove: (id) => remove(`/sport-types/${id}`),
};
export const venueApi = {
  list: (params, signal) => get('/venues', params, signal),
  mine: (params, signal) => get('/owner/venues', params, signal),
  get: (id, signal) => get(`/venues/${id}`, {}, signal),
  courts: (id, params, signal) => get(`/venues/${id}/courts`, params, signal),
  create: (body) => post('/venues', body),
  update: (id, body, etag) => put(`/venues/${id}`, body, etag),
  remove: (id, etag) => remove(`/venues/${id}`, etag),
};
export const courtApi = {
  get: (id, signal) => get(`/courts/${id}`, {}, signal),
  availability: (id, date, signal) => get(`/courts/${id}/availability`, { date }, signal),
  quote: (id, start, end, signal) => get(`/courts/${id}/quote`, { start, end }, signal),
  create: (venueId, body) => post(`/venues/${venueId}/courts`, body),
  update: (id, body, etag) => put(`/courts/${id}`, body, etag),
  remove: (id, etag) => remove(`/courts/${id}`, etag),
};
export const bookingApi = {
  list: (params, signal) => get('/users/me/bookings', params, signal),
  forVenue: (venueId, params, signal) => get(`/venues/${venueId}/bookings`, params, signal),
  get: (id, signal) => get(`/bookings/${id}`, {}, signal),
  create: (body, key) => post('/bookings', body, key),
  cancel: (id, reason) => post(`/bookings/${id}/cancel`, { reason }),
  confirm: (id) => post(`/bookings/${id}/confirm`),
  complete: (id) => post(`/bookings/${id}/complete`),
  checkIn: (id) => post(`/bookings/${id}/check-in`),
  checkOut: (id) => post(`/bookings/${id}/check-out`),
};
export const addonApi = {
  venueServices: (venueId, signal) => get(`/venues/${venueId}/services`, {}, signal),
  createVenueService: (venueId, body) => post(`/venues/${venueId}/services`, body),
  updateVenueService: (venueId, serviceId, body) =>
    put(`/venues/${venueId}/services/${serviceId}`, body),
  listAssets: (venueId, serviceId, signal) =>
    get(`/venues/${venueId}/services/${serviceId}/assets`, {}, signal),
  createAsset: (venueId, serviceId, body) =>
    post(`/venues/${venueId}/services/${serviceId}/assets`, body),
  updateAsset: (venueId, serviceId, assetId, body) =>
    put(`/venues/${venueId}/services/${serviceId}/assets/${assetId}`, body),
  bookingServices: (bookingId, signal) => get(`/bookings/${bookingId}/services`, {}, signal),
  reserveOrder: (bookingId, body, key) => post(`/bookings/${bookingId}/services`, body, key),
  cancelOrder: (bookingId, orderId) => remove(`/bookings/${bookingId}/services/${orderId}`),
  deliverOrder: (bookingId, orderId, body = {}) =>
    post(`/bookings/${bookingId}/services/${orderId}/deliver`, body),
};
export const rentalPhotoApi = {
  list: (rentalId, signal) => get(`/rentals/${rentalId}/photos`, {}, signal),
  upload: (rentalId, { phase, angle, file }) => {
    const form = new FormData();
    form.append('phase', phase);
    form.append('angle', angle);
    form.append('file', file);
    return request(`/rentals/${rentalId}/photos`, { method: 'POST', body: form });
  },
  link: (rentalId, photoId, signal) => get(`/rentals/${rentalId}/photos/${photoId}/link`, {}, signal),
  download: (rentalId, photoId, signal) =>
    request(`/rentals/${rentalId}/photos/${photoId}`, { signal, responseType: 'blob' }),
};
export const paymentApi = {
  list: (bookingId, signal) => get(`/bookings/${bookingId}/payments`, {}, signal),
  create: (bookingId, method, key) => post(`/bookings/${bookingId}/payments`, { method }, key),
  get: (id, signal) => get(`/payments/${id}`, {}, signal),
};
export const reviewApi = {
  list: (venueId, params, signal) => get(`/venues/${venueId}/reviews`, params, signal),
  create: (venueId, body) => post(`/venues/${venueId}/reviews`, body),
  update: (id, body) => put(`/reviews/${id}`, body),
  remove: (id) => remove(`/reviews/${id}`),
};
