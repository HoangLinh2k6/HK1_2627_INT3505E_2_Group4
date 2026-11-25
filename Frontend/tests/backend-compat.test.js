import test from 'node:test';
import assert from 'node:assert/strict';
import { createBackendTransport } from '../apps/web/src/api/backendAdapter.js';
import { localDate, addDays, slotStart } from '../packages/shared/time.js';

const NOW = Date.parse('2026-11-09T09:00:00+07:00');
const TIME_ZONE = 'Asia/Ho_Chi_Minh';

function toLocalIso(ms) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(new Date(ms));
  const get = (t) => parts.find((p) => p.type === t)?.value || '00';
  const hour = get('hour') === '24' ? '00' : get('hour');
  return `${get('year')}-${get('month')}-${get('day')}T${hour}:${get('minute')}:${get('second')}`;
}

function parseLocalMs(value) {
  const s = String(value || '').trim();
  if (/(Z|[+-]\d{2}:\d{2})$/.test(s)) return Date.parse(s);
  return Date.parse(`${s}+07:00`);
}

function createMockSpringBackend() {
  const calls = [];
  let tokenSeq = 1;
  let refreshCookie = null;
  const sessions = new Map();

  const state = {
    users: [
      {
        id: 1,
        email: 'player@keosan.demo',
        password: 'Demo@12345',
        fullName: 'Minh Anh',
        phone: '0900000000',
        role: 'CUSTOMER',
      },
      {
        id: 2,
        email: 'owner@keosan.demo',
        password: 'Demo@12345',
        fullName: 'Quản lý sân',
        phone: '0900000001',
        role: 'OWNER',
      },
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
        basePricePerHour: 300000,
        status: 'AVAILABLE',
      },
      {
        id: 11,
        venue: { id: 1 },
        sportType: { id: 1, name: 'Bóng đá' },
        name: 'Sân số 2 (Chưa có giá)',
        basePricePerHour: null,
        status: 'AVAILABLE',
      },
    ],
    bookings: [],
    payments: [],
    reviews: [],
    services: [],
    assets: [],
    orders: [],
    rentals: [],
    photos: [],
  };

  let nextUserId = 3;
  let nextCourtId = 12;
  let nextBookingId = 50;
  let nextPaymentId = 70;
  let nextReviewId = 90;
  let nextServiceId = 100;
  let nextAssetId = 200;
  let nextOrderId = 300;
  let nextRentalId = 400;
  let nextPhotoId = 500;

  const json = (data, status = 200) =>
    new Response(status === 204 ? null : JSON.stringify(data), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });

  const problem = (status, detail) =>
    new Response(JSON.stringify({ status, detail }), {
      status,
      headers: { 'Content-Type': 'application/problem+json' },
    });

  const notFound = (msg) =>
    new Response(JSON.stringify({ status: 404, error: 'Not Found', message: msg }), {
      status: 404,
      headers: { 'Content-Type': 'application/json' },
    });

  function priceInterval(court, startMs, endMs) {
    let total = 0;
    const lines = [];
    for (let t = startMs; t < endMs; t += 1800000) {
      const hour = Number(toLocalIso(t).slice(11, 13));
      const peak = hour >= 17 && hour < 22;
      const amount = Math.round((Number(court.basePricePerHour) + (peak ? 100000 : 0)) / 2);
      total += amount;
      lines.push({
        startTime: new Date(t).toISOString(),
        endTime: new Date(t + 1800000).toISOString(),
        amount,
      });
    }
    return { total, lines };
  }

  function hasOverlap(courtId, startMs, endMs) {
    return state.bookings.some(
      (b) =>
        b.court.id === courtId &&
        ['PENDING', 'CONFIRMED'].includes(b.status) &&
        parseLocalMs(b.startTime) < endMs &&
        parseLocalMs(b.endTime) > startMs,
    );
  }

  async function fetchImpl(urlString, options = {}) {
    const url = new URL(urlString, 'http://127.0.0.1:8080');
    const path = url.pathname.replace(/^\/api\/v1/, '');
    const method = (options.method || 'GET').toUpperCase();
    const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
    const body = isFormData ? options.body : options.body ? JSON.parse(options.body) : null;
    const reqHeaders = new Headers(options.headers || {});
    const authHeader = reqHeaders.get('Authorization') || '';
    const bearer = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : null;
    const principal = bearer ? sessions.get(bearer) || null : null;

    calls.push({
      method,
      path,
      search: url.search,
      headers: Object.fromEntries(reqHeaders.entries()),
      credentials: options.credentials,
      body,
    });

    // Closed or legacy endpoints must never be called by the updated adapter.
    if (
      (path === '/users/me' && method === 'POST') ||
      (path === '/users/me' && url.searchParams.has('id')) ||
      path === '/bookings' && method === 'GET' ||
      path === '/payments' && method === 'GET' ||
      /^\/owner\/venues\/\d+$/.test(path)
    ) {
      return problem(403, `Closed legacy endpoint called: ${method} ${path}${url.search}`);
    }

    // AuthController
    if (path === '/auth/register' && method === 'POST') {
      if (state.users.some((u) => u.email === body.email.toLowerCase())) {
        return problem(409, 'Email already exists');
      }
      const user = {
        id: nextUserId++,
        email: body.email.toLowerCase(),
        password: body.password,
        fullName: body.fullName,
        phone: body.phone,
        role: 'CUSTOMER',
      };
      state.users.push(user);
      return json(
        { data: { id: user.id, email: user.email, fullName: user.fullName, phone: user.phone, role: user.role } },
        201,
      );
    }

    if (path === '/auth/login' && method === 'POST') {
      const user = state.users.find(
        (u) => u.email === body.email.toLowerCase() && u.password === body.password,
      );
      if (!user) return problem(401, 'Invalid email or password');
      const accessToken = `access-${user.id}-${tokenSeq++}`;
      const refresh = `refresh-${user.id}-${tokenSeq++}`;
      sessions.set(accessToken, { userId: user.id, role: user.role });
      refreshCookie = { token: refresh, userId: user.id, role: user.role };
      return json({
        data: {
          accessToken,
          tokenType: 'Bearer',
          expiresIn: 900,
          user: { id: user.id, email: user.email, fullName: user.fullName, phone: user.phone, role: user.role },
        },
      });
    }

    if (path === '/auth/refresh' && method === 'POST') {
      if (options.credentials !== 'include' || !refreshCookie) {
        return problem(401, 'Refresh session is invalid or expired');
      }
      const user = state.users.find((u) => u.id === refreshCookie.userId);
      const accessToken = `access-${user.id}-${tokenSeq++}`;
      const refresh = `refresh-${user.id}-${tokenSeq++}`;
      sessions.set(accessToken, { userId: user.id, role: user.role });
      refreshCookie = { token: refresh, userId: user.id, role: user.role };
      return json({
        data: {
          accessToken,
          tokenType: 'Bearer',
          expiresIn: 900,
          user: { id: user.id, email: user.email, fullName: user.fullName, phone: user.phone, role: user.role },
        },
      });
    }

    if (path === '/auth/logout' && method === 'POST') {
      if (bearer) sessions.delete(bearer);
      refreshCookie = null;
      return json(null, 204);
    }

    // UserController
    if (path === '/users/me') {
      if (!principal) return problem(401, 'Authentication required');
      const user = state.users.find((u) => u.id === principal.userId);
      if (!user) return notFound('User not found');
      if (method === 'PUT') {
        user.fullName = body.fullName;
        user.phone = body.phone;
      }
      return json({
        data: { id: user.id, email: user.email, fullName: user.fullName, phone: user.phone, role: user.role },
      });
    }

    if (path === '/sport-types' && method === 'GET') {
      return state.sports.length ? json(state.sports) : notFound('No sport types found');
    }

    if (path === '/venues' && method === 'GET') {
      const q = (url.searchParams.get('q') || '').trim().toLowerCase();
      const district = (url.searchParams.get('district') || '').trim().toLowerCase();
      const city = (url.searchParams.get('city') || '').trim().toLowerCase();
      const sportTypeId = url.searchParams.get('sportTypeId');
      const maxPrice = url.searchParams.get('maxPrice');
      const date = url.searchParams.get('date');
      const from = url.searchParams.get('from');
      const to = url.searchParams.get('to');

      const matched = state.venues.filter((v) => {
        if (v.status !== 'ACTIVE') return false;
        if (city && v.city.toLowerCase() !== city) return false;
        if (district && v.district.toLowerCase() !== district) return false;
        const venueCourts = state.courts.filter((c) => c.venue.id === v.id && c.status === 'AVAILABLE');
        const matchesKeyword =
          !q ||
          `${v.name} ${v.address} ${v.district} ${v.city}`.toLowerCase().includes(q) ||
          venueCourts.some((c) => c.name.toLowerCase().includes(q));
        if (!matchesKeyword) return false;
        if (!sportTypeId && !maxPrice && !date) return true;
        return venueCourts.some((c) => {
          if (c.basePricePerHour == null || c.basePricePerHour <= 0) return false;
          if (sportTypeId && String(c.sportType.id) !== sportTypeId) return false;
          if (!date) {
            return !maxPrice || c.basePricePerHour <= Number(maxPrice);
          }
          const startStr = `${date}T${from || '06:00'}:00+07:00`;
          const endStr = `${date}T${to || '23:00'}:00+07:00`;
          const sMs = Date.parse(startStr);
          const eMs = Date.parse(endStr);
          for (let t = sMs; t + 1800000 <= eMs; t += 1800000) {
            const hour = Number(toLocalIso(t).slice(11, 13));
            const eff = c.basePricePerHour + (hour >= 17 && hour < 22 ? 100000 : 0);
            if ((!maxPrice || eff <= Number(maxPrice)) && !hasOverlap(c.id, t, t + 1800000)) {
              return true;
            }
          }
          return false;
        });
      });

      return json({
        content: matched,
        totalElements: matched.length,
        totalPages: 1,
        number: 0,
        size: 100,
      });
    }

    if (path === '/owner/venues' && method === 'GET') {
      if (!principal) return problem(401, 'Authentication required');
      if (principal.role !== 'OWNER') return problem(403, 'Access denied');
      return json(state.venues.filter((v) => v.owner.id === principal.userId));
    }

    if (/^\/venues\/\d+$/.test(path) && method === 'GET') {
      const id = Number(path.split('/')[2]);
      const v = state.venues.find((x) => x.id === id);
      return v ? json(v) : notFound('Venue not found');
    }

    if (/^\/venues\/\d+\/courts$/.test(path)) {
      const vid = Number(path.split('/')[2]);
      if (method === 'GET') {
        const list = state.courts.filter((c) => c.venue.id === vid);
        return list.length ? json(list) : notFound('No courts found');
      }
      if (method === 'POST') {
        if (!principal) return problem(401, 'Authentication required');
        if (principal.role !== 'OWNER') return problem(403, 'Access denied');
        const created = {
          id: nextCourtId++,
          venue: { id: vid },
          sportType: state.sports.find((s) => s.id === body.sportType?.id) || state.sports[0],
          name: body.name,
          basePricePerHour: body.basePricePerHour ?? null,
          status: body.status || 'AVAILABLE',
        };
        state.courts.push(created);
        return json(created, 201);
      }
    }

    if (path === '/courts' && method === 'GET') {
      return json(state.courts);
    }

    if (/^\/courts\/\d+$/.test(path)) {
      const cid = Number(path.split('/')[2]);
      const c = state.courts.find((x) => x.id === cid);
      if (!c) return notFound('Court not found');
      if (method === 'GET') return json(c);
      if (method === 'PUT') {
        if (!principal) return problem(401, 'Authentication required');
        if (principal.role !== 'OWNER') return problem(403, 'Access denied');
        if (body.name !== undefined) c.name = body.name;
        if (body.status !== undefined) c.status = body.status;
        if (body.basePricePerHour !== undefined) c.basePricePerHour = body.basePricePerHour;
        return json(c);
      }
    }

    if (/^\/courts\/\d+\/availability$/.test(path) && method === 'GET') {
      const cid = Number(path.split('/')[2]);
      const c = state.courts.find((x) => x.id === cid);
      if (!c) return notFound('Court not found');
      const date = url.searchParams.get('date');
      const openMs = Date.parse(`${date}T06:00:00+07:00`);
      const closeMs = Date.parse(`${date}T23:00:00+07:00`);
      const slots = [];
      for (let t = openMs; t < closeMs; t += 1800000) {
        const priced = c.basePricePerHour != null && c.basePricePerHour > 0;
        slots.push({
          startTime: new Date(t).toISOString(),
          endTime: new Date(t + 1800000).toISOString(),
          available: priced && t > NOW && !hasOverlap(cid, t, t + 1800000),
        });
      }
      return json({
        courtId: String(cid),
        date,
        timezone: TIME_ZONE,
        slotMinutes: 30,
        slots,
      });
    }

    if (/^\/courts\/\d+\/quote$/.test(path) && method === 'GET') {
      const cid = Number(path.split('/')[2]);
      const c = state.courts.find((x) => x.id === cid);
      if (!c) return notFound('Court not found');
      const startMs = parseLocalMs(url.searchParams.get('start'));
      const endMs = parseLocalMs(url.searchParams.get('end'));
      const priced = c.basePricePerHour != null && c.basePricePerHour > 0;
      const pricing = priced ? priceInterval(c, startMs, endMs) : { total: null, lines: [] };
      return json({
        courtId: String(cid),
        startTime: new Date(startMs).toISOString(),
        endTime: new Date(endMs).toISOString(),
        durationMinutes: (endMs - startMs) / 60000,
        currency: 'VND',
        totalPrice: pricing.total,
        available: priced && startMs > NOW && !hasOverlap(cid, startMs, endMs),
        lines: pricing.lines,
      });
    }

    if (path === '/users/me/bookings' && method === 'GET') {
      if (!principal) return problem(401, 'Authentication required');
      if (principal.role !== 'CUSTOMER') return problem(403, 'Access denied');
      const rows = state.bookings.filter((b) => b.user.id === principal.userId);
      return rows.length
        ? json({ content: rows, totalElements: rows.length, totalPages: 1 })
        : notFound('Booking not found');
    }

    if (/^\/venues\/\d+\/bookings$/.test(path) && method === 'GET') {
      if (!principal) return problem(401, 'Authentication required');
      if (principal.role !== 'OWNER') return problem(403, 'Access denied');
      const vid = Number(path.split('/')[2]);
      const rows = state.bookings.filter((b) => b.court.venue.id === vid);
      return rows.length
        ? json({ content: rows, totalElements: rows.length, totalPages: 1 })
        : notFound('Booking not found');
    }

    if (path === '/bookings' && method === 'POST') {
      if (!principal) return problem(401, 'Authentication required');
      if (principal.role !== 'CUSTOMER') return problem(403, 'Access denied');
      assert.equal(body.user, undefined, 'POST /bookings must not send user');
      assert.equal(body.totalPrice, undefined, 'POST /bookings must not send totalPrice');
      const requestKey = reqHeaders.get('Idempotency-Key') || null;
      if (requestKey) {
        const existing = state.bookings.find(
          (b) => b.user.id === principal.userId && b.requestKey === requestKey,
        );
        if (existing) {
          if (
            existing.court.id !== body.court.id ||
            existing.startTime !== body.startTime ||
            existing.endTime !== body.endTime
          ) {
            return problem(409, 'Idempotency-Key is already used for another booking');
          }
          return json(existing, 201);
        }
      }
      const court = state.courts.find((c) => c.id === body.court.id);
      if (!court) return notFound('Court not found');
      if (court.basePricePerHour == null || court.basePricePerHour <= 0) {
        return problem(409, 'Court has no configured price');
      }
      const startMs = parseLocalMs(body.startTime);
      const endMs = parseLocalMs(body.endTime);
      if (hasOverlap(court.id, startMs, endMs)) {
        return problem(409, 'Booking time overlaps with an existing booking');
      }
      const { total } = priceInterval(court, startMs, endMs);
      const created = {
        id: nextBookingId++,
        user: state.users.find((u) => u.id === principal.userId),
        court,
        startTime: body.startTime,
        endTime: body.endTime,
        totalPrice: total,
        status: 'PENDING',
        expiresAt: toLocalIso(Math.min(NOW + 30 * 60000, startMs)),
        checkedInAt: null,
        checkedOutAt: null,
        requestKey,
      };
      state.bookings.push(created);
      return json(created, 201);
    }

    if (/^\/bookings\/\d+$/.test(path) && method === 'GET') {
      if (!principal) return problem(401, 'Authentication required');
      const bid = Number(path.split('/')[2]);
      const b = state.bookings.find((x) => x.id === bid);
      return b ? json(b) : notFound('Booking not found');
    }

    if (/^\/bookings\/\d+\/(confirm|complete|cancel)$/.test(path) && method === 'PATCH') {
      if (!principal) return problem(401, 'Authentication required');
      const [, , bidStr, action] = path.split('/');
      if (action !== 'cancel' && principal.role !== 'OWNER') return problem(403, 'Access denied');
      const b = state.bookings.find((x) => x.id === Number(bidStr));
      if (!b) return notFound('Booking not found');
      b.status =
        action === 'confirm' ? 'CONFIRMED' : action === 'complete' ? 'COMPLETED' : 'CANCELLED';
      return new Response(null, { status: 200 });
    }

    if (/^\/bookings\/\d+\/payments$/.test(path)) {
      if (!principal) return problem(401, 'Authentication required');
      const bid = Number(path.split('/')[2]);
      if (method === 'GET') {
        const list = state.payments.filter((p) => p.booking.id === bid);
        return list.length ? json(list) : notFound('Payment not found');
      }
      if (method === 'POST') {
        if (principal.role !== 'CUSTOMER') return problem(403, 'Access denied');
        const b = state.bookings.find((x) => x.id === bid);
        if (!b) return notFound('Booking not found');
        if (b.status !== 'CONFIRMED') return problem(409, 'Only confirmed bookings can be paid');
        const created = {
          id: nextPaymentId++,
          booking: { id: bid },
          amount: b.totalPrice,
          method: body.method,
          transactionRef: `TXN-${nextPaymentId}`,
          paidAt: toLocalIso(NOW),
        };
        state.payments.push(created);
        return json(created, 201);
      }
    }

    if (/^\/venues\/\d+\/reviews$/.test(path)) {
      const vid = Number(path.split('/')[2]);
      if (method === 'GET') {
        const rows = state.reviews.filter((r) => r.venue.id === vid);
        return json({ content: rows, totalElements: rows.length, totalPages: 1 });
      }
      if (method === 'POST') {
        if (!principal) return problem(401, 'Authentication required');
        if (principal.role !== 'CUSTOMER') return problem(403, 'Access denied');
        assert.equal(body.venue, undefined, 'POST review must send flat bookingId');
        const user = state.users.find((u) => u.id === principal.userId);
        const created = {
          id: nextReviewId++,
          venue: { id: vid },
          user: { id: user.id, fullName: user.fullName },
          booking: { id: body.bookingId },
          rating: body.rating,
          comment: body.comment,
          createdAt: '2026-11-10T21:00:00',
        };
        state.reviews.push(created);
        return json(created, 201);
      }
    }

    if (path === '/reviews' && method === 'GET') {
      return json(state.reviews);
    }

    if (/^\/reviews\/\d+$/.test(path) && method === 'PUT') {
      if (!principal) return problem(401, 'Authentication required');
      const rid = Number(path.split('/')[2]);
      const r = state.reviews.find((x) => x.id === rid);
      if (!r) return notFound('Review not found');
      r.rating = body.rating;
      r.comment = body.comment;
      return json({
        id: r.id,
        venue: r.venue,
        booking: r.booking,
        user: r.user,
        rating: r.rating,
        comment: r.comment,
        createdAt: r.createdAt,
      });
    }

    // Venue services & assets
    if (/^\/venues\/\d+\/services$/.test(path)) {
      const vid = Number(path.split('/')[2]);
      if (method === 'GET') {
        const isOwner = principal?.role === 'OWNER';
        const rows = state.services
          .filter((s) => s.venueId === vid && (isOwner || s.active))
          .map((s) => ({
            id: s.id,
            name: s.name,
            kind: s.kind,
            unitPrice: s.unitPrice,
            active: s.active,
            stockOnHand: isOwner ? s.stockOnHand : null,
            availableStock: s.stockOnHand,
          }));
        return json(rows);
      }
      if (method === 'POST') {
        if (!principal || principal.role !== 'OWNER') return problem(403, 'Access denied');
        if (body.kind === 'RENTAL' && body.stockOnHand !== undefined) {
          return problem(400, 'stockOnHand must be omitted for rentals');
        }
        const svc = {
          id: nextServiceId++,
          venueId: vid,
          name: body.name,
          kind: body.kind,
          unitPrice: body.unitPrice,
          active: true,
          stockOnHand: body.kind === 'CONSUMABLE' ? body.stockOnHand : null,
        };
        state.services.push(svc);
        return json({ ...svc, availableStock: svc.stockOnHand }, 201);
      }
    }

    if (/^\/venues\/\d+\/services\/\d+$/.test(path) && method === 'PUT') {
      if (!principal || principal.role !== 'OWNER') return problem(403, 'Access denied');
      const sid = Number(path.split('/')[4]);
      const svc = state.services.find((s) => s.id === sid);
      if (!svc) return notFound('Service not found');
      if (svc.kind === 'RENTAL' && body.stockOnHand !== undefined) {
        return problem(400, 'stockOnHand must be omitted for rentals');
      }
      svc.name = body.name;
      svc.unitPrice = body.unitPrice;
      svc.active = body.active;
      if (svc.kind === 'CONSUMABLE') svc.stockOnHand = body.stockOnHand;
      return json({ ...svc, availableStock: svc.stockOnHand });
    }

    if (/^\/venues\/\d+\/services\/\d+\/assets$/.test(path)) {
      if (!principal || principal.role !== 'OWNER') return problem(403, 'Access denied');
      const sid = Number(path.split('/')[4]);
      if (method === 'GET') return json(state.assets.filter((a) => a.serviceId === sid));
      if (method === 'POST') {
        const asset = {
          id: nextAssetId++,
          serviceId: sid,
          code: body.code.toUpperCase(),
          state: 'AVAILABLE',
        };
        state.assets.push(asset);
        return json(asset, 201);
      }
    }

    if (/^\/venues\/\d+\/services\/\d+\/assets\/\d+$/.test(path) && method === 'PUT') {
      if (!principal || principal.role !== 'OWNER') return problem(403, 'Access denied');
      const aid = Number(path.split('/')[6]);
      const asset = state.assets.find((a) => a.id === aid);
      if (!asset) return notFound('Asset not found');
      asset.state = body.state;
      return json(asset);
    }

    function bookingServicesPayload(b) {
      const orders = state.orders
        .filter((o) => o.bookingId === b.id)
        .map((o) => ({
          ...o,
          rentals: state.rentals.filter((r) => r.orderId === o.id),
        }));
      const deliveredServicesTotal = orders
        .filter((o) => o.deliveredAt)
        .reduce((sum, o) => sum + o.total, 0);
      return {
        bookingId: b.id,
        checkedInAt: b.checkedInAt,
        checkedOutAt: b.checkedOutAt,
        courtTotal: b.totalPrice,
        deliveredServicesTotal,
        total: b.totalPrice + deliveredServicesTotal,
        orders,
      };
    }

    if (/^\/bookings\/\d+\/services$/.test(path)) {
      if (!principal) return problem(401, 'Authentication required');
      const bid = Number(path.split('/')[2]);
      const b = state.bookings.find((x) => x.id === bid);
      if (!b) return notFound('Booking not found');
      if (method === 'GET') return json(bookingServicesPayload(b));
      if (method === 'POST') {
        if (principal.role !== 'CUSTOMER') return problem(403, 'Access denied');
        const key = reqHeaders.get('Idempotency-Key');
        if (!key) return problem(400, 'Idempotency-Key required');
        const svc = state.services.find((s) => s.id === body.serviceId);
        if (!svc) return notFound('Service not found');
        const ord = {
          id: nextOrderId++,
          bookingId: bid,
          serviceId: svc.id,
          itemName: svc.name,
          kind: svc.kind,
          quantity: body.quantity,
          unitPrice: svc.unitPrice,
          total: svc.unitPrice * body.quantity,
          status: 'RESERVED',
          orderedAt: toLocalIso(NOW),
          deliveredAt: null,
          deliveredBy: null,
          cancelledAt: null,
        };
        state.orders.push(ord);
        let rentals = [];
        if (svc.kind === 'RENTAL') {
          const availAssets = state.assets.filter(
            (a) => a.serviceId === svc.id && a.state === 'AVAILABLE',
          );
          rentals = availAssets.slice(0, body.quantity).map((a) => {
            const rental = {
              id: nextRentalId++,
              orderId: ord.id,
              assetId: a.id,
              assetCode: a.code,
              handedOutAt: null,
              returnedAt: null,
              returnedBy: null,
            };
            state.rentals.push(rental);
            return rental;
          });
        }
        return json({ ...ord, rentals }, 201);
      }
    }

    if (/^\/bookings\/\d+\/services\/\d+$/.test(path) && method === 'DELETE') {
      if (!principal) return problem(401, 'Authentication required');
      const oid = Number(path.split('/')[4]);
      const ord = state.orders.find((o) => o.id === oid);
      if (!ord) return notFound('Order not found');
      ord.status = 'RELEASED';
      ord.cancelledAt = toLocalIso(NOW);
      return json(null, 204);
    }

    if (/^\/bookings\/\d+\/services\/\d+\/deliver$/.test(path) && method === 'POST') {
      if (!principal || principal.role !== 'OWNER') return problem(403, 'Access denied');
      const oid = Number(path.split('/')[4]);
      const ord = state.orders.find((o) => o.id === oid);
      if (!ord) return notFound('Order not found');
      ord.status = 'DELIVERED';
      ord.deliveredAt = toLocalIso(NOW);
      ord.deliveredBy = principal.userId;
      const rentals = state.rentals.filter((r) => r.orderId === ord.id);
      if (ord.kind === 'RENTAL') {
        assert.deepEqual(
          body.assetIds,
          rentals.map((r) => r.assetId),
        );
        rentals.forEach((r) => (r.handedOutAt = toLocalIso(NOW)));
      }
      return json({ ...ord, rentals });
    }

    if (/^\/bookings\/\d+\/check-in$/.test(path) && method === 'POST') {
      if (!principal || principal.role !== 'OWNER') return problem(403, 'Access denied');
      const bid = Number(path.split('/')[2]);
      const b = state.bookings.find((x) => x.id === bid);
      if (!b) return notFound('Booking not found');
      b.checkedInAt = toLocalIso(NOW);
      return json(bookingServicesPayload(b));
    }

    if (/^\/bookings\/\d+\/check-out$/.test(path) && method === 'POST') {
      if (!principal) return problem(401, 'Authentication required');
      const bid = Number(path.split('/')[2]);
      const b = state.bookings.find((x) => x.id === bid);
      if (!b) return notFound('Booking not found');
      b.checkedOutAt = toLocalIso(NOW);
      b.status = 'COMPLETED';
      return json(bookingServicesPayload(b));
    }

    if (/^\/rentals\/\d+\/photos$/.test(path)) {
      if (!principal) return problem(401, 'Authentication required');
      const rid = Number(path.split('/')[2]);
      if (method === 'GET') return json(state.photos.filter((p) => p.rentalId === rid));
      if (method === 'POST') {
        assert.equal(isFormData, true, 'Upload must send multipart FormData');
        assert.equal(reqHeaders.get('Content-Type'), null, 'Must not force JSON Content-Type');
        const file = body.get('file');
        const bytes = new Uint8Array(await file.arrayBuffer());
        const ph = {
          id: nextPhotoId++,
          rentalId: rid,
          phase: body.get('phase'),
          angle: body.get('angle'),
          uploadedAt: toLocalIso(NOW),
          bytes,
          contentType: file.type || 'image/png',
        };
        state.photos.push(ph);
        return json({ id: ph.id, phase: ph.phase, angle: ph.angle, uploadedAt: ph.uploadedAt });
      }
    }

    if (/^\/rentals\/\d+\/photos\/\d+\/link$/.test(path) && method === 'GET') {
      if (!principal) return problem(401, 'Authentication required');
      const pid = Number(path.split('/')[4]);
      const ph = state.photos.find((p) => p.id === pid);
      if (!ph) return notFound('Photo not found');
      return json({
        url: `https://res.cloudinary.com/demo/image/authenticated/${pid}.png`,
        expiresAt: '2026-11-09T09:05:00Z',
      });
    }

    if (/^\/rentals\/\d+\/photos\/\d+$/.test(path) && method === 'GET') {
      if (!principal) return problem(401, 'Authentication required');
      const pid = Number(path.split('/')[4]);
      const ph = state.photos.find((p) => p.id === pid);
      if (!ph) return notFound('Photo not found');
      return new Response(ph.bytes, { status: 200, headers: { 'Content-Type': ph.contentType } });
    }

    return notFound(`Unhandled ${method} ${path}`);
  }

  return { fetchImpl, calls, state };
}

