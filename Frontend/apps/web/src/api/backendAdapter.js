import { localDate, addDays, clockTime, TIME_ZONE } from '../../../../packages/shared/time.js';

const STORAGE_USERS_KEY = 'keosan_backend_users';
const STORAGE_SESSION_KEY = 'keosan_backend_session';
const STORAGE_META_KEY = 'keosan_backend_meta';

const DEFAULT_DEMO_USERS = [
  {
    id: '1',
    email: 'player@keosan.demo',
    fullName: 'Minh Anh',
    phone: '0900000000',
    role: 'CUSTOMER',
    password: 'Demo@12345',
  },
  {
    id: '2',
    email: 'owner@keosan.demo',
    fullName: 'Quản lý sân',
    phone: '0900000001',
    role: 'OWNER',
    password: 'Demo@12345',
  },
  {
    id: '3',
    email: 'admin@keosan.demo',
    fullName: 'Quản trị viên',
    phone: '0900000002',
    role: 'ADMIN',
    password: 'Demo@12345',
  },
];

const IMAGE_KEYS = ['pitch-1', 'pitch-2', 'pitch-3'];

function createMemoryStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
  };
}

function resolveStorage(customStorage) {
  if (customStorage) return customStorage;
  try {
    if (typeof globalThis.localStorage !== 'undefined') return globalThis.localStorage;
  } catch {
    // Ignore storage access errors in restricted contexts.
  }
  return createMemoryStorage();
}

function normal(value) {
  return String(value || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .trim()
    .replace(/\s+/g, ' ');
}

function defaultImageKey(id) {
  const n = Number(id);
  if (Number.isFinite(n) && n > 0) return IMAGE_KEYS[(n - 1) % IMAGE_KEYS.length];
  return 'pitch-1';
}

function inferTeamSize(name) {
  const str = String(name || '');
  if (/\b11\b|11\s*người/i.test(str)) return 11;
  if (/\b7\b|7\s*người/i.test(str)) return 7;
  if (/\b5\b|5\s*người/i.test(str)) return 5;
  return 7;
}

function toLocalTime(value) {
  if (!value) return '06:00:00';
  const s = String(value).trim();
  if (/^\d{2}:\d{2}:\d{2}$/.test(s)) return s;
  if (/^\d{2}:\d{2}$/.test(s)) return `${s}:00`;
  return '06:00:00';
}

function fromLocalTime(value, fallback = '06:00') {
  if (Array.isArray(value) && value.length >= 2) {
    return `${String(value[0]).padStart(2, '0')}:${String(value[1]).padStart(2, '0')}`;
  }
  const s = String(value || '').trim();
  const match = s.match(/^(\d{2}:\d{2})/);
  return match ? match[1] : fallback;
}

function toLocalDateTime(isoOrDate) {
  if (!isoOrDate) return null;
  const d = new Date(isoOrDate);
  if (Number.isNaN(d.getTime())) return String(isoOrDate);
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(d);
  const get = (t) => parts.find((p) => p.type === t)?.value || '00';
  const hour = get('hour') === '24' ? '00' : get('hour');
  return `${get('year')}-${get('month')}-${get('day')}T${hour}:${get('minute')}:${get('second')}`;
}

function fromLocalDateTime(value) {
  if (!value) return null;
  if (Array.isArray(value) && value.length >= 5) {
    const [y, m, d, hh, mm, ss = 0] = value;
    const str = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}T${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')}+07:00`;
    return new Date(str).toISOString();
  }
  const s = String(value).trim();
  if (/(Z|[+-]\d{2}:\d{2})$/.test(s)) return new Date(s).toISOString();
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(s)) {
    return new Date(`${s}+07:00`).toISOString();
  }
  const parsed = Date.parse(s);
  return Number.isFinite(parsed) ? new Date(parsed).toISOString() : s;
}

function unwrapCollection(payload) {
  if (!payload) return [];
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload.content)) return payload.content;
  if (Array.isArray(payload.data)) return payload.data;
  return [];
}

function paginate(rows, url) {
  const page = Number(url.searchParams.get('page') || 1);
  const limit = Number(url.searchParams.get('limit') || 6);
  const safePage = Number.isInteger(page) && page >= 1 ? page : 1;
  const safeLimit = Number.isInteger(limit) && limit >= 1 && limit <= 50 ? limit : 6;
  const totalPages = Math.max(1, Math.ceil(rows.length / safeLimit));
  const link = (p) => {
    const u = new URL(url);
    u.searchParams.set('page', String(p));
    return `/api/v1${u.pathname}${u.search}`;
  };
  return {
    data: rows.slice((safePage - 1) * safeLimit, safePage * safeLimit),
    meta: { page: safePage, limit: safeLimit, total: rows.length, totalPages },
    links: {
      self: link(safePage),
      prev: safePage > 1 ? link(safePage - 1) : null,
      next: safePage * safeLimit < rows.length ? link(safePage + 1) : null,
    },
  };
}

function jsonResponse(data, status = 200, headers = {}) {
  return new Response(status === 204 ? null : JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...headers },
  });
}

function problemResponse(status, code, detail, instance = '') {
  return new Response(
    JSON.stringify({
      type: `urn:keosan:problem:${code.toLowerCase().replaceAll('_', '-')}`,
      title: 'Request failed',
      status,
      detail,
      code,
      instance,
    }),
    {
      status,
      headers: { 'Content-Type': 'application/problem+json', 'Cache-Control': 'no-store' },
    },
  );
}

