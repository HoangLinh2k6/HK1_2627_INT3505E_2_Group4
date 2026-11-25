import { localDate, TIME_ZONE } from '../../../../packages/shared/time.js';

const STORAGE_META_KEY = 'keosan_backend_meta';
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

function buildForwardHeaders(incomingHeaders, { includeIdempotency = false } = {}) {
  const incoming = new Headers(incomingHeaders || {});
  const out = {};
  const auth = incoming.get('Authorization');
  if (auth) out.Authorization = auth;
  if (includeIdempotency) {
    const key = incoming.get('Idempotency-Key');
    if (key) out['Idempotency-Key'] = key;
  }
  return out;
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

  function getMeta() {
    return readJson(STORAGE_META_KEY, { venues: {}, courts: {}, bookings: {} });
  }

  function saveMeta(meta) {
    writeJson(STORAGE_META_KEY, meta);
  }

  async function callBackend(
    baseUrl,
    relPath,
    { method = 'GET', body, headers = {}, signal, allowNotFound = false, rawResponse = false } = {},
  ) {
    const fn = fetchImpl || globalThis.fetch;
    const targetUrl = `${baseUrl.replace(/\/$/, '')}${relPath}`;
    const isFormData = typeof FormData !== 'undefined' && body instanceof FormData;
    const res = await fn(targetUrl, {
      method,
      signal,
      credentials: 'include',
      headers: {
        Accept: rawResponse
          ? 'image/jpeg, image/png, application/problem+json, application/json'
          : 'application/json, application/problem+json',
        ...(body !== undefined && !isFormData ? { 'Content-Type': 'application/json' } : {}),
        ...headers,
      },
      ...(body !== undefined ? { body: isFormData ? body : JSON.stringify(body) } : {}),
    });

    if (res.status === 404 && allowNotFound) return null;
    if (res.status === 204) return null;

    if (rawResponse && res.ok) return res;

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
      id: String(src.id ?? fallback?.id ?? ''),
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
    const rawPrice = c.basePricePerHour !== undefined ? c.basePricePerHour : null;
    const basePricePerHour = rawPrice != null && rawPrice !== '' ? Number(rawPrice) : null;
    return {
      id,
      venueId: resolvedVenueId,
      sportTypeId: String(c.sportType?.id ?? c.sportTypeId ?? saved.sportTypeId ?? ''),
      sportName: c.sportType?.name || saved.sportName || 'Bóng đá',
      name: c.name || saved.name || `Sân ${id}`,
      teamSize: saved.teamSize !== undefined ? saved.teamSize : inferTeamSize(c.name),
      basePricePerHour,
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
    const pricedCourts = activeCourts.filter(
      (c) => c.basePricePerHour != null && c.basePricePerHour > 0,
    );
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
      ownerId: String(v.owner?.id ?? v.ownerId ?? saved.ownerId ?? ''),
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
      minPrice: pricedCourts.length
        ? Math.min(...pricedCourts.map((c) => c.basePricePerHour))
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

  function normalizeBooking(
    b,
    { court, venue, payments = [], review = null, meta = getMeta() } = {},
  ) {
    const id = String(b.id);
    const saved = meta.bookings[id] || {};
    const courtId = String(b.court?.id ?? court?.id ?? saved.courtId ?? '');
    const normCourt = court || (b.court ? normalizeCourt(b.court, undefined, meta) : null);
    const venueId = String(
      b.court?.venue?.id ?? venue?.id ?? normCourt?.venueId ?? saved.venueId ?? '',
    );
    const status = String(b.status || 'PENDING').toLowerCase();
    const hasPaid = payments.some((p) => p.status === 'succeeded');
    const hasPending = payments.some((p) => p.status === 'pending');
    let paymentStatus = 'unpaid';
    if (status === 'cancelled' && hasPaid) paymentStatus = 'refund_pending';
    else if (hasPaid) paymentStatus = 'paid';
    else if (hasPending) paymentStatus = 'pending';

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
      contactName: b.user?.fullName || saved.contactName || 'Người chơi',
      contactPhone: b.user?.phone || saved.contactPhone || '',
      startTime,
      endTime,
      totalPrice: Number(b.totalPrice || 0),
      currency: 'VND',
      status,
      expiresAt: b.expiresAt ? fromLocalDateTime(b.expiresAt) : null,
      checkedInAt: b.checkedInAt ? fromLocalDateTime(b.checkedInAt) : null,
      checkedOutAt: b.checkedOutAt ? fromLocalDateTime(b.checkedOutAt) : null,
      note: saved.note || '',
      cancelReason: saved.cancelReason || '',
      paymentStatus,
      review,
      createdAt: startTime || new Date(now()).toISOString(),
      updatedAt: startTime || new Date(now()).toISOString(),
    };
  }

  function normalizeVenueService(s) {
    return {
      id: String(s.id),
      name: s.name || '',
      kind: String(s.kind || 'CONSUMABLE').toUpperCase(),
      unitPrice: Number(s.unitPrice || 0),
      active: Boolean(s.active),
      stockOnHand: s.stockOnHand ?? null,
      availableStock: s.availableStock ?? null,
    };
  }

  function normalizeAsset(a) {
    return {
      id: String(a.id),
      serviceId: String(a.serviceId),
      code: a.code || '',
      state: String(a.state || 'AVAILABLE').toUpperCase(),
    };
  }

  function normalizeRental(r) {
    return {
      id: String(r.id),
      assetId: String(r.assetId),
      assetCode: r.assetCode || '',
      handedOutAt: r.handedOutAt ? fromLocalDateTime(r.handedOutAt) : null,
      returnedAt: r.returnedAt ? fromLocalDateTime(r.returnedAt) : null,
      returnedBy: r.returnedBy != null ? String(r.returnedBy) : null,
    };
  }

  function normalizeAddonOrder(o) {
    return {
      id: String(o.id),
      serviceId: String(o.serviceId),
      itemName: o.itemName || '',
      kind: String(o.kind || 'CONSUMABLE').toUpperCase(),
      quantity: Number(o.quantity || 0),
      unitPrice: Number(o.unitPrice || 0),
      total: Number(o.total || 0),
      status: String(o.status || 'RESERVED').toUpperCase(),
      orderedAt: o.orderedAt ? fromLocalDateTime(o.orderedAt) : null,
      deliveredAt: o.deliveredAt ? fromLocalDateTime(o.deliveredAt) : null,
      deliveredBy: o.deliveredBy != null ? String(o.deliveredBy) : null,
      cancelledAt: o.cancelledAt ? fromLocalDateTime(o.cancelledAt) : null,
      rentals: (o.rentals || []).map(normalizeRental),
    };
  }

  function normalizeBookingServices(bs) {
    return {
      bookingId: String(bs.bookingId),
      checkedInAt: bs.checkedInAt ? fromLocalDateTime(bs.checkedInAt) : null,
      checkedOutAt: bs.checkedOutAt ? fromLocalDateTime(bs.checkedOutAt) : null,
      courtTotal: Number(bs.courtTotal || 0),
      deliveredServicesTotal: Number(bs.deliveredServicesTotal || 0),
      total: Number(bs.total || 0),
      orders: (bs.orders || []).map(normalizeAddonOrder),
    };
  }

  function normalizeRentalPhoto(ph) {
    return {
      id: String(ph.id),
      phase: String(ph.phase || 'CHECK_IN').toUpperCase(),
      angle: String(ph.angle || 'OVERVIEW').toUpperCase(),
      uploadedAt: ph.uploadedAt ? fromLocalDateTime(ph.uploadedAt) : null,
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

  async function hydrateVenues(baseUrl, rawVenues, signal, authHeaders = {}) {
    if (!rawVenues?.length) return [];
    const meta = getMeta();
    return Promise.all(
      rawVenues.map(async (rawV) => {
        const vid = String(rawV.id);
        const [rawCourts, rawReviews] = await Promise.all([
          callBackend(baseUrl, `/venues/${vid}/courts`, {
            headers: authHeaders,
            signal,
            allowNotFound: true,
          }),
          callBackend(baseUrl, `/venues/${vid}/reviews?page=0&size=50`, {
            headers: authHeaders,
            signal,
            allowNotFound: true,
          }),
        ]);
        const courts = unwrapCollection(rawCourts).map((c) => normalizeCourt(c, vid, meta));
        const reviews = unwrapCollection(rawReviews).map((r) => normalizeReview(r, vid));
        return normalizeVenue(rawV, courts, reviews, meta);
      }),
    );
  }

  async function hydrateVenueItems(baseUrl, rawVenues, signal, authHeaders = {}) {
    if (!rawVenues?.length) return [];
    const meta = getMeta();
    const [allCourts, allReviews] = await Promise.all([
      callBackend(baseUrl, '/courts', { headers: authHeaders, signal, allowNotFound: true }).catch(
        () => null,
      ),
      callBackend(baseUrl, '/reviews', { headers: authHeaders, signal, allowNotFound: true }).catch(
        () => null,
      ),
    ]);

    if (Array.isArray(allCourts)) {
      const courtsByVenue = new Map();
      allCourts.forEach((rawC) => {
        const c = normalizeCourt(rawC, undefined, meta);
        if (!c.venueId) return;
        if (!courtsByVenue.has(c.venueId)) courtsByVenue.set(c.venueId, []);
        courtsByVenue.get(c.venueId).push(c);
      });
      const reviewsByVenue = new Map();
      (Array.isArray(allReviews) ? allReviews : []).forEach((rawR) => {
        const r = normalizeReview(rawR);
        if (!r.venueId) return;
        if (!reviewsByVenue.has(r.venueId)) reviewsByVenue.set(r.venueId, []);
        reviewsByVenue.get(r.venueId).push(r);
      });
      return rawVenues.map((rawV) => {
        const vid = String(rawV.id);
        const courts = courtsByVenue.get(vid) || [];
        const reviews = reviewsByVenue.get(vid) || [];
        return { venue: normalizeVenue(rawV, courts, reviews, meta), courts };
      });
    }

    return Promise.all(
      rawVenues.map(async (rawV) => {
        const vid = String(rawV.id);
        const [rawCourts, rawReviews] = await Promise.all([
          callBackend(baseUrl, `/venues/${vid}/courts`, {
            headers: authHeaders,
            signal,
            allowNotFound: true,
          }),
          callBackend(baseUrl, `/venues/${vid}/reviews?page=0&size=50`, {
            headers: authHeaders,
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

  async function hydrateBookings(baseUrl, rawBookings, signal, authHeaders = {}) {
    if (!rawBookings?.length) return [];
    const meta = getMeta();
    const courtCache = new Map();
    const venueCache = new Map();

    // Seed caches from the fresh venue catalog when available.
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
        rawB.court?.venue?.id ??
          courtCache.get(cid)?.venueId ??
          meta.bookings[String(rawB.id)]?.venueId ??
          '',
      );
      if (rawB.court?.venue?.name) {
        venueCache.set(vid, normalizeVenue(rawB.court.venue, [], [], meta));
      } else if (vid && !venueCache.has(vid)) {
        missingVenueIds.add(vid);
      }
    }

    const courtFetchPromise = Promise.all(
      [...missingCourtIds].map(async (cid) => {
        const fetchedC = await callBackend(baseUrl, `/courts/${cid}`, {
          headers: authHeaders,
          signal,
          allowNotFound: true,
        });
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
      await Promise.all(
        rawBookings.map(async (rawB) => {
          const rawPayments = await callBackend(baseUrl, `/bookings/${rawB.id}/payments`, {
            headers: authHeaders,
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
            const fetchedV = await callBackend(baseUrl, `/venues/${vid}`, {
              headers: authHeaders,
              signal,
              allowNotFound: true,
            });
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
      return normalizeBooking(rawB, { court, venue, payments, meta });
    });
  }

  async function send(path, options = {}, baseUrl = '/api/v1') {
    const url = new URL(path, 'http://backend.local');
    const p = url.pathname.split('/').filter(Boolean);
    const method = (options.method || 'GET').toUpperCase();
    const body = options.body || {};
    const headers = options.headers || {};
    const signal = options.signal;
    const authHeaders = buildForwardHeaders(headers);

    try {
      if (method !== 'GET') invalidateCache();

      // 1. Auth endpoints (/auth/register, /login, /refresh, /logout)
      if (p[0] === 'auth' && method === 'POST') {
        if (p[1] === 'register') {
          const created = await callBackend(baseUrl, '/auth/register', {
            method: 'POST',
            body: {
              email: body.email,
              password: body.password,
              fullName: body.fullName,
              phone: body.phone,
            },
            signal,
          });
          const user = normalizeUser(created?.data ?? created);
          return jsonResponse({ data: user }, 201);
        }

        if (p[1] === 'login') {
          const res = await callBackend(baseUrl, '/auth/login', {
            method: 'POST',
            body: { email: body.email, password: body.password },
            signal,
          });
          const session = res?.data ?? res;
          invalidateCache();
          return jsonResponse({
            data: { ...session, user: normalizeUser(session?.user) },
          });
        }

        if (p[1] === 'refresh') {
          const res = await callBackend(baseUrl, '/auth/refresh', {
            method: 'POST',
            signal,
          });
          const session = res?.data ?? res;
          return jsonResponse({
            data: { ...session, user: normalizeUser(session?.user) },
          });
        }

        if (p[1] === 'logout') {
          await callBackend(baseUrl, '/auth/logout', {
            method: 'POST',
            headers: authHeaders,
            signal,
          });
          invalidateCache();
          return jsonResponse(null, 204);
        }
      }

      // 2. Authenticated user profile (/users/me)
      if (p[0] === 'users' && p[1] === 'me' && p.length === 2) {
        if (method === 'PUT') {
          const remote = await callBackend(baseUrl, '/users/me', {
            method: 'PUT',
            headers: authHeaders,
            body: { fullName: body.fullName, phone: body.phone },
            signal,
          });
          return jsonResponse({ data: normalizeUser(remote?.data ?? remote) });
        }
        if (method === 'GET') {
          const remote = await callBackend(baseUrl, '/users/me', {
            headers: authHeaders,
            signal,
          });
          return jsonResponse({ data: normalizeUser(remote?.data ?? remote) });
        }
      }

      // 3. Sport types
      if (p[0] === 'sport-types') {
        if (method === 'GET') {
          if (sportsCache.data && now() - sportsCache.timestamp < CACHE_TTL_MS) {
            return jsonResponse({ data: sportsCache.data });
          }
          const list = await callBackend(baseUrl, '/sport-types', {
            headers: authHeaders,
            signal,
            allowNotFound: true,
          });
          const normalized = unwrapCollection(list).map(normalizeSport);
          sportsCache = { timestamp: now(), data: normalized };
          return jsonResponse({ data: normalized });
        }
        if (method === 'POST') {
          const created = await callBackend(baseUrl, '/sport-types', {
            method: 'POST',
            headers: authHeaders,
            body: { name: body.name },
            signal,
          });
          return jsonResponse({ data: normalizeSport(created) }, 201);
        }
        if (method === 'PUT') {
          const updated = await callBackend(baseUrl, `/sport-types/${p[1]}`, {
            method: 'PUT',
            headers: authHeaders,
            body: { name: body.name },
            signal,
          });
          return jsonResponse({ data: normalizeSport(updated) });
        }
        if (method === 'DELETE') {
          await callBackend(baseUrl, `/sport-types/${p[1]}`, {
            method: 'DELETE',
            headers: authHeaders,
            signal,
          });
          return jsonResponse(null, 204);
        }
      }

      // 4. Venues search & owner venues
      if (
        (p[0] === 'venues' && p.length === 1 && method === 'GET') ||
        (p[0] === 'owner' && p[1] === 'venues' && p.length === 2 && method === 'GET')
      ) {
        const isOwner = p[0] === 'owner';
        const q = Object.fromEntries(url.searchParams);
        let items;

        if (isOwner) {
          const list = await callBackend(baseUrl, '/owner/venues', {
            headers: authHeaders,
            signal,
            allowNotFound: true,
          });
          const rawVenues = unwrapCollection(list);
          items = await hydrateVenueItems(baseUrl, rawVenues, signal, authHeaders);
        } else {
          const serverParams = new URLSearchParams({ page: '0', size: '100' });
          const keyword = (q.q || q.keyword || '').trim();
          if (keyword) serverParams.set('q', keyword);
          if (q.city) serverParams.set('city', q.city);
          if (q.district) serverParams.set('district', q.district);
          if (/^\d+$/.test(String(q.sportTypeId || ''))) {
            serverParams.set('sportTypeId', String(q.sportTypeId));
          }
          if (q.maxPrice) serverParams.set('maxPrice', q.maxPrice);
          if (q.start) serverParams.set('start', q.start);
          if (q.end) serverParams.set('end', q.end);
          if (q.date) serverParams.set('date', q.date);
          if (q.from) serverParams.set('from', q.from);
          if (q.to) serverParams.set('to', q.to);

          const hasServerFilters = Boolean(
            keyword ||
              q.city ||
              q.district ||
              serverParams.has('sportTypeId') ||
              q.maxPrice ||
              q.start ||
              q.end ||
              q.date ||
              q.from ||
              q.to,
          );
          const canUseCatalogCache = !hasServerFilters && !authHeaders.Authorization;

          if (canUseCatalogCache && catalogCache.items && now() - catalogCache.timestamp < CACHE_TTL_MS) {
            items = catalogCache.items;
          } else {
            const all = await callBackend(baseUrl, `/venues?${serverParams}`, {
              headers: authHeaders,
              signal,
              allowNotFound: true,
            });
            const rawVenues = unwrapCollection(all);
            items = await hydrateVenueItems(baseUrl, rawVenues, signal, authHeaders);
            if (canUseCatalogCache) catalogCache = { timestamp: now(), items };
          }
        }

        if (!isOwner && q.teamSize) {
          const sportFilter = /^\d+$/.test(String(q.sportTypeId || ''))
            ? String(q.sportTypeId)
            : '';
          const maxPriceFilter = q.maxPrice ? Number(q.maxPrice) : null;
          items = items.filter((x) =>
            x.courts.some(
              (c) =>
                c.status === 'active' &&
                c.teamSize === Number(q.teamSize) &&
                (!sportFilter || c.sportTypeId === sportFilter) &&
                (maxPriceFilter == null ||
                  (c.basePricePerHour != null && c.basePricePerHour <= maxPriceFilter)),
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
          const created = await callBackend(baseUrl, '/venues', {
            method: 'POST',
            headers: authHeaders,
            body: payload,
            signal,
          });
          const meta = getMeta();
          meta.venues[String(created.id)] = {
            ownerId: created.owner?.id ? String(created.owner.id) : '',
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
          const cached = !authHeaders.Authorization ? findCachedVenueEntry(vid) : null;
          if (cached) {
            return jsonResponse({ data: cached.venue }, 200, { ETag: `"${cached.venue.version}"` });
          }
          const rawV = await callBackend(baseUrl, `/venues/${vid}`, {
            headers: authHeaders,
            signal,
          });
          const [v] = await hydrateVenues(baseUrl, [rawV], signal, authHeaders);
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
            headers: authHeaders,
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
          const [v] = await hydrateVenues(baseUrl, [updated], signal, authHeaders);
          return jsonResponse({ data: v }, 200, { ETag: `"${v.version}"` });
        }

        if (method === 'DELETE') {
          await callBackend(baseUrl, `/venues/${vid}`, {
            method: 'DELETE',
            headers: authHeaders,
            signal,
          });
          return jsonResponse(null, 204);
        }
      }

      // 6. Venue courts (/venues/{venueId}/courts)
      if (p[0] === 'venues' && p[2] === 'courts') {
        const vid = p[1];
        const meta = getMeta();
        if (method === 'GET') {
          const cached = !authHeaders.Authorization ? findCachedVenueEntry(vid) : null;
          let courts;
          if (cached) {
            courts = [...cached.courts];
          } else {
            const rawList = await callBackend(baseUrl, `/venues/${vid}/courts`, {
              headers: authHeaders,
              signal,
              allowNotFound: true,
            });
            courts = unwrapCollection(rawList).map((c) => normalizeCourt(c, vid, meta));
          }
          const size = url.searchParams.get('teamSize');
          const sport = url.searchParams.get('sportTypeId');
          if (size) courts = courts.filter((c) => c.teamSize === Number(size));
          if (sport) courts = courts.filter((c) => c.sportTypeId === String(sport));
          courts.sort((a, b) => a.name.localeCompare(b.name, 'vi'));
          return jsonResponse(paginate(courts, url));
        }

        if (method === 'POST') {
          const priceNum = Number(body.basePricePerHour);
          const hasPrice =
            body.basePricePerHour !== undefined &&
            body.basePricePerHour !== null &&
            body.basePricePerHour !== '' &&
            Number.isFinite(priceNum) &&
            priceNum > 0;
          const payload = {
            name: body.name,
            sportType: { id: Number(body.sportTypeId) || 1 },
            ...(hasPrice ? { basePricePerHour: Math.round(priceNum) } : {}),
            status: body.status === 'inactive' ? 'INACTIVE' : 'AVAILABLE',
          };
          const created = await callBackend(baseUrl, `/venues/${vid}/courts`, {
            method: 'POST',
            headers: authHeaders,
            body: payload,
            signal,
          });
          const cid = String(created.id);
          meta.courts[cid] = {
            venueId: String(vid),
            sportTypeId: String(body.sportTypeId || created.sportType?.id || '1'),
            teamSize: body.teamSize ?? null,
            description: body.description || '',
            imageKey: body.imageKey || defaultImageKey(cid),
            version: 1,
          };
          saveMeta(meta);
          const court = normalizeCourt(created, vid, meta);
          return jsonResponse({ data: court }, 201, { ETag: `"${court.version}"` });
        }
      }

      // 6b. Venue services & equipment assets (/venues/{venueId}/services...)
      if (p[0] === 'venues' && p[2] === 'services') {
        const vid = p[1];
        if (p.length === 3) {
          if (method === 'GET') {
            const list = await callBackend(baseUrl, `/venues/${vid}/services`, {
              headers: authHeaders,
              signal,
            });
            return jsonResponse({ data: unwrapCollection(list).map(normalizeVenueService) });
          }
          if (method === 'POST') {
            const kind = String(body.kind || 'CONSUMABLE').toUpperCase();
            const payload = {
              name: body.name,
              kind,
              unitPrice: Math.round(Number(body.unitPrice)),
              ...(kind === 'CONSUMABLE' ? { stockOnHand: Math.round(Number(body.stockOnHand)) } : {}),
            };
            const created = await callBackend(baseUrl, `/venues/${vid}/services`, {
              method: 'POST',
              headers: authHeaders,
              body: payload,
              signal,
            });
            return jsonResponse({ data: normalizeVenueService(created) }, 201);
          }
        }
        if (p.length === 4 && method === 'PUT') {
          const sid = p[3];
          const hasStock =
            body.stockOnHand !== undefined && body.stockOnHand !== null && body.stockOnHand !== '';
          const payload = {
            name: body.name,
            unitPrice: Math.round(Number(body.unitPrice)),
            active: Boolean(body.active),
            ...(hasStock ? { stockOnHand: Math.round(Number(body.stockOnHand)) } : {}),
          };
          const updated = await callBackend(baseUrl, `/venues/${vid}/services/${sid}`, {
            method: 'PUT',
            headers: authHeaders,
            body: payload,
            signal,
          });
          return jsonResponse({ data: normalizeVenueService(updated) });
        }
        if (p.length === 5 && p[4] === 'assets') {
          const sid = p[3];
          if (method === 'GET') {
            const list = await callBackend(baseUrl, `/venues/${vid}/services/${sid}/assets`, {
              headers: authHeaders,
              signal,
            });
            return jsonResponse({ data: unwrapCollection(list).map(normalizeAsset) });
          }
          if (method === 'POST') {
            const created = await callBackend(baseUrl, `/venues/${vid}/services/${sid}/assets`, {
              method: 'POST',
              headers: authHeaders,
              body: { code: String(body.code || '').trim() },
              signal,
            });
            return jsonResponse({ data: normalizeAsset(created) }, 201);
          }
        }
        if (p.length === 6 && p[4] === 'assets' && method === 'PUT') {
          const sid = p[3];
          const aid = p[5];
          const updated = await callBackend(
            baseUrl,
            `/venues/${vid}/services/${sid}/assets/${aid}`,
            {
              method: 'PUT',
              headers: authHeaders,
              body: { state: String(body.state).toUpperCase() },
              signal,
            },
          );
          return jsonResponse({ data: normalizeAsset(updated) });
        }
      }

      // 7. Courts (/courts/{id}, /availability, /quote)
      if (p[0] === 'courts') {
        const cid = p[1];
        const meta = getMeta();
        if (p[2] === 'availability' && method === 'GET') {
          const date = url.searchParams.get('date') || '';
          const raw = await callBackend(
            baseUrl,
            `/courts/${cid}/availability?date=${encodeURIComponent(date)}`,
            { headers: authHeaders, signal },
          );
          return jsonResponse({
            data: { ...raw, courtId: String(raw?.courtId ?? cid) },
          });
        }

        if (p[2] === 'quote' && method === 'GET') {
          const start = url.searchParams.get('start') || '';
          const end = url.searchParams.get('end') || '';
          const q = new URLSearchParams({ start, end });
          const raw = await callBackend(baseUrl, `/courts/${cid}/quote?${q}`, {
            headers: authHeaders,
            signal,
          });
          return jsonResponse({
            data: {
              ...raw,
              courtId: String(raw?.courtId ?? cid),
              totalPrice: raw?.totalPrice != null ? Number(raw.totalPrice) : null,
              lines: (raw?.lines || []).map((line) => ({
                ...line,
                amount: Number(line.amount),
              })),
            },
          });
        }

        if (method === 'GET') {
          const cached = !authHeaders.Authorization ? findCachedCourtEntry(cid) : null;
          if (cached) {
            return jsonResponse({ data: cached.court }, 200, { ETag: `"${cached.court.version}"` });
          }
          const rawC = await callBackend(baseUrl, `/courts/${cid}`, {
            headers: authHeaders,
            signal,
          });
          const court = normalizeCourt(rawC, undefined, meta);
          return jsonResponse({ data: court }, 200, { ETag: `"${court.version}"` });
        }

        if (method === 'PUT') {
          const priceNum = Number(body.basePricePerHour);
          const hasPrice =
            body.basePricePerHour !== undefined &&
            body.basePricePerHour !== null &&
            body.basePricePerHour !== '' &&
            Number.isFinite(priceNum) &&
            priceNum > 0;
          const payload = {
            ...(body.name !== undefined ? { name: body.name } : {}),
            ...(body.sportTypeId ? { sportType: { id: Number(body.sportTypeId) } } : {}),
            ...(hasPrice ? { basePricePerHour: Math.round(priceNum) } : {}),
            ...(body.status !== undefined
              ? { status: body.status === 'inactive' ? 'INACTIVE' : 'AVAILABLE' }
              : {}),
          };
          const updated = await callBackend(baseUrl, `/courts/${cid}`, {
            method: 'PUT',
            headers: authHeaders,
            body: payload,
            signal,
          });
          const prev = meta.courts[cid] || {};
          const resolvedVenueId = String(updated.venue?.id ?? prev.venueId ?? venueByCourt.get(cid) ?? '');
          meta.courts[cid] = {
            ...prev,
            venueId: resolvedVenueId,
            sportTypeId: String(body.sportTypeId || updated.sportType?.id || prev.sportTypeId || ''),
            teamSize: body.teamSize !== undefined ? body.teamSize : (prev.teamSize ?? null),
            description: body.description ?? prev.description ?? '',
            imageKey: body.imageKey || prev.imageKey || defaultImageKey(cid),
            version: (prev.version || 1) + 1,
          };
          saveMeta(meta);
          const court = normalizeCourt(updated, resolvedVenueId || undefined, meta);
          return jsonResponse({ data: court }, 200, { ETag: `"${court.version}"` });
        }

        if (method === 'DELETE') {
          await callBackend(baseUrl, `/courts/${cid}`, {
            method: 'DELETE',
            headers: authHeaders,
            signal,
          });
          return jsonResponse(null, 204);
        }
      }

      // 8. User bookings & Venue bookings
      if ((p[0] === 'users' && p[2] === 'bookings') || (p[0] === 'venues' && p[2] === 'bookings')) {
        const isVenueScope = p[0] === 'venues';
        const directPath = isVenueScope
          ? `/venues/${encodeURIComponent(String(p[1]))}/bookings?page=0&size=100`
          : '/users/me/bookings?page=0&size=100';

        const directPage = await callBackend(baseUrl, directPath, {
          headers: authHeaders,
          signal,
          allowNotFound: true,
        });

        const rawRows = unwrapCollection(directPage);
        let rows = await hydrateBookings(baseUrl, rawRows, signal, authHeaders);
        const status = url.searchParams.get('status');
        const date = url.searchParams.get('date');
        if (status) rows = rows.filter((b) => b.status === status);
        if (date) rows = rows.filter((b) => localDate(b.startTime) === date);
        rows.sort((a, b) => (b.startTime || '').localeCompare(a.startTime || ''));
        return jsonResponse(paginate(rows, url));
      }

      // 9. Bookings create, detail & PATCH lifecycle actions
      if (p[0] === 'bookings') {
        const meta = getMeta();
        if (p.length === 1 && method === 'POST') {
          const payload = {
            court: { id: Number(body.courtId) },
            startTime: toLocalDateTime(body.startTime),
            endTime: toLocalDateTime(body.endTime),
          };
          const created = await callBackend(baseUrl, '/bookings', {
            method: 'POST',
            headers: buildForwardHeaders(headers, { includeIdempotency: true }),
            body: payload,
            signal,
          });
          const bid = String(created.id);
          const cid = String(created.court?.id ?? body.courtId ?? '');
          let court = created.court?.name ? normalizeCourt(created.court, undefined, meta) : null;
          if (!court && cid) {
            const rawC = await callBackend(baseUrl, `/courts/${cid}`, {
              headers: authHeaders,
              signal,
              allowNotFound: true,
            });
            if (rawC) court = normalizeCourt(rawC, undefined, meta);
          }
          const vid = String(created.court?.venue?.id ?? court?.venueId ?? '');
          let venue = created.court?.venue?.name
            ? normalizeVenue(created.court.venue, [], [], meta)
            : null;
          if (!venue && vid) {
            const rawV = await callBackend(baseUrl, `/venues/${vid}`, {
              headers: authHeaders,
              signal,
              allowNotFound: true,
            });
            if (rawV) venue = normalizeVenue(rawV, [], [], meta);
          }
          meta.bookings[bid] = {
            ...(meta.bookings[bid] || {}),
            userId: String(created.user?.id ?? ''),
            courtId: cid,
            venueId: vid,
            venueName: venue?.name || created.court?.venue?.name || '',
            courtName: court?.name || created.court?.name || '',
            teamSize: court?.teamSize,
            sportName: court?.sportName,
            address: venue?.address || '',
            imageKey: court?.imageKey || defaultImageKey(cid || bid),
            contactName: created.user?.fullName || '',
            contactPhone: created.user?.phone || '',
            ...(body.note ? { note: body.note } : {}),
          };
          saveMeta(meta);
          const booking = normalizeBooking(created, { court, venue, payments: [], meta });
          return jsonResponse({ data: booking }, 201);
        }

        const bid = p[1];
        if (p[2] === 'payments') {
          if (method === 'GET') {
            const list = await callBackend(baseUrl, `/bookings/${bid}/payments`, {
              headers: authHeaders,
              signal,
              allowNotFound: true,
            });
            return jsonResponse({ data: unwrapCollection(list).map((x) => normalizePayment(x, bid)) });
          }
          if (method === 'POST') {
            const methodEnum = String(body.method || 'bank_transfer').toUpperCase();
            const validMethod = ['CASH', 'BANK_TRANSFER', 'MOMO', 'VNPAY'].includes(methodEnum)
              ? methodEnum
              : 'BANK_TRANSFER';
            const created = await callBackend(baseUrl, `/bookings/${bid}/payments`, {
              method: 'POST',
              headers: authHeaders,
              body: { method: validMethod },
              signal,
            });
            return jsonResponse({ data: normalizePayment(created, bid) }, 201);
          }
        }

        if (p[2] === 'services') {
          if (p.length === 3 && method === 'GET') {
            const summary = await callBackend(baseUrl, `/bookings/${bid}/services`, {
              headers: authHeaders,
              signal,
            });
            return jsonResponse({ data: normalizeBookingServices(summary) });
          }
          if (p.length === 3 && method === 'POST') {
            const created = await callBackend(baseUrl, `/bookings/${bid}/services`, {
              method: 'POST',
              headers: buildForwardHeaders(headers, { includeIdempotency: true }),
              body: { serviceId: Number(body.serviceId), quantity: Number(body.quantity) },
              signal,
            });
            return jsonResponse({ data: normalizeAddonOrder(created) }, 201);
          }
          if (p.length === 4 && method === 'DELETE') {
            await callBackend(baseUrl, `/bookings/${bid}/services/${p[3]}`, {
              method: 'DELETE',
              headers: authHeaders,
              signal,
            });
            return jsonResponse(null, 204);
          }
          if (p.length === 5 && p[4] === 'deliver' && method === 'POST') {
            const hasAssets = Array.isArray(body.assetIds) && body.assetIds.length > 0;
            const delivered = await callBackend(
              baseUrl,
              `/bookings/${bid}/services/${p[3]}/deliver`,
              {
                method: 'POST',
                headers: authHeaders,
                body: hasAssets ? { assetIds: body.assetIds.map(Number) } : {},
                signal,
              },
            );
            return jsonResponse({ data: normalizeAddonOrder(delivered) });
          }
        }

        if ((p[2] === 'check-in' || p[2] === 'check-out') && p.length === 3 && method === 'POST') {
          const summary = await callBackend(baseUrl, `/bookings/${bid}/${p[2]}`, {
            method: 'POST',
            headers: authHeaders,
            signal,
          });
          return jsonResponse({ data: normalizeBookingServices(summary) });
        }

        if (['confirm', 'complete', 'cancel'].includes(p[2]) && method === 'POST') {
          await callBackend(baseUrl, `/bookings/${bid}/${p[2]}`, {
            method: 'PATCH',
            headers: authHeaders,
            signal,
          });
          if (p[2] === 'cancel' && body.reason) {
            meta.bookings[bid] = { ...(meta.bookings[bid] || {}), cancelReason: body.reason };
            saveMeta(meta);
          }
          const rawB = await callBackend(baseUrl, `/bookings/${bid}`, {
            headers: authHeaders,
            signal,
          });
          const [hydrated] = await hydrateBookings(baseUrl, [rawB], signal, authHeaders);
          return jsonResponse({ data: hydrated });
        }

        if (method === 'GET' && p.length === 2) {
          const rawB = await callBackend(baseUrl, `/bookings/${bid}`, {
            headers: authHeaders,
            signal,
          });
          const knownVid = String(
            rawB.court?.venue?.id ??
              venueByCourt.get(String(rawB.court?.id ?? '')) ??
              meta.bookings[String(bid)]?.venueId ??
              '',
          );
          const [[hydrated], prefetchedReviews] = await Promise.all([
            hydrateBookings(baseUrl, [rawB], signal, authHeaders),
            knownVid
              ? callBackend(baseUrl, `/venues/${knownVid}/reviews?page=0&size=50`, {
                  headers: authHeaders,
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
                    headers: authHeaders,
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
        const rawP = await callBackend(baseUrl, `/payments/${p[1]}`, {
          headers: authHeaders,
          signal,
        });
        return jsonResponse({ data: normalizePayment(rawP) });
      }

      // 11. Reviews (/venues/{venueId}/reviews, /reviews/{id})
      if (p[0] === 'venues' && p[2] === 'reviews') {
        const vid = p[1];
        if (method === 'GET') {
          const rawPage = await callBackend(baseUrl, `/venues/${vid}/reviews?page=0&size=50`, {
            headers: authHeaders,
            signal,
            allowNotFound: true,
          });
          const rows = unwrapCollection(rawPage).map((r) => normalizeReview(r, vid));
          return jsonResponse(paginate(rows, url));
        }
        if (method === 'POST') {
          const created = await callBackend(baseUrl, `/venues/${vid}/reviews`, {
            method: 'POST',
            headers: authHeaders,
            body: {
              bookingId: Number(body.bookingId),
              rating: Number(body.rating),
              comment: body.comment,
            },
            signal,
          });
          return jsonResponse({ data: normalizeReview(created, vid) }, 201);
        }
      }

      if (p[0] === 'reviews') {
        if (method === 'PUT') {
          const updated = await callBackend(baseUrl, `/reviews/${p[1]}`, {
            method: 'PUT',
            headers: authHeaders,
            body: { rating: Number(body.rating), comment: body.comment },
            signal,
          });
          return jsonResponse({ data: normalizeReview(updated, undefined, p[1]) });
        }
        if (method === 'DELETE') {
          await callBackend(baseUrl, `/reviews/${p[1]}`, {
            method: 'DELETE',
            headers: authHeaders,
            signal,
          });
          return jsonResponse(null, 204);
        }
      }

      // 12. Rental photos (/rentals/{rentalId}/photos...)
      if (p[0] === 'rentals' && p[2] === 'photos') {
        const rid = p[1];
        if (p.length === 3 && method === 'GET') {
          const list = await callBackend(baseUrl, `/rentals/${rid}/photos`, {
            headers: authHeaders,
            signal,
          });
          return jsonResponse({ data: unwrapCollection(list).map(normalizeRentalPhoto) });
        }
        if (p.length === 3 && method === 'POST') {
          const uploaded = await callBackend(baseUrl, `/rentals/${rid}/photos`, {
            method: 'POST',
            headers: authHeaders,
            body,
            signal,
          });
          return jsonResponse({ data: normalizeRentalPhoto(uploaded) });
        }
        if (p.length === 5 && p[4] === 'link' && method === 'GET') {
          const link = await callBackend(baseUrl, `/rentals/${rid}/photos/${p[3]}/link`, {
            headers: authHeaders,
            signal,
          });
          return jsonResponse({ data: link });
        }
        if (p.length === 4 && method === 'GET') {
          return await callBackend(baseUrl, `/rentals/${rid}/photos/${p[3]}`, {
            headers: authHeaders,
            signal,
            rawResponse: true,
          });
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