test('backend adapter connects to real /auth/*, Bearer headers, Idempotency-Key, /venues search, court pricing, availability and quote', async () => {
  const spring = createMockSpringBackend();
  const adapter = createBackendTransport({ fetchImpl: spring.fetchImpl, now: () => NOW });

  async function call(path, { token, headers = {}, ...options } = {}) {
    const res = await adapter.send(
      path,
      {
        ...options,
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...headers,
        },
      },
      '/api/v1',
    );
    const body = res.status === 204 ? null : await res.json();
    return { status: res.status, body };
  }

  // 1. Sport types & server-backed venue search (q, district, date, from, to, maxPrice)
  const sports = await call('/sport-types');
  assert.equal(sports.status, 200);
  assert.deepEqual(sports.body.data, [{ id: '1', name: 'Bóng đá' }]);

  const tomorrow = addDays(localDate(NOW), 1);
  const venues = await call(
    `/venues?keyword=M%E1%BB%B9%20%C4%90%C3%ACnh&district=Nam%20T%E1%BB%AB%20Li%C3%AAm&date=${tomorrow}&from=18:00&to=20:00&maxPrice=400000`,
  );
  assert.equal(venues.status, 200);
  assert.equal(venues.body.data.length, 1);
  assert.equal(venues.body.data[0].minPrice, 300000);
  const venueSearchCall = spring.calls.find((c) => c.method === 'GET' && c.path === '/venues');
  assert.ok(venueSearchCall.search.includes('q='));
  assert.ok(venueSearchCall.search.includes('from=18%3A00'));
  assert.ok(venueSearchCall.search.includes('maxPrice=400000'));

  // 2. Priced & unpriced courts use backend /availability and /quote directly
  const availPriced = await call(`/courts/10/availability?date=${tomorrow}`);
  assert.equal(availPriced.status, 200);
  assert.equal(availPriced.body.data.slots[0].available, true);

  const availUnpriced = await call(`/courts/11/availability?date=${tomorrow}`);
  assert.equal(availUnpriced.status, 200);
  assert.equal(availUnpriced.body.data.slots[0].available, false);

  const start = slotStart(tomorrow, 18); // peak hour 18:00-19:00
  const end = slotStart(tomorrow, 19);
  const quotePriced = await call(
    `/courts/10/quote?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`,
  );
  assert.equal(quotePriced.status, 200);
  assert.equal(quotePriced.body.data.totalPrice, 400000);
  assert.equal(quotePriced.body.data.available, true);

  const quoteUnpriced = await call(
    `/courts/11/quote?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`,
  );
  assert.equal(quoteUnpriced.status, 200);
  assert.equal(quoteUnpriced.body.data.totalPrice, null);
  assert.equal(quoteUnpriced.body.data.available, false);

  // 3. Real /auth/register, /auth/login, /auth/refresh, and /users/me with Bearer token
  const reg = await call('/auth/register', {
    method: 'POST',
    body: {
      email: 'newplayer@keosan.demo',
      password: 'Password123',
      fullName: 'Tân Binh',
      phone: '0912345678',
    },
  });
  assert.equal(reg.status, 201);
  assert.equal(reg.body.data.role, 'CUSTOMER');

  const login = await call('/auth/login', {
    method: 'POST',
    body: { email: 'player@keosan.demo', password: 'Demo@12345' },
  });
  assert.equal(login.status, 200);
  const playerToken = login.body.data.accessToken;
  assert.ok(playerToken);

  const refreshed = await call('/auth/refresh', { method: 'POST' });
  assert.equal(refreshed.status, 200);
  const activePlayerToken = refreshed.body.data.accessToken;
  assert.notEqual(activePlayerToken, playerToken);

  const profileUpdate = await call('/users/me', {
    method: 'PUT',
    token: activePlayerToken,
    body: { fullName: 'Minh Anh Updated', phone: '0911111111' },
  });
  assert.equal(profileUpdate.status, 200);
  assert.equal(profileUpdate.body.data.fullName, 'Minh Anh Updated');

  // 4. Empty bookings 404 -> empty list without hitting closed GET /bookings
  const emptyBookings = await call('/users/me/bookings', { token: activePlayerToken });
  assert.equal(emptyBookings.status, 200);
  assert.deepEqual(emptyBookings.body.data, []);

  // 5. Create booking forwards Idempotency-Key and omits server-computed fields
  const idemKey = 'booking-idem-1';
  const createdBooking = await call('/bookings', {
    method: 'POST',
    token: activePlayerToken,
    headers: { 'Idempotency-Key': idemKey },
    body: { courtId: '10', startTime: start, endTime: end, note: 'Thuê bóng' },
  });
  assert.equal(createdBooking.status, 201);
  assert.equal(createdBooking.body.data.status, 'pending');
  assert.equal(createdBooking.body.data.totalPrice, 400000);

  const replayBooking = await call('/bookings', {
    method: 'POST',
    token: activePlayerToken,
    headers: { 'Idempotency-Key': idemKey },
    body: { courtId: '10', startTime: start, endTime: end },
  });
  assert.equal(replayBooking.status, 201);
  assert.equal(replayBooking.body.data.id, createdBooking.body.data.id);

  const conflictReplay = await call('/bookings', {
    method: 'POST',
    token: activePlayerToken,
    headers: { 'Idempotency-Key': idemKey },
    body: { courtId: '10', startTime: slotStart(tomorrow, 19), endTime: slotStart(tomorrow, 20) },
  });
  assert.equal(conflictReplay.status, 409);

  // 6. Owner login, /owner/venues, court basePricePerHour CRUD, confirm -> pay -> complete -> review
  const ownerLogin = await call('/auth/login', {
    method: 'POST',
    body: { email: 'owner@keosan.demo', password: 'Demo@12345' },
  });
  const ownerToken = ownerLogin.body.data.accessToken;

  const ownerVenues = await call('/owner/venues', { token: ownerToken });
  assert.equal(ownerVenues.status, 200);
  assert.equal(ownerVenues.body.data.length, 1);

  const updatedCourt = await call('/courts/11', {
    method: 'PUT',
    token: ownerToken,
    body: { name: 'Sân số 2 (5 người)', sportTypeId: '1', basePricePerHour: 220000, status: 'active' },
  });
  assert.equal(updatedCourt.status, 200);
  assert.equal(updatedCourt.body.data.basePricePerHour, 220000);

  const confirmed = await call(`/bookings/${createdBooking.body.data.id}/confirm`, {
    method: 'POST',
    token: ownerToken,
  });
  assert.equal(confirmed.status, 200);
  assert.equal(confirmed.body.data.status, 'confirmed');

  const payment = await call(`/bookings/${createdBooking.body.data.id}/payments`, {
    method: 'POST',
    token: activePlayerToken,
    body: { method: 'momo' },
  });
  assert.equal(payment.status, 201);
  assert.equal(payment.body.data.method, 'momo');
  assert.equal(payment.body.data.amount, 400000);

  const completed = await call(`/bookings/${createdBooking.body.data.id}/complete`, {
    method: 'POST',
    token: ownerToken,
  });
  assert.equal(completed.status, 200);
  assert.equal(completed.body.data.status, 'completed');
  assert.equal(completed.body.data.paymentStatus, 'paid');

  const review = await call('/venues/1/reviews', {
    method: 'POST',
    token: activePlayerToken,
    body: { bookingId: createdBooking.body.data.id, rating: 5, comment: 'Sân đẹp, đèn sáng.' },
  });
  assert.equal(review.status, 201);
  assert.equal(review.body.data.rating, 5);

  const logout = await call('/auth/logout', { method: 'POST', token: ownerToken });
  assert.equal(logout.status, 204);

  // Confirm no closed legacy routes were ever called.
  assert.equal(
    spring.calls.some(
      (c) =>
        (c.path === '/users/me' && (c.method === 'POST' || c.search.includes('id='))) ||
        (c.path === '/bookings' && c.method === 'GET') ||
        (c.path === '/payments' && c.method === 'GET') ||
        /^\/owner\/venues\/\d+$/.test(c.path),
    ),
    false,
  );
});