export function createBackendTransport({ fetchImpl, storage: customStorage, now = () => Date.now() } = {}) {
  const storage = resolveStorage(customStorage);
  const venueByCourt = new Map();
  const CACHE_TTL_MS = 30000;
  let catalogCache = { timestamp: 0, items: null };
  let sportsCache = { timestamp: 0, data: null };

  function invalidateCache() {
    catalogCache = { timestamp: 0, items: null };
    sportsCache = { timestamp: 0, data: null };
  }

  function readJson(key, fallback) {
    try {
      const raw = storage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch {
      return fallback;
    }
  }

  function writeJson(key, value) {
    try {
      storage.setItem(key, JSON.stringify(value));
    } catch {
      // Ignore storage quota errors.
    }
  }

  function getUsers() {
    const stored = readJson(STORAGE_USERS_KEY, null);
    if (Array.isArray(stored) && stored.length) return stored;
    writeJson(STORAGE_USERS_KEY, DEFAULT_DEMO_USERS);
    return DEFAULT_DEMO_USERS.map((u) => ({ ...u }));
  }

  function saveUsers(users) {
    writeJson(STORAGE_USERS_KEY, users);
  }

  function getSession() {
    return readJson(STORAGE_SESSION_KEY, null);
  }

  function saveSession(session) {
    if (!session) storage.removeItem(STORAGE_SESSION_KEY);
    else writeJson(STORAGE_SESSION_KEY, session);
  }

  function getMeta() {
    return readJson(STORAGE_META_KEY, { venues: {}, courts: {}, bookings: {} });
  }

  function saveMeta(meta) {
    writeJson(STORAGE_META_KEY, meta);
  }

  function stripPassword(user) {
    if (!user) return null;
    const { password: _pw, passwordHash: _pwh, ...rest } = user;
    return { ...rest, id: String(rest.id) };
  }

  function currentUser(headers) {
    const authHeader = new Headers(headers).get('Authorization');
    const session = getSession();
    if (!session) return null;
    if (authHeader && session.accessToken && authHeader !== `Bearer ${session.accessToken}`) {
      return session.user || null;
    }
    return session.user || null;
  }

  async function callBackend(baseUrl, relPath, { method = 'GET', body, signal, allowNotFound = false } = {}) {
    const fn = fetchImpl || globalThis.fetch;
    const targetUrl = `${baseUrl.replace(/\/$/, '')}${relPath}`;
    const res = await fn(targetUrl, {
      method,
      signal,
      credentials: 'include',
      headers: {
        Accept: 'application/json, application/problem+json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    });

    if (res.status === 404 && allowNotFound) return null;
    if (res.status === 204) return null;

    const text = await res.text();
    let payload = null;
    if (text) {
      try {
        payload = JSON.parse(text);
      } catch {
        payload = { detail: text };
      }
    }

    if (!res.ok) {
      const detail =
        payload?.detail ||
        payload?.message ||
        payload?.error ||
        `Lỗi từ máy chủ (${res.status}).`;
      throw { status: res.status, code: `HTTP_${res.status}`, detail };
    }
    return payload;
  }

  function normalizeUser(raw, fallback) {
    if (!raw && !fallback) return null;
    const src = raw || fallback;
    return {
      id: String(src.id ?? fallback?.id ?? '1'),
      email: src.email || fallback?.email || '',
      fullName: src.fullName || fallback?.fullName || '',
      phone: src.phone || fallback?.phone || '',
      role: String(src.role || fallback?.role || 'CUSTOMER').toUpperCase(),
    };
  }

  function normalizeSport(s) {
    return { id: String(s.id), name: s.name };
  }

  function normalizeCourt(c, venueIdFallback, meta = getMeta()) {
    const id = String(c.id);
    const saved = meta.courts[id] || {};
    const resolvedVenueId = String(
      c.venue?.id ?? c.venueId ?? venueIdFallback ?? saved.venueId ?? venueByCourt.get(id) ?? '',
    );
    if (resolvedVenueId) venueByCourt.set(id, resolvedVenueId);
    const statusRaw = String(c.status || 'AVAILABLE').toUpperCase();
    return {
      id,
      venueId: resolvedVenueId,
      sportTypeId: String(c.sportType?.id ?? c.sportTypeId ?? saved.sportTypeId ?? ''),
      sportName: c.sportType?.name || saved.sportName || 'Bóng đá',
      name: c.name || saved.name || `Sân ${id}`,
      teamSize: saved.teamSize !== undefined ? saved.teamSize : inferTeamSize(c.name),
      basePricePerHour: Number(saved.basePricePerHour ?? c.basePricePerHour ?? 300000),
      description: saved.description || c.description || '',
      imageKey: saved.imageKey || c.imageKey || defaultImageKey(id),
      status: statusRaw === 'AVAILABLE' || statusRaw === 'ACTIVE' ? 'active' : 'inactive',
      version: Number(saved.version || 1),
    };
  }

  function normalizeVenue(v, courts = [], reviews = [], meta = getMeta()) {
    const id = String(v.id);
    const saved = meta.venues[id] || {};
    const activeCourts = courts.filter((c) => c.status === 'active');
    const courtTypes = [];
    activeCourts.forEach((c) => {
      const existing = courtTypes.find(
        (t) => t.sportTypeId === c.sportTypeId && t.teamSize === c.teamSize,
      );
      if (existing) existing.count++;
      else {
        courtTypes.push({
          sportTypeId: c.sportTypeId,
          sportName: c.sportName || 'Bóng đá',
          teamSize: c.teamSize,
          count: 1,
        });
      }
    });
    const statusRaw = String(v.status || 'ACTIVE').toUpperCase();
    return {
      id,
      ownerId: String(v.owner?.id ?? v.ownerId ?? saved.ownerId ?? '2'),
      name: v.name || '',
      address: v.address || '',
      district: v.district || '',
      city: v.city || 'Hà Nội',
      description: v.description || '',
      openTime: fromLocalTime(v.openTime, '06:00'),
      closeTime: fromLocalTime(v.closeTime, '23:00'),
      timezone: 'Asia/Ho_Chi_Minh',
      status: statusRaw === 'ACTIVE' ? 'active' : 'inactive',
      amenities: saved.amenities || ['parking', 'lights', 'water'],
      imageKey: saved.imageKey || defaultImageKey(id),
      version: Number(saved.version || 1),
      courtCount: activeCourts.length,
      courtTypes,
      minPrice: activeCourts.length
        ? Math.min(...activeCourts.map((c) => c.basePricePerHour))
        : null,
      rating: reviews.length
        ? reviews.reduce((sum, r) => sum + Number(r.rating || 0), 0) / reviews.length
        : 0,
      reviewCount: reviews.length,
    };
  }

  function normalizePayment(p, bookingIdFallback) {
    const id = String(p.id);
    const paidAt = p.paidAt ? fromLocalDateTime(p.paidAt) : null;
    return {
      id,
      bookingId: String(p.booking?.id ?? p.bookingId ?? bookingIdFallback ?? ''),
      amount: Number(p.amount || 0),
      currency: 'VND',
      method: String(p.method || 'BANK_TRANSFER').toLowerCase(),
      status: paidAt ? 'succeeded' : 'pending',
      transactionRef: p.transactionRef || null,
      paidAt,
      createdAt: paidAt || new Date(now()).toISOString(),
      checkoutUrl: null,
    };
  }

  function normalizeReview(r, venueIdFallback, reviewIdFallback) {
    const id = String(r.id ?? reviewIdFallback ?? '');
    return {
      id,
      venueId: String(r.venue?.id ?? r.venueId ?? venueIdFallback ?? ''),
      bookingId: String(r.booking?.id ?? r.bookingId ?? ''),
      userId: String(r.user?.id ?? r.userId ?? ''),
      fullName: r.user?.fullName || r.fullName || 'Người chơi',
      rating: Number(r.rating || 5),
      comment: r.comment || '',
      createdAt: r.createdAt ? fromLocalDateTime(r.createdAt) : new Date(now()).toISOString(),
      updatedAt: r.createdAt ? fromLocalDateTime(r.createdAt) : new Date(now()).toISOString(),
    };
  }

  function normalizeBooking(b, { court, venue, payments = [], review = null, meta = getMeta(), users = [] } = {}) {
    const id = String(b.id);
    const saved = meta.bookings[id] || {};
    const courtId = String(b.court?.id ?? court?.id ?? saved.courtId ?? '');
    const normCourt = court || (b.court ? normalizeCourt(b.court, undefined, meta) : null);
    const venueId = String(b.court?.venue?.id ?? venue?.id ?? normCourt?.venueId ?? saved.venueId ?? '');
    const status = String(b.status || 'PENDING').toLowerCase();
    const hasPaid = payments.some((p) => p.status === 'succeeded');
    const hasPending = payments.some((p) => p.status === 'pending');
    let paymentStatus = 'unpaid';
    if (status === 'cancelled' && hasPaid) paymentStatus = 'refund_pending';
    else if (hasPaid) paymentStatus = 'paid';
    else if (hasPending) paymentStatus = 'pending';

    const matchedUser = users.find((u) => String(u.id) === String(b.user?.id ?? saved.userId));
    const startTime = fromLocalDateTime(b.startTime);
    const endTime = fromLocalDateTime(b.endTime);

    return {
      id,
      code: `KS-${id.padStart(4, '0')}`,
      userId: String(b.user?.id ?? saved.userId ?? ''),
      courtId,
      venueId,
      venueName: b.court?.venue?.name || venue?.name || saved.venueName || `Cơ sở #${venueId || 1}`,
      courtName: b.court?.name || normCourt?.name || saved.courtName || `Sân #${courtId}`,
      teamSize: normCourt?.teamSize ?? saved.teamSize ?? inferTeamSize(b.court?.name),
      sportName: b.court?.sportType?.name || normCourt?.sportName || saved.sportName || 'Bóng đá',
      address: b.court?.venue?.address || venue?.address || saved.address || '',
      imageKey: normCourt?.imageKey || saved.imageKey || defaultImageKey(courtId || id),
      contactName: b.user?.fullName || matchedUser?.fullName || saved.contactName || 'Người chơi',
      contactPhone: b.user?.phone || matchedUser?.phone || saved.contactPhone || '',
      startTime,
      endTime,
      totalPrice: Number(b.totalPrice || 0),
      currency: 'VND',
      status,
      expiresAt: b.expiresAt ? fromLocalDateTime(b.expiresAt) : null,
      note: saved.note || '',
      cancelReason: saved.cancelReason || '',
      paymentStatus,
      review,
      createdAt: startTime || new Date(now()).toISOString(),
      updatedAt: startTime || new Date(now()).toISOString(),
    };
  }

  function computeQuote(court, venue, start, end, bookings = []) {
    const a = Date.parse(start);
    const z = Date.parse(end);
    const durationMinutes = (z - a) / 60000;
    if (
      !Number.isFinite(a) ||
      !Number.isFinite(z) ||
      a <= now() ||
      a % 1800000 ||
      z % 1800000 ||
      durationMinutes < 30 ||
      durationMinutes > 180 ||
      localDate(a) !== localDate(z) ||
      localDate(a) > addDays(localDate(now()), 30)
    ) {
      throw {
        status: 422,
        code: 'INVALID_INTERVAL',
        detail: 'Chọn 30–180 phút theo bước 30 phút, trong 30 ngày tới.',
      };
    }
    if (
      clockTime(a) < venue.openTime ||
      clockTime(z) > venue.closeTime ||
      court.status !== 'active' ||
      venue.status !== 'active'
    ) {
      throw {
        status: 409,
        code: 'CLOSED',
        detail: 'Khung giờ nằm ngoài thời gian hoạt động.',
      };
    }
    const lines = [];
    for (let t = a; t < z; t += 1800000) {
      const peak = clockTime(t) >= '17:00' && clockTime(t) < '22:00';
      lines.push({
        startTime: new Date(t).toISOString(),
        endTime: new Date(t + 1800000).toISOString(),
        amount: Math.round((court.basePricePerHour + (peak ? 100000 : 0)) / 2),
      });
    }
    const isBlocked = bookings.some(
      (b) =>
        String(b.courtId) === String(court.id) &&
        ['pending', 'confirmed'].includes(b.status) &&
        (!b.expiresAt || b.status !== 'pending' || Date.parse(b.expiresAt) > now()) &&
        Date.parse(b.startTime) < z &&
        Date.parse(b.endTime) > a,
    );
    return {
      courtId: court.id,
      startTime: new Date(a).toISOString(),
      endTime: new Date(z).toISOString(),
      durationMinutes,
      currency: 'VND',
      totalPrice: lines.reduce((sum, l) => sum + l.amount, 0),
      available: !isBlocked,
      lines,
    };
  }

  function findCachedVenueEntry(venueId) {
    if (!catalogCache.items || now() - catalogCache.timestamp >= CACHE_TTL_MS) return null;
    return catalogCache.items.find((x) => x.venue.id === String(venueId)) || null;
  }

  function findCachedCourtEntry(courtId) {
    if (!catalogCache.items || now() - catalogCache.timestamp >= CACHE_TTL_MS) return null;
    const cid = String(courtId);
    for (const item of catalogCache.items) {
      const court = item.courts.find((c) => c.id === cid);
      if (court) return { court, venue: item.venue };
    }
    return null;
  }

  async function hydrateVenues(baseUrl, rawVenues, signal) {
    const meta = getMeta();
    return Promise.all(
      rawVenues.map(async (rawV) => {
        const vid = String(rawV.id);
        const [rawCourts, rawReviews] = await Promise.all([
          callBackend(baseUrl, `/venues/${vid}/courts`, { signal, allowNotFound: true }),
          callBackend(baseUrl, `/venues/${vid}/reviews?page=0&size=50`, { signal, allowNotFound: true }),
        ]);
        const courts = unwrapCollection(rawCourts).map((c) => normalizeCourt(c, vid, meta));
        const reviews = unwrapCollection(rawReviews).map((r) => normalizeReview(r, vid));
        return normalizeVenue(rawV, courts, reviews, meta);
      }),
    );
  }

  async function hydrateBookings(baseUrl, rawBookings, signal) {
    if (!rawBookings?.length) return [];
    const meta = getMeta();
    const users = getUsers();
    const courtCache = new Map();
    const venueCache = new Map();

    // Seed caches from the fresh venue catalog when available
    if (catalogCache.items && now() - catalogCache.timestamp < CACHE_TTL_MS) {
      for (const item of catalogCache.items) {
        venueCache.set(item.venue.id, item.venue);
        for (const c of item.courts) courtCache.set(c.id, c);
      }
    }

    const missingCourtIds = new Set();
    const missingVenueIds = new Set();

    for (const rawB of rawBookings) {
      const cid = String(rawB.court?.id ?? meta.bookings[String(rawB.id)]?.courtId ?? '');
      if (rawB.court?.name) {
        courtCache.set(cid, normalizeCourt(rawB.court, undefined, meta));
      } else if (cid && !courtCache.has(cid)) {
        missingCourtIds.add(cid);
      }

      const vid = String(
        rawB.court?.venue?.id ?? courtCache.get(cid)?.venueId ?? meta.bookings[String(rawB.id)]?.venueId ?? '',
      );
      if (rawB.court?.venue?.name) {
        venueCache.set(vid, normalizeVenue(rawB.court.venue, [], [], meta));
      } else if (vid && !venueCache.has(vid)) {
        missingVenueIds.add(vid);
      }
    }

    // Fetch missing courts and payments in bulk/parallel instead of per-booking N+1 calls
    const courtFetchPromise = Promise.all(
      [...missingCourtIds].map(async (cid) => {
        const fetchedC = await callBackend(baseUrl, `/courts/${cid}`, { signal, allowNotFound: true });
        if (!fetchedC) {
          courtCache.set(cid, null);
          return;
        }
        const normC = normalizeCourt(fetchedC, undefined, meta);
        courtCache.set(cid, normC);
        if (fetchedC.venue?.name) {
          venueCache.set(String(fetchedC.venue.id), normalizeVenue(fetchedC.venue, [], [], meta));
        } else if (normC.venueId && !venueCache.has(normC.venueId)) {
          missingVenueIds.add(normC.venueId);
        }
      }),
    );

    const paymentsPromise = (async () => {
      const byBooking = new Map();
      if (rawBookings.length === 1) {
        const rawPayments = await callBackend(baseUrl, `/bookings/${rawBookings[0].id}/payments`, {
          signal,
          allowNotFound: true,
        });
        byBooking.set(
          String(rawBookings[0].id),
          unwrapCollection(rawPayments).map((p) => normalizePayment(p, rawBookings[0].id)),
        );
        return byBooking;
      }

      const bulkPayments = await callBackend(baseUrl, '/payments', { signal, allowNotFound: true }).catch(
        () => null,
      );
      if (Array.isArray(bulkPayments)) {
        for (const p of bulkPayments) {
          const bid = String(p.booking?.id ?? p.bookingId ?? '');
          if (!bid) continue;
          if (!byBooking.has(bid)) byBooking.set(bid, []);
          byBooking.get(bid).push(normalizePayment(p, bid));
        }
        return byBooking;
      }

      // Fall back to per-booking payment lookup only if bulk /payments is unsupported (404)
      await Promise.all(
        rawBookings.map(async (rawB) => {
          const rawPayments = await callBackend(baseUrl, `/bookings/${rawB.id}/payments`, {
            signal,
            allowNotFound: true,
          });
          byBooking.set(
            String(rawB.id),
            unwrapCollection(rawPayments).map((p) => normalizePayment(p, rawB.id)),
          );
        }),
      );
      return byBooking;
    })();

    const [, paymentsByBooking] = await Promise.all([courtFetchPromise, paymentsPromise]);

    if (missingVenueIds.size) {
      await Promise.all(
        [...missingVenueIds]
          .filter((vid) => !venueCache.has(vid))
          .map(async (vid) => {
            const fetchedV = await callBackend(baseUrl, `/venues/${vid}`, { signal, allowNotFound: true });
            venueCache.set(vid, fetchedV ? normalizeVenue(fetchedV, [], [], meta) : null);
          }),
      );
    }

    return rawBookings.map((rawB) => {
      const cid = String(rawB.court?.id ?? meta.bookings[String(rawB.id)]?.courtId ?? '');
      const court = courtCache.get(cid) || null;
      const vid = String(
        rawB.court?.venue?.id ?? court?.venueId ?? meta.bookings[String(rawB.id)]?.venueId ?? '',
      );
      const venue = venueCache.get(vid) || null;
      const payments = paymentsByBooking.get(String(rawB.id)) || [];
      return normalizeBooking(rawB, { court, venue, payments, meta, users });
    });
  }

  async function loadCourtContext(baseUrl, cid, meta, signal, { includeBookings = true } = {}) {
    const cached = findCachedCourtEntry(cid);
    const bookingsPromise = includeBookings
      ? callBackend(baseUrl, '/bookings?page=0&size=200', { signal, allowNotFound: true })
      : Promise.resolve(null);

    if (cached) {
      const rawBookings = await bookingsPromise;
      const bookings = includeBookings
        ? unwrapCollection(rawBookings)
            .filter((b) => String(b.court?.id ?? '') === String(cid))
            .map((b) => normalizeBooking(b, { court: cached.court, venue: cached.venue, meta }))
        : [];
      return { court: cached.court, venue: cached.venue, bookings };
    }

    const [rawC, rawBookings] = await Promise.all([
      callBackend(baseUrl, `/courts/${cid}`, { signal }),
      bookingsPromise,
    ]);
    const court = normalizeCourt(rawC, undefined, meta);
    let venue = null;
    if (rawC.venue?.openTime) {
      venue = normalizeVenue(rawC.venue, [court], [], meta);
    } else if (court.venueId) {
      const rawV = await callBackend(baseUrl, `/venues/${court.venueId}`, { signal, allowNotFound: true });
      if (rawV) venue = normalizeVenue(rawV, [court], [], meta);
    }
    if (!venue) {
      venue = {
        id: court.venueId || '1',
        name: 'Cơ sở thể thao',
        address: '',
        openTime: '06:00',
        closeTime: '23:00',
        timezone: TIME_ZONE,
        status: 'active',
      };
    }

    const bookings = includeBookings
      ? unwrapCollection(rawBookings)
          .filter((b) => String(b.court?.id ?? '') === String(court.id))
          .map((b) => normalizeBooking(b, { court, venue, meta }))
      : [];

    return { court, venue, bookings };
  }

  async function send(path, options = {}, baseUrl = '/api/v1') {
    const url = new URL(path, 'http://backend.local');
    const p = url.pathname.split('/').filter(Boolean);
    const method = (options.method || 'GET').toUpperCase();
    const body = options.body || {};
    const headers = options.headers || {};
    const signal = options.signal;

    try {
      if (method !== 'GET') invalidateCache();
      // 1. Auth bridge (Backend has no /auth/* endpoints yet)
      if (p[0] === 'auth' && method === 'POST') {
        const users = getUsers();
        if (p[1] === 'login') {
          const email = String(body.email || '').trim().toLowerCase();
          const found = users.find((u) => u.email.toLowerCase() === email);
          if (!found || found.password !== body.password) {
            throw { status: 401, code: 'INVALID_CREDENTIALS', detail: 'Email hoặc mật khẩu chưa đúng.' };
          }
          let synced = stripPassword(found);
          try {
            const remoteUser = await callBackend(baseUrl, '/users/me', {
              method: 'POST',
              body: {
                email: found.email,
                fullName: found.fullName,
                phone: found.phone,
                role: found.role,
              },
              signal,
              allowNotFound: true,
            });
            if (remoteUser) synced = normalizeUser(remoteUser, synced);
          } catch {
            // Keep local profile if backend is unreachable during offline checks.
          }
          const session = {
            accessToken: `backend-session-${synced.id}-${now()}`,
            tokenType: 'Bearer',
            expiresIn: 900,
            user: synced,
          };
          saveSession(session);
          return jsonResponse({ data: session });
        }

        if (p[1] === 'register') {
          const email = String(body.email || '').trim().toLowerCase();
          if (
            !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email) ||
            String(body.password || '').length < 8 ||
            !String(body.fullName || '').trim() ||
            !/^0\d{9}$/.test(String(body.phone || ''))
          ) {
            throw {
              status: 422,
              code: 'INVALID_PROFILE',
              detail: 'Điền họ tên, email, số điện thoại 10 số và mật khẩu ít nhất 8 ký tự.',
            };
          }
          if (users.some((u) => u.email.toLowerCase() === email)) {
            throw { status: 409, code: 'EMAIL_EXISTS', detail: 'Email đã được sử dụng.' };
          }
          const maxId = users.reduce((m, u) => Math.max(m, Number(u.id) || 0), 3);
          const created = {
            id: String(maxId + 1),
            email,
            fullName: body.fullName.trim(),
            phone: body.phone,
            role: 'CUSTOMER',
            password: body.password,
          };
          try {
            const remoteUser = await callBackend(baseUrl, '/users/me', {
              method: 'POST',
              body: {
                email: created.email,
                fullName: created.fullName,
                phone: created.phone,
                role: created.role,
              },
              signal,
              allowNotFound: true,
            });
            if (remoteUser?.id) created.id = String(remoteUser.id);
          } catch {
            // Fall back to local ID if backend user sync is unavailable.
          }
          users.push(created);
          saveUsers(users);
          return jsonResponse({ data: stripPassword(created) }, 201);
        }

        if (p[1] === 'refresh') {
          const existing = getSession();
          if (!existing?.user) {
            throw { status: 401, code: 'SESSION_EXPIRED', detail: 'Phiên đăng nhập đã hết hạn.' };
          }
          const refreshed = {
            accessToken: `backend-session-${existing.user.id}-${now()}`,
            tokenType: 'Bearer',
            expiresIn: 900,
            user: existing.user,
          };
          saveSession(refreshed);
          return jsonResponse({ data: refreshed });
        }

        if (p[1] === 'logout') {
          saveSession(null);
          return jsonResponse(null, 204);
        }
      }

      const me = currentUser(headers);
      const requireUser = () => {
        if (!me) throw { status: 401, code: 'UNAUTHENTICATED', detail: 'Vui lòng đăng nhập lại.' };
        return me;
      };

      // 2. User profile (/users/me?id=...)
      if (p[0] === 'users' && p[1] === 'me' && p.length === 2) {
        const user = requireUser();
        if (method === 'PUT') {
          const remote = await callBackend(baseUrl, `/users/me?id=${user.id}`, {
            method: 'PUT',
            body: { fullName: body.fullName, phone: body.phone },
            signal,
            allowNotFound: true,
          });
          const updated = normalizeUser(remote, { ...user, fullName: body.fullName, phone: body.phone });
          const users = getUsers().map((u) =>
            String(u.id) === updated.id ? { ...u, fullName: updated.fullName, phone: updated.phone } : u,
          );
          saveUsers(users);
          const session = getSession();
          if (session) saveSession({ ...session, user: updated });
          return jsonResponse({ data: updated });
        }
        const remote = await callBackend(baseUrl, `/users/me?id=${user.id}`, {
          signal,
          allowNotFound: true,
        });
        return jsonResponse({ data: normalizeUser(remote, user) });
      }

      // 3. Sport types
      if (p[0] === 'sport-types') {
        if (method === 'GET') {
          if (sportsCache.data && now() - sportsCache.timestamp < CACHE_TTL_MS) {
            return jsonResponse({ data: sportsCache.data });
          }
          const list = await callBackend(baseUrl, '/sport-types', { signal, allowNotFound: true });
          const normalized = unwrapCollection(list).map(normalizeSport);
          sportsCache = { timestamp: now(), data: normalized };
          return jsonResponse({ data: normalized });
        }
        if (method === 'POST') {
          const created = await callBackend(baseUrl, '/sport-types', {
            method: 'POST',
            body: { name: body.name },
            signal,
          });
          return jsonResponse({ data: normalizeSport(created) }, 201);
        }
        if (method === 'PUT') {
          const updated = await callBackend(baseUrl, `/sport-types/${p[1]}`, {
            method: 'PUT',
            body: { name: body.name },
            signal,
          });
          return jsonResponse({ data: normalizeSport(updated) });
        }
        if (method === 'DELETE') {
          await callBackend(baseUrl, `/sport-types/${p[1]}`, { method: 'DELETE', signal });
          return jsonResponse(null, 204);
        }
      }

      // 4. Venues list & owner venues
      if (
        (p[0] === 'venues' && p.length === 1 && method === 'GET') ||
        (p[0] === 'owner' && p[1] === 'venues' && method === 'GET')
      ) {
        const isOwner = p[0] === 'owner';
        const meta = getMeta();
        let items;

        if (!isOwner && catalogCache.items && now() - catalogCache.timestamp < CACHE_TTL_MS) {
          items = catalogCache.items;
        } else {
          let rawVenues = [];
          let bulkCourts = null;
          let bulkReviews = null;

          if (isOwner) {
            const ownerId = p[2] || me?.id || '2';
            const [list, allCourts, allReviews] = await Promise.all([
              callBackend(baseUrl, `/owner/venues/${ownerId}`, { signal, allowNotFound: true }),
              callBackend(baseUrl, '/courts', { signal, allowNotFound: true }).catch(() => null),
              callBackend(baseUrl, '/reviews', { signal, allowNotFound: true }).catch(() => null),
            ]);
            rawVenues = unwrapCollection(list);
            if (!rawVenues.length) {
              const all = await callBackend(baseUrl, '/venues?page=0&size=100', {
                signal,
                allowNotFound: true,
              });
              rawVenues = unwrapCollection(all).filter(
                (v) => String(v.owner?.id ?? v.ownerId ?? '') === String(ownerId),
              );
            }
            if (Array.isArray(allCourts)) bulkCourts = allCourts;
            if (Array.isArray(allReviews)) bulkReviews = allReviews;
          } else {
            const [all, allCourts, allReviews] = await Promise.all([
              callBackend(baseUrl, '/venues?page=0&size=100', { signal, allowNotFound: true }),
              callBackend(baseUrl, '/courts', { signal, allowNotFound: true }).catch(() => null),
              callBackend(baseUrl, '/reviews', { signal, allowNotFound: true }).catch(() => null),
            ]);
            rawVenues = unwrapCollection(all);
            if (Array.isArray(allCourts)) bulkCourts = allCourts;
            if (Array.isArray(allReviews)) bulkReviews = allReviews;
          }

          if (bulkCourts !== null) {
            const courtsByVenue = new Map();
            bulkCourts.forEach((rawC) => {
              const c = normalizeCourt(rawC, undefined, meta);
              if (!c.venueId) return;
              if (!courtsByVenue.has(c.venueId)) courtsByVenue.set(c.venueId, []);
              courtsByVenue.get(c.venueId).push(c);
            });
            const reviewsByVenue = new Map();
            (bulkReviews || []).forEach((rawR) => {
              const r = normalizeReview(rawR);
              if (!r.venueId) return;
              if (!reviewsByVenue.has(r.venueId)) reviewsByVenue.set(r.venueId, []);
              reviewsByVenue.get(r.venueId).push(r);
            });
            items = rawVenues.map((rawV) => {
              const vid = String(rawV.id);
              const courts = courtsByVenue.get(vid) || [];
              const reviews = reviewsByVenue.get(vid) || [];
              return { venue: normalizeVenue(rawV, courts, reviews, meta), courts };
            });
          } else {
            items = await Promise.all(
              rawVenues.map(async (rawV) => {
                const vid = String(rawV.id);
                const [rawCourts, rawReviews] = await Promise.all([
                  callBackend(baseUrl, `/venues/${vid}/courts`, { signal, allowNotFound: true }),
                  callBackend(baseUrl, `/venues/${vid}/reviews?page=0&size=50`, {
                    signal,
                    allowNotFound: true,
                  }),
                ]);
                const courts = unwrapCollection(rawCourts).map((c) => normalizeCourt(c, vid, meta));
                const reviews = unwrapCollection(rawReviews).map((r) => normalizeReview(r, vid));
                return { venue: normalizeVenue(rawV, courts, reviews, meta), courts };
              }),
            );
          }

          if (!isOwner) catalogCache = { timestamp: now(), items };
        }

        const q = Object.fromEntries(url.searchParams);
        if (!isOwner) items = items.filter((x) => x.venue.status === 'active');
        if (q.keyword) {
          items = items.filter((x) =>
            normal(`${x.venue.name} ${x.venue.address}`).includes(normal(q.keyword)),
          );
        }
        if (q.city) items = items.filter((x) => normal(x.venue.city) === normal(q.city));
        if (q.district) items = items.filter((x) => normal(x.venue.district) === normal(q.district));
        if (q.sportTypeId || q.teamSize || q.maxPrice) {
          items = items.filter((x) =>
            x.courts.some(
              (c) =>
                c.status === 'active' &&
                (!q.sportTypeId || c.sportTypeId === String(q.sportTypeId)) &&
                (!q.teamSize || c.teamSize === Number(q.teamSize)) &&
                (!q.maxPrice || c.basePricePerHour <= Number(q.maxPrice)),
            ),
          );
        }

        const venues = items.map((x) => x.venue);
        const sort = q.sort || '-rating,minPrice';
        venues.sort((a, b) => {
          for (const key of [...sort.split(','), 'id']) {
            const k = key.replace(/^-/, '');
            const av = a[k];
            const bv = b[k];
            if (av == null || bv == null) {
              if (av !== bv) return av == null ? 1 : -1;
              continue;
            }
            const d = typeof av === 'number' ? av - bv : String(av).localeCompare(String(bv), 'vi');
            if (d) return key.startsWith('-') ? -d : d;
          }
          return 0;
        });

        return jsonResponse(paginate(venues, url));
      }

      // 5. Venues CRUD
      if (p[0] === 'venues' && p.length <= 2) {
        if (method === 'POST') {
          const user = requireUser();
          const payload = {
            name: body.name,
            address: body.address,
            district: body.district,
            city: body.city,
            description: body.description,
            openTime: toLocalTime(body.openTime),
            closeTime: toLocalTime(body.closeTime),
            status: body.status === 'inactive' ? 'INACTIVE' : 'ACTIVE',
            owner: { id: Number(user.id) || 2 },
          };
          const created = await callBackend(baseUrl, '/venues', { method: 'POST', body: payload, signal });
          const meta = getMeta();
          meta.venues[String(created.id)] = {
            ownerId: String(user.id),
            amenities: body.amenities || ['parking', 'lights'],
            imageKey: body.imageKey || 'pitch-1',
            version: 1,
          };
          saveMeta(meta);
          const v = normalizeVenue(created, [], [], meta);
          return jsonResponse({ data: v }, 201, { ETag: `"${v.version}"` });
        }

        const vid = p[1];
        if (method === 'GET') {
          const cached = findCachedVenueEntry(vid);
          if (cached) {
            return jsonResponse({ data: cached.venue }, 200, { ETag: `"${cached.venue.version}"` });
          }
          const rawV = await callBackend(baseUrl, `/venues/${vid}`, { signal });
          const [v] = await hydrateVenues(baseUrl, [rawV], signal);
          return jsonResponse({ data: v }, 200, { ETag: `"${v.version}"` });
        }

        if (method === 'PUT') {
          const payload = {
            name: body.name,
            address: body.address,
            district: body.district,
            city: body.city,
            description: body.description,
            openTime: toLocalTime(body.openTime),
            closeTime: toLocalTime(body.closeTime),
            status: body.status === 'inactive' ? 'INACTIVE' : 'ACTIVE',
          };
          const updated = await callBackend(baseUrl, `/venues/${vid}`, {
            method: 'PUT',
            body: payload,
            signal,
          });
          const meta = getMeta();
          const prev = meta.venues[vid] || {};
          meta.venues[vid] = {
            ...prev,
            amenities: body.amenities || prev.amenities || ['parking', 'lights'],
            imageKey: body.imageKey || prev.imageKey || defaultImageKey(vid),
            version: (prev.version || 1) + 1,
          };
          saveMeta(meta);
          const [v] = await hydrateVenues(baseUrl, [updated], signal);
          return jsonResponse({ data: v }, 200, { ETag: `"${v.version}"` });
        }

        if (method === 'DELETE') {
          await callBackend(baseUrl, `/venues/${vid}`, { method: 'DELETE', signal });
          return jsonResponse(null, 204);
        }
      }

      // 6. Venue courts (/venues/{venueId}/courts)
      if (p[0] === 'venues' && p[2] === 'courts') {
        const vid = p[1];
        const meta = getMeta();
        if (method === 'GET') {
          const cached = me?.role !== 'OWNER' ? findCachedVenueEntry(vid) : null;
          let courts;
          if (cached) {
            courts = [...cached.courts];
          } else {
            const rawList = await callBackend(baseUrl, `/venues/${vid}/courts`, {
              signal,
              allowNotFound: true,
            });
            courts = unwrapCollection(rawList).map((c) => normalizeCourt(c, vid, meta));
          }
          if (me?.role !== 'OWNER') courts = courts.filter((c) => c.status === 'active');
          const size = url.searchParams.get('teamSize');
          const sport = url.searchParams.get('sportTypeId');
          if (size) courts = courts.filter((c) => c.teamSize === Number(size));
          if (sport) courts = courts.filter((c) => c.sportTypeId === String(sport));
          courts.sort((a, b) => a.name.localeCompare(b.name, 'vi'));
          return jsonResponse(paginate(courts, url));
        }

        if (method === 'POST') {
          const payload = {
            name: body.name,
            venue: { id: Number(vid) },
            sportType: { id: Number(body.sportTypeId) || 1 },
            status: body.status === 'inactive' ? 'INACTIVE' : 'AVAILABLE',
          };
          const created = await callBackend(baseUrl, `/venues/${vid}/courts`, {
            method: 'POST',
            body: payload,
            signal,
          });
          const cid = String(created.id);
          meta.courts[cid] = {
            venueId: String(vid),
            sportTypeId: String(body.sportTypeId || created.sportType?.id || '1'),
            teamSize: body.teamSize ?? null,
            basePricePerHour: Number(body.basePricePerHour || 300000),
            description: body.description || '',
            imageKey: body.imageKey || defaultImageKey(cid),
            version: 1,
          };
          saveMeta(meta);
          const court = normalizeCourt(created, vid, meta);
          return jsonResponse({ data: court }, 201, { ETag: `"${court.version}"` });
        }
      }

      // 7. Courts (/courts/{id}, /availability, /quote)
      if (p[0] === 'courts') {
        const cid = p[1];
        const meta = getMeta();
        if (p[2] === 'availability') {
          const date = url.searchParams.get('date');
          if (
            !/^\d{4}-\d{2}-\d{2}$/.test(date || '') ||
            !Number.isFinite(Date.parse(`${date}T12:00:00+07:00`)) ||
            localDate(`${date}T12:00:00+07:00`) !== date ||
            date < localDate(now()) ||
            date > addDays(localDate(now()), 30)
          ) {
            throw { status: 400, code: 'INVALID_DATE', detail: 'Ngày không hợp lệ hoặc ngoài 30 ngày tới.' };
          }
          const { court, venue, bookings } = await loadCourtContext(baseUrl, cid, meta, signal);
          const slots = [];
          const close = Date.parse(`${date}T${venue.closeTime}:00+07:00`);
          for (let t = Date.parse(`${date}T${venue.openTime}:00+07:00`); t < close; t += 1800000) {
            const overlaps = bookings.some(
              (b) =>
                ['pending', 'confirmed'].includes(b.status) &&
                (!b.expiresAt || b.status !== 'pending' || Date.parse(b.expiresAt) > now()) &&
                Date.parse(b.startTime) < t + 1800000 &&
                Date.parse(b.endTime) > t,
            );
            slots.push({
              startTime: new Date(t).toISOString(),
              endTime: new Date(t + 1800000).toISOString(),
              available: t > now() && court.status === 'active' && venue.status === 'active' && !overlaps,
            });
          }
          return jsonResponse({
            data: { courtId: court.id, date, timezone: venue.timezone, slotMinutes: 30, slots },
          });
        }

        if (p[2] === 'quote') {
          const { court, venue, bookings } = await loadCourtContext(baseUrl, cid, meta, signal);
          const q = computeQuote(
            court,
            venue,
            url.searchParams.get('start'),
            url.searchParams.get('end'),
            bookings,
          );
          return jsonResponse({ data: q });
        }

        if (method === 'GET') {
          const cached = findCachedCourtEntry(cid);
          if (cached) {
            return jsonResponse({ data: cached.court }, 200, { ETag: `"${cached.court.version}"` });
          }
          const rawC = await callBackend(baseUrl, `/courts/${cid}`, { signal });
          const court = normalizeCourt(rawC, undefined, meta);
          return jsonResponse({ data: court }, 200, { ETag: `"${court.version}"` });
        }

        if (method === 'PUT') {
          const existingRaw = await callBackend(baseUrl, `/courts/${cid}`, { signal });
          const existing = normalizeCourt(existingRaw, undefined, meta);
          const payload = {
            name: body.name,
            venue: { id: Number(existing.venueId) },
            sportType: { id: Number(body.sportTypeId || existing.sportTypeId) || 1 },
            status: body.status === 'inactive' ? 'INACTIVE' : 'AVAILABLE',
          };
          const updated = await callBackend(baseUrl, `/courts/${cid}`, {
            method: 'PUT',
            body: payload,
            signal,
          });
          const prev = meta.courts[cid] || {};
          meta.courts[cid] = {
            ...prev,
            venueId: existing.venueId,
            sportTypeId: String(body.sportTypeId || existing.sportTypeId),
            teamSize: body.teamSize ?? prev.teamSize ?? null,
            basePricePerHour: Number(body.basePricePerHour ?? prev.basePricePerHour ?? 300000),
            description: body.description ?? prev.description ?? '',
            imageKey: body.imageKey || prev.imageKey || existing.imageKey,
            version: (prev.version || 1) + 1,
          };
          saveMeta(meta);
          const court = normalizeCourt(updated, existing.venueId, meta);
          return jsonResponse({ data: court }, 200, { ETag: `"${court.version}"` });
        }

        if (method === 'DELETE') {
          await callBackend(baseUrl, `/courts/${cid}`, { method: 'DELETE', signal });
          return jsonResponse(null, 204);
        }
      }

      // 8. User bookings & Venue bookings (prefer dedicated SQL-filtered endpoints; fall back cleanly for mocks)
      if ((p[0] === 'users' && p[2] === 'bookings') || (p[0] === 'venues' && p[2] === 'bookings')) {
        const user = requireUser();
        const meta = getMeta();
        const isVenueScope = p[0] === 'venues';
        const targetId = isVenueScope ? String(p[1]) : String(user.id);
        const directPath = isVenueScope
          ? `/venues/${encodeURIComponent(targetId)}/bookings?page=0&size=100`
          : `/users/me/bookings?userId=${encodeURIComponent(targetId)}&page=0&size=100`;

        const directPage = await callBackend(baseUrl, directPath, {
          signal,
          allowNotFound: true,
        });

        let rawRows;
        if (directPage !== null) {
          rawRows = unwrapCollection(directPage);
        } else {
          const rawPage = await callBackend(baseUrl, '/bookings?page=0&size=200', {
            signal,
            allowNotFound: true,
          });
          rawRows = unwrapCollection(rawPage).filter((rawB) => {
            if (isVenueScope) {
              const knownVid = rawB.court?.venue?.id ?? meta.bookings[String(rawB.id)]?.venueId;
              return !knownVid || String(knownVid) === targetId;
            }
            const knownUid = rawB.user?.id ?? meta.bookings[String(rawB.id)]?.userId;
            return !knownUid || String(knownUid) === targetId;
          });
        }

        let rows = await hydrateBookings(baseUrl, rawRows, signal);
        rows = rows.filter((b) =>
          isVenueScope ? String(b.venueId) === targetId : String(b.userId) === targetId,
        );
        const status = url.searchParams.get('status');
        const date = url.searchParams.get('date');
        if (status) rows = rows.filter((b) => b.status === status);
        if (date) rows = rows.filter((b) => localDate(b.startTime) === date);
        rows.sort((a, b) => (b.startTime || '').localeCompare(a.startTime || ''));
        return jsonResponse(paginate(rows, url));
      }

      // 9. Bookings create, detail & PATCH lifecycle actions
      if (p[0] === 'bookings') {
        const user = requireUser();
        const meta = getMeta();
        if (p.length === 1 && method === 'POST') {
          if (!user.phone) {
            throw {
              status: 422,
              code: 'PHONE_REQUIRED',
              detail: 'Cập nhật số điện thoại trong hồ sơ trước khi đặt sân.',
            };
          }
          const { court, venue, bookings: existing } = await loadCourtContext(
            baseUrl,
            body.courtId,
            meta,
            signal,
          );
          const q = computeQuote(court, venue, body.startTime, body.endTime, existing);
          if (!q.available) {
            throw {
              status: 409,
              code: 'SLOT_UNAVAILABLE',
              detail: 'Khung giờ vừa được giữ hoặc đặt. Vui lòng chọn giờ khác.',
            };
          }
          const expiresIso = new Date(Math.min(now() + 30 * 60000, Date.parse(q.startTime))).toISOString();
          const payload = {
            user: { id: Number(user.id) || 1 },
            court: { id: Number(court.id) },
            startTime: toLocalDateTime(q.startTime),
            endTime: toLocalDateTime(q.endTime),
            totalPrice: q.totalPrice,
            status: 'PENDING',
            expiresAt: toLocalDateTime(expiresIso),
          };
          const created = await callBackend(baseUrl, '/bookings', { method: 'POST', body: payload, signal });
          const bid = String(created.id);
          meta.bookings[bid] = {
            userId: String(user.id),
            courtId: court.id,
            venueId: venue.id,
            venueName: venue.name,
            courtName: court.name,
            teamSize: court.teamSize,
            sportName: court.sportName,
            address: venue.address,
            imageKey: court.imageKey,
            contactName: user.fullName,
            contactPhone: user.phone,
            note: body.note || '',
          };
          saveMeta(meta);
          const booking = normalizeBooking(created, { court, venue, meta, users: getUsers() });
          return jsonResponse({ data: booking }, 201);
        }

        const bid = p[1];
        if (p[2] === 'payments') {
          if (method === 'GET') {
            const list = await callBackend(baseUrl, `/bookings/${bid}/payments`, {
              signal,
              allowNotFound: true,
            });
            return jsonResponse({ data: unwrapCollection(list).map((x) => normalizePayment(x, bid)) });
          }
          if (method === 'POST') {
            const rawB = await callBackend(baseUrl, `/bookings/${bid}`, { signal });
            const methodEnum = String(body.method || 'bank_transfer').toUpperCase();
            const payload = {
              booking: { id: Number(bid) },
              amount: Number(rawB.totalPrice || 0),
              method: ['CASH', 'BANK_TRANSFER', 'MOMO', 'VNPAY'].includes(methodEnum)
                ? methodEnum
                : 'BANK_TRANSFER',
              transactionRef: `TXN-${now()}`,
              paidAt: toLocalDateTime(new Date(now()).toISOString()),
            };
            const created = await callBackend(baseUrl, `/bookings/${bid}/payments`, {
              method: 'POST',
              body: payload,
              signal,
            });
            return jsonResponse({ data: normalizePayment(created, bid) }, 201);
          }
        }

        if (['confirm', 'complete', 'cancel'].includes(p[2]) && method === 'POST') {
          await callBackend(baseUrl, `/bookings/${bid}/${p[2]}`, { method: 'PATCH', signal });
          if (p[2] === 'cancel' && body.reason) {
            meta.bookings[bid] = { ...(meta.bookings[bid] || {}), cancelReason: body.reason };
            saveMeta(meta);
          }
          const rawB = await callBackend(baseUrl, `/bookings/${bid}`, { signal });
          const [hydrated] = await hydrateBookings(baseUrl, [rawB], signal);
          return jsonResponse({ data: hydrated });
        }

        if (method === 'GET' && p.length === 2) {
          const rawB = await callBackend(baseUrl, `/bookings/${bid}`, { signal });
          const knownVid = String(
            rawB.court?.venue?.id ??
              venueByCourt.get(String(rawB.court?.id ?? '')) ??
              meta.bookings[String(bid)]?.venueId ??
              '',
          );
          const [[hydrated], prefetchedReviews] = await Promise.all([
            hydrateBookings(baseUrl, [rawB], signal),
            knownVid
              ? callBackend(baseUrl, `/venues/${knownVid}/reviews?page=0&size=50`, {
                  signal,
                  allowNotFound: true,
                })
              : Promise.resolve(null),
          ]);
          let review = null;
          if (hydrated.venueId) {
            const rawReviews =
              hydrated.venueId === knownVid
                ? prefetchedReviews
                : await callBackend(baseUrl, `/venues/${hydrated.venueId}/reviews?page=0&size=50`, {
                    signal,
                    allowNotFound: true,
                  });
            const match = unwrapCollection(rawReviews).find(
              (r) => String(r.booking?.id ?? r.bookingId ?? '') === String(bid),
            );
            if (match) review = normalizeReview(match, hydrated.venueId);
          }
          return jsonResponse({ data: { ...hydrated, review } });
        }
      }

      // 10. Payments single read
      if (p[0] === 'payments' && method === 'GET') {
        const rawP = await callBackend(baseUrl, `/payments/${p[1]}`, { signal });
        return jsonResponse({ data: normalizePayment(rawP) });
      }

      // 11. Reviews (/venues/{venueId}/reviews, /reviews/{id})
      if (p[0] === 'venues' && p[2] === 'reviews') {
        const vid = p[1];
        if (method === 'GET') {
          const rawPage = await callBackend(baseUrl, `/venues/${vid}/reviews?page=0&size=50`, {
            signal,
            allowNotFound: true,
          });
          const rows = unwrapCollection(rawPage).map((r) => normalizeReview(r, vid));
          return jsonResponse(paginate(rows, url));
        }
        if (method === 'POST') {
          const user = requireUser();
          const payload = {
            venue: { id: Number(vid) },
            user: { id: Number(user.id) || 1 },
            booking: { id: Number(body.bookingId) },
            rating: Number(body.rating),
            comment: body.comment,
          };
          const created = await callBackend(baseUrl, `/venues/${vid}/reviews`, {
            method: 'POST',
            body: payload,
            signal,
          });
          return jsonResponse({ data: normalizeReview(created, vid) }, 201);
        }
      }

      if (p[0] === 'reviews') {
        if (method === 'PUT') {
          const updated = await callBackend(baseUrl, `/reviews/${p[1]}`, {
            method: 'PUT',
            body: { rating: Number(body.rating), comment: body.comment },
            signal,
          });
          return jsonResponse({ data: normalizeReview(updated, undefined, p[1]) });
        }
        if (method === 'DELETE') {
          await callBackend(baseUrl, `/reviews/${p[1]}`, { method: 'DELETE', signal });
          return jsonResponse(null, 204);
        }
      }

      return problemResponse(404, 'NOT_FOUND', 'Chưa hỗ trợ endpoint này.', path);
    } catch (error) {
      if (error?.name === 'AbortError') throw error;
      if (error?.status) return problemResponse(error.status, error.code || 'BACKEND_ERROR', error.detail, path);
      return problemResponse(502, 'NETWORK_ERROR', error?.message || 'Không thể kết nối backend.', path);
    }
  }

  return { send };
}

export const backendTransport = createBackendTransport();
