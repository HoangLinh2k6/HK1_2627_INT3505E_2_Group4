import test from 'node:test';
import assert from 'node:assert/strict';
import { createBackendTransport } from '../apps/web/src/api/backendAdapter.js';
import { localDate, addDays, slotStart } from '../packages/shared/time.js';

const NOW = Date.parse('2026-11-09T09:00:00+07:00');

function createMockSpringBackend() {
  const calls = [];
  const state = {
    users: [
      { id: 1, email: 'player@keosan.demo', fullName: 'Minh Anh', phone: '0900000000', role: 'CUSTOMER' },
      { id: 2, email: 'owner@keosan.demo', fullName: 'Quản lý sân', phone: '0900000001', role: 'OWNER' },
    ],
    sports: [{ id: 1, name: 'Bóng đá' }],
    venues: [
      {
        id: 1,
        owner: { id: 2 },
        name: 'Sân bóng Mỹ Đình',
        address: 'Lê Đức Thọ, Nam Từ Liêm, Hà Nội',
        district: 'Nam Từ Liêm',
        city: 'Hà Nội',
        description: 'Mặt cỏ nhân tạo đạt chuẩn.',
        openTime: '06:00:00',
        closeTime: '23:00:00',
        status: 'ACTIVE',
      },
    ],
    courts: [
      {
        id: 10,
        venue: { id: 1 },
        sportType: { id: 1, name: 'Bóng đá' },
        name: 'Sân số 1 (7 người)',
        status: 'AVAILABLE',
      },
    ],
    bookings: [],
    payments: [],
    reviews: [],
  };

  let nextBookingId = 50;
  let nextPaymentId = 70;
  let nextReviewId = 90;

  const json = (data, status = 200) =>
    new Response(status === 204 ? null : JSON.stringify(data), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });

  const notFound = (msg) =>
    new Response(JSON.stringify({ status: 404, error: 'Not Found', message: msg }), {
      status: 404,
      headers: { 'Content-Type': 'application/json' },
    });

  async function fetchImpl(urlString, options = {}) {
    const url = new URL(urlString, 'http://127.0.0.1:8080');
    const path = url.pathname.replace(/^\/api\/v1/, '');
    const method = (options.method || 'GET').toUpperCase();
    const body = options.body ? JSON.parse(options.body) : null;
    calls.push({ method, path, search: url.search, body });

    if (path === '/users/me') {
      if (method === 'POST') {
        let user = state.users.find((u) => u.email === body.email);
        if (!user) {
          user = {
            id: state.users.length + 1,
            email: body.email,
            fullName: body.fullName,
            phone: body.phone,
            role: body.role || 'CUSTOMER',
          };
          state.users.push(user);
        }
        return json(user);
      }
      const id = Number(url.searchParams.get('id'));
      const user = state.users.find((u) => u.id === id);
      if (!user) return notFound('User not found');
      if (method === 'PUT') {
        user.fullName = body.fullName;
        user.phone = body.phone;
      }
      return json(user);
    }

    if (path === '/sport-types' && method === 'GET') {
      return state.sports.length ? json(state.sports) : notFound('No sport types found');
    }

    if (path === '/venues' && method === 'GET') {
      return state.venues.length ? json(state.venues) : notFound('No venues found');
    }

    if (path.startsWith('/owner/venues/') && method === 'GET') {
      const ownerId = Number(path.split('/')[3]);
      const list = state.venues.filter((v) => v.owner.id === ownerId);
      return list.length ? json(list) : notFound('No venues found for the given owner');
    }

    if (/^\/venues\/\d+$/.test(path) && method === 'GET') {
      const id = Number(path.split('/')[2]);
      const v = state.venues.find((x) => x.id === id);
      return v ? json(v) : notFound('Venue not found');
    }

    if (/^\/venues\/\d+\/courts$/.test(path) && method === 'GET') {
      const vid = Number(path.split('/')[2]);
      const list = state.courts.filter((c) => c.venue.id === vid);
      return list.length ? json(list) : notFound('No courts found');
    }

    if (/^\/courts\/\d+$/.test(path) && method === 'GET') {
      const cid = Number(path.split('/')[2]);
      const c = state.courts.find((x) => x.id === cid);
      return c ? json(c) : notFound('Court not found');
    }

    if (/^\/venues\/\d+\/reviews$/.test(path)) {
      const vid = Number(path.split('/')[2]);
      if (method === 'GET') {
        const rows = state.reviews.filter((r) => r.venue.id === vid);
        if (!rows.length) return notFound('Venue not found');
        return json({ content: rows, totalElements: rows.length, totalPages: 1 });
      }
      if (method === 'POST') {
        const created = {
          id: nextReviewId++,
          venue: { id: body.venue.id },
          user: state.users.find((u) => u.id === body.user.id) || { id: body.user.id, fullName: 'Minh Anh' },
          booking: { id: body.booking.id },
          rating: body.rating,
          comment: body.comment,
          createdAt: '2026-11-10T21:00:00',
        };
        state.reviews.push(created);
        return json(created, 201);
      }
    }

    if (/^\/reviews\/\d+$/.test(path) && method === 'PUT') {
      const rid = Number(path.split('/')[2]);
      const r = state.reviews.find((x) => x.id === rid);
      if (!r) return notFound('Review not found');
      r.rating = body.rating;
      r.comment = body.comment;
      // Mirror ReviewController.java returning the incoming body without id
      return json({ rating: body.rating, comment: body.comment });
    }

    if (path === '/bookings' && method === 'GET') {
      if (!state.bookings.length) return notFound('No bookings found');
      return json({ content: state.bookings, totalElements: state.bookings.length, totalPages: 1 });
    }

    if (path === '/bookings' && method === 'POST') {
      const created = {
        id: nextBookingId++,
        user: state.users.find((u) => u.id === body.user.id) || { id: body.user.id },
        court: state.courts.find((c) => c.id === body.court.id) || { id: body.court.id },
        startTime: body.startTime,
        endTime: body.endTime,
        totalPrice: body.totalPrice,
        status: body.status,
        expiresAt: body.expiresAt,
      };
      state.bookings.push(created);
      return json(created, 201);
    }

    if (/^\/bookings\/\d+$/.test(path) && method === 'GET') {
      const bid = Number(path.split('/')[2]);
      const b = state.bookings.find((x) => x.id === bid);
      return b ? json(b) : notFound('Booking not found');
    }

    if (/^\/bookings\/\d+\/(confirm|complete|cancel)$/.test(path) && method === 'PATCH') {
      const [, , bidStr, action] = path.split('/');
      const b = state.bookings.find((x) => x.id === Number(bidStr));
      if (!b) return notFound('Booking not found');
      b.status =
        action === 'confirm' ? 'CONFIRMED' : action === 'complete' ? 'COMPLETED' : 'CANCELLED';
      return new Response(null, { status: 200 });
    }

    if (/^\/bookings\/\d+\/payments$/.test(path)) {
      const bid = Number(path.split('/')[2]);
      if (method === 'GET') {
        const list = state.payments.filter((p) => p.booking.id === bid);
        return list.length ? json(list) : notFound('No payments found');
      }
      if (method === 'POST') {
        const created = {
          id: nextPaymentId++,
          booking: { id: body.booking.id },
          amount: body.amount,
          method: body.method,
          transactionRef: body.transactionRef,
          paidAt: body.paidAt,
        };
        state.payments.push(created);
        return json(created);
      }
    }

    return notFound(`Unhandled ${method} ${path}`);
  }

  return { fetchImpl, calls, state };
}