test('backend adapter handles all 16 addon, check-in, check-out and Cloudinary rental photo endpoints', async () => {
  const spring = createMockSpringBackend();
  const adapter = createBackendTransport({ fetchImpl: spring.fetchImpl, now: () => NOW });

  async function call(path, { token, headers = {}, ...options } = {}) {
    const res = await adapter.send(
      path,
      {
        ...options,
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...headers,
        },
      },
      '/api/v1',
    );
    const body = res.status === 204 ? null : await res.json();
    return { status: res.status, body };
  }

  const ownerToken = (
    await call('/auth/login', {
      method: 'POST',
      body: { email: 'owner@keosan.demo', password: 'Demo@12345' },
    })
  ).body.data.accessToken;
  const playerToken = (
    await call('/auth/login', {
      method: 'POST',
      body: { email: 'player@keosan.demo', password: 'Demo@12345' },
    })
  ).body.data.accessToken;

  // 1. Venue services & equipment assets (CONSUMABLE + RENTAL)
  const drink = await call('/venues/1/services', {
    method: 'POST',
    token: ownerToken,
    body: { name: 'Nước suối', kind: 'CONSUMABLE', unitPrice: 15000, stockOnHand: 20 },
  });
  assert.equal(drink.status, 201);
  assert.equal(drink.body.data.stockOnHand, 20);

  const rentalSvc = await call('/venues/1/services', {
    method: 'POST',
    token: ownerToken,
    body: { name: 'Thuê vợt', kind: 'RENTAL', unitPrice: 50000 },
  });
  assert.equal(rentalSvc.status, 201);
  assert.equal(rentalSvc.body.data.stockOnHand, null);

  const updatedDrink = await call(`/venues/1/services/${drink.body.data.id}`, {
    method: 'PUT',
    token: ownerToken,
    body: { name: 'Nước suối 500ml', unitPrice: 18000, active: true, stockOnHand: 25 },
  });
  assert.equal(updatedDrink.status, 200);
  assert.equal(updatedDrink.body.data.unitPrice, 18000);

  const asset = await call(`/venues/1/services/${rentalSvc.body.data.id}/assets`, {
    method: 'POST',
    token: ownerToken,
    body: { code: 'VOT-007' },
  });
  assert.equal(asset.status, 201);
  assert.equal(asset.body.data.code, 'VOT-007');

  const assetsList = await call(`/venues/1/services/${rentalSvc.body.data.id}/assets`, {
    token: ownerToken,
  });
  assert.equal(assetsList.status, 200);
  assert.equal(assetsList.body.data.length, 1);

  const assetMaint = await call(
    `/venues/1/services/${rentalSvc.body.data.id}/assets/${asset.body.data.id}`,
    { method: 'PUT', token: ownerToken, body: { state: 'MAINTENANCE' } },
  );
  assert.equal(assetMaint.body.data.state, 'MAINTENANCE');
  await call(`/venues/1/services/${rentalSvc.body.data.id}/assets/${asset.body.data.id}`, {
    method: 'PUT',
    token: ownerToken,
    body: { state: 'AVAILABLE' },
  });

  const catalog = await call('/venues/1/services');
  assert.equal(catalog.status, 200);
  assert.equal(catalog.body.data.length, 2);

  // 2. Book court, confirm, reserve consumable & rental orders, cancel consumable, check-in, deliver rental
  const tomorrow = addDays(localDate(NOW), 1);
  const booking = await call('/bookings', {
    method: 'POST',
    token: playerToken,
    headers: { 'Idempotency-Key': 'addon-booking-1' },
    body: {
      courtId: '10',
      startTime: slotStart(tomorrow, 18),
      endTime: slotStart(tomorrow, 19),
    },
  });
  const bid = booking.body.data.id;
  await call(`/bookings/${bid}/confirm`, { method: 'POST', token: ownerToken });

  const drinkOrder = await call(`/bookings/${bid}/services`, {
    method: 'POST',
    token: playerToken,
    headers: { 'Idempotency-Key': 'ord-drink-1' },
    body: { serviceId: drink.body.data.id, quantity: 2 },
  });
  assert.equal(drinkOrder.status, 201);
  const delDrink = await call(`/bookings/${bid}/services/${drinkOrder.body.data.id}`, {
    method: 'DELETE',
    token: playerToken,
  });
  assert.equal(delDrink.status, 204);

  const rentalOrder = await call(`/bookings/${bid}/services`, {
    method: 'POST',
    token: playerToken,
    headers: { 'Idempotency-Key': 'ord-rental-1' },
    body: { serviceId: rentalSvc.body.data.id, quantity: 1 },
  });
  assert.equal(rentalOrder.status, 201);
  const rentalId = rentalOrder.body.data.rentals[0].id;
  const assetId = Number(rentalOrder.body.data.rentals[0].assetId);

  const checkedIn = await call(`/bookings/${bid}/check-in`, { method: 'POST', token: ownerToken });
  assert.equal(checkedIn.status, 200);
  assert.ok(checkedIn.body.data.checkedInAt);

  const delivered = await call(`/bookings/${bid}/services/${rentalOrder.body.data.id}/deliver`, {
    method: 'POST',
    token: ownerToken,
    body: { assetIds: [assetId] },
  });
  assert.equal(delivered.status, 200);
  assert.equal(delivered.body.data.status, 'DELIVERED');

  // 3. Rental photos multipart upload, list, signed link & raw binary download
  const form = new FormData();
  form.append('phase', 'CHECK_IN');
  form.append('angle', 'OVERVIEW');
  form.append('file', new Blob([new Uint8Array([137, 80, 78, 71])], { type: 'image/png' }), 'test.png');

  const uploaded = await call(`/rentals/${rentalId}/photos`, {
    method: 'POST',
    token: playerToken,
    body: form,
  });
  assert.equal(uploaded.status, 200);
  assert.equal(uploaded.body.data.phase, 'CHECK_IN');
  const photoId = uploaded.body.data.id;

  const photoList = await call(`/rentals/${rentalId}/photos`, { token: ownerToken });
  assert.equal(photoList.body.data.length, 1);

  const photoLink = await call(`/rentals/${rentalId}/photos/${photoId}/link`, { token: ownerToken });
  assert.match(photoLink.body.data.url, /^https:\/\/res\.cloudinary\.com\//);

  const rawDownloadRes = await adapter.send(
    `/rentals/${rentalId}/photos/${photoId}`,
    { headers: { Authorization: `Bearer ${ownerToken}` } },
    '/api/v1',
  );
  assert.equal(rawDownloadRes.status, 200);
  assert.equal(rawDownloadRes.headers.get('Content-Type'), 'image/png');
  assert.equal((await rawDownloadRes.arrayBuffer()).byteLength, 4);

  // 4. Booking services summary & check-out
  const summary = await call(`/bookings/${bid}/services`, { token: playerToken });
  assert.equal(summary.body.data.deliveredServicesTotal, 50000);
  assert.equal(summary.body.data.total, 450000);

  const checkedOut = await call(`/bookings/${bid}/check-out`, { method: 'POST', token: playerToken });
  assert.equal(checkedOut.status, 200);
  assert.ok(checkedOut.body.data.checkedOutAt);
});