test('backend adapter translates Spring Boot entities, empty 404s, PATCH actions, availability and reviews', async () => {
  const spring = createMockSpringBackend();
  const adapter = createBackendTransport({ fetchImpl: spring.fetchImpl, now: () => NOW });

  async function call(path, options = {}) {
    const res = await adapter.send(path, options, '/api/v1');
    const body = res.status === 204 ? null : await res.json();
    return { status: res.status, body };
  }

  // 1. Sport types + Venues (even when reviews return 404)
  const sports = await call('/sport-types');
  assert.equal(sports.status, 200);
  assert.deepEqual(sports.body.data, [{ id: '1', name: 'Bóng đá' }]);

  const venues = await call('/venues?district=Nam%20T%E1%BB%AB%20Li%C3%AAm');
  assert.equal(venues.status, 200);
  assert.equal(venues.body.data.length, 1);
  assert.equal(venues.body.data[0].status, 'active');
  assert.equal(venues.body.data[0].openTime, '06:00');
  assert.equal(venues.body.data[0].courtCount, 1);
  assert.equal(venues.body.data[0].courtTypes[0].teamSize, 7);

  // 2. Availability & Quote (computed despite backend returning null)
  const tomorrow = addDays(localDate(NOW), 1);
  const avail = await call(`/courts/10/availability?date=${tomorrow}`);
  assert.equal(avail.status, 200);
  assert.ok(avail.body.data.slots.length > 0);
  assert.equal(avail.body.data.slots[0].available, true);

  const start = slotStart(tomorrow, 18); // peak hour 18:00–19:00
  const end = slotStart(tomorrow, 19);
  const quote = await call(`/courts/10/quote?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`);
  assert.equal(quote.status, 200);
  assert.equal(quote.body.data.totalPrice, 400000);
  assert.equal(quote.body.data.available, true);

  // 3. Login & Update profile -> GET/PUT /users/me?id=1
  const login = await call('/auth/login', {
    method: 'POST',
    body: { email: 'player@keosan.demo', password: 'Demo@12345' },
  });
  assert.equal(login.status, 200);
  assert.equal(login.body.data.user.id, '1');

  const profileUpdate = await call('/users/me', {
    method: 'PUT',
    body: { fullName: 'Minh Anh Updated', phone: '0911111111' },
  });
  assert.equal(profileUpdate.status, 200);
  assert.equal(profileUpdate.body.data.fullName, 'Minh Anh Updated');

  // 4. Empty bookings 404 from Spring Boot -> clean empty paginated response
  const emptyBookings = await call('/users/me/bookings');
  assert.equal(emptyBookings.status, 200);
  assert.deepEqual(emptyBookings.body.data, []);

  // 5. Create booking -> sends LocalDateTime & nested user/court objects
  const createdBooking = await call('/bookings', {
    method: 'POST',
    body: { courtId: '10', startTime: start, endTime: end, note: 'Thuê bóng' },
  });
  assert.equal(createdBooking.status, 201);
  assert.equal(createdBooking.body.data.status, 'pending');
  assert.equal(createdBooking.body.data.totalPrice, 400000);

  const postBookingCall = spring.calls.find((c) => c.method === 'POST' && c.path === '/bookings');
  assert.equal(postBookingCall.body.user.id, 1);
  assert.equal(postBookingCall.body.court.id, 10);
  assert.match(postBookingCall.body.startTime, /^\d{4}-\d{2}-\d{2}T18:00:00$/);

  // Availability now blocks the 18:00 slot
  const availAfter = await call(`/courts/10/availability?date=${tomorrow}`);
  const slot18 = availAfter.body.data.slots.find((s) => s.startTime === start);
  assert.equal(slot18.available, false);

  // 6. Owner confirm/complete -> translates POST to PATCH on Spring Boot
  const confirmed = await call(`/bookings/${createdBooking.body.data.id}/confirm`, { method: 'POST' });
  assert.equal(confirmed.status, 200);
  assert.equal(confirmed.body.data.status, 'confirmed');
  assert.ok(
    spring.calls.some(
      (c) => c.method === 'PATCH' && c.path === `/bookings/${createdBooking.body.data.id}/confirm`,
    ),
  );

  // 7. Payment create -> sends nested booking { id } & uppercase PaymentMethod enum
  const payment = await call(`/bookings/${createdBooking.body.data.id}/payments`, {
    method: 'POST',
    body: { method: 'bank_transfer' },
  });
  assert.equal(payment.status, 201);
  assert.equal(payment.body.data.status, 'succeeded');
  assert.equal(payment.body.data.method, 'bank_transfer');

  // 8. Complete booking & create/update review
  const completed = await call(`/bookings/${createdBooking.body.data.id}/complete`, { method: 'POST' });
  assert.equal(completed.status, 200);
  assert.equal(completed.body.data.status, 'completed');
  assert.equal(completed.body.data.paymentStatus, 'paid');

  const review = await call('/venues/1/reviews', {
    method: 'POST',
    body: { bookingId: createdBooking.body.data.id, rating: 5, comment: 'Sân đẹp, đèn sáng.' },
  });
  assert.equal(review.status, 201);
  assert.equal(review.body.data.rating, 5);

  const updatedReview = await call(`/reviews/${review.body.data.id}`, {
    method: 'PUT',
    body: { rating: 4, comment: 'Sân ổn, giữ nguyên lịch.' },
  });
  assert.equal(updatedReview.status, 200);
  assert.equal(updatedReview.body.data.id, review.body.data.id);
  assert.equal(updatedReview.body.data.rating, 4);

  // 9. Owner venues endpoint routes to /owner/venues/{ownerId}
  await call('/auth/login', {
    method: 'POST',
    body: { email: 'owner@keosan.demo', password: 'Demo@12345' },
  });
  const ownerVenues = await call('/owner/venues');
  assert.equal(ownerVenues.status, 200);
  assert.equal(ownerVenues.body.data.length, 1);
  assert.ok(spring.calls.some((c) => c.method === 'GET' && c.path === '/owner/venues/2'));
});
