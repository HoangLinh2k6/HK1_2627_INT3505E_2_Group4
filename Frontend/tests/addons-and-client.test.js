import test from 'node:test';
import assert from 'node:assert/strict';
import { createMockTransport } from '../apps/web/src/mocks/transport.js';
import { setAccessToken } from '../apps/web/src/api/client.js';
import { rentalPhotoApi, identityApi } from '../apps/web/src/api/services.js';

const PNG_SAMPLE = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3, 4]);

async function sendJson(transport, path, options = {}) {
  const res = await transport.send(path, options);
  const json = res.status === 204 ? null : await res.json();
  return { status: res.status, headers: res.headers, body: json };
}

async function loginMock(transport, email) {
  const res = await sendJson(transport, '/auth/login', {
    method: 'POST',
    body: { email, password: 'Demo@12345' },
  });
  assert.equal(res.status, 200, `Login failed for ${email}: ${JSON.stringify(res.body)}`);
  return res.body.data.accessToken;
}

test('mock addon lifecycle: services, assets, string IDs, deliver, photos, and check-out gate', async () => {
  const transport = createMockTransport();
  const ownerToken = await loginMock(transport, 'owner@keosan.demo');
  const playerToken = await loginMock(transport, 'player@keosan.demo');

  const ownerAuth = { Authorization: `Bearer ${ownerToken}` };
  const playerAuth = { Authorization: `Bearer ${playerToken}` };

  // Find a confirmed booking's venueId from fixtures
  const bookingRes = await sendJson(transport, '/bookings/demo-confirmed', {
    method: 'GET',
    headers: playerAuth,
  });
  assert.equal(bookingRes.status, 200);
  const venueId = bookingRes.body.data.venueId;

  // Owner creates a CONSUMABLE and a RENTAL service
  const consumableRes = await sendJson(transport, `/venues/${venueId}/services`, {
    method: 'POST',
    headers: ownerAuth,
    body: { name: 'Nước khoáng 500ml', kind: 'CONSUMABLE', unitPrice: 15000, stockOnHand: 20 },
  });
  assert.equal(consumableRes.status, 201);
  assert.equal(consumableRes.body.data.stockOnHand, 20);

  const rentalSvcRes = await sendJson(transport, `/venues/${venueId}/services`, {
    method: 'POST',
    headers: ownerAuth,
    body: { name: 'Vợt cầu lông Pro', kind: 'RENTAL', unitPrice: 60000 },
  });
  assert.equal(rentalSvcRes.status, 201);
  const rentalService = rentalSvcRes.body.data;
  assert.equal(typeof rentalService.id, 'string');

  // Owner registers an asset and toggles its state MAINTENANCE -> AVAILABLE
  const assetRes = await sendJson(
    transport,
    `/venues/${venueId}/services/${rentalService.id}/assets`,
    {
      method: 'POST',
      headers: ownerAuth,
      body: { code: 'VOT-001' },
    },
  );
  assert.equal(assetRes.status, 201);
  const asset = assetRes.body.data;
  assert.equal(typeof asset.id, 'string');

  const maintRes = await sendJson(
    transport,
    `/venues/${venueId}/services/${rentalService.id}/assets/${asset.id}`,
    {
      method: 'PUT',
      headers: ownerAuth,
      body: { state: 'MAINTENANCE' },
    },
  );
  assert.equal(maintRes.status, 200);
  assert.equal(maintRes.body.data.state, 'MAINTENANCE');

  const availRes = await sendJson(
    transport,
    `/venues/${venueId}/services/${rentalService.id}/assets/${asset.id}`,
    {
      method: 'PUT',
      headers: ownerAuth,
      body: { state: 'AVAILABLE' },
    },
  );
  assert.equal(availRes.status, 200);
  assert.equal(availRes.body.data.state, 'AVAILABLE');

  // Player reserves the RENTAL service using a string serviceId (as in mock mode UI)
  const reserveRes = await sendJson(transport, '/bookings/demo-confirmed/services', {
    method: 'POST',
    headers: { ...playerAuth, 'Idempotency-Key': 'test-idem-reserve-1' },
    body: { serviceId: rentalService.id, quantity: 1 },
  });
  assert.equal(reserveRes.status, 201);
  const rentalOrder = reserveRes.body.data;
  assert.equal(rentalOrder.serviceId, rentalService.id);
  assert.equal(rentalOrder.rentals.length, 1);

  const assetIds = rentalOrder.rentals.map((r) => r.assetId);
  assert.equal(typeof assetIds[0], 'string');

  // Owner checks in the booking and delivers the rental with string assetIds
  const checkInRes = await sendJson(transport, '/bookings/demo-confirmed/check-in', {
    method: 'POST',
    headers: ownerAuth,
  });
  assert.equal(checkInRes.status, 200);
  assert.ok(checkInRes.body.data.checkedInAt);

  const deliverRes = await sendJson(
    transport,
    `/bookings/demo-confirmed/services/${rentalOrder.id}/deliver`,
    {
      method: 'POST',
      headers: ownerAuth,
      body: { assetIds },
    },
  );
  assert.equal(deliverRes.status, 200);
  assert.equal(deliverRes.body.data.status, 'DELIVERED');

  // Check-out must be rejected (409) until all 4 required rental photos exist
  const blockedCheckOut = await sendJson(transport, '/bookings/demo-confirmed/check-out', {
    method: 'POST',
    headers: ownerAuth,
  });
  assert.equal(blockedCheckOut.status, 409);

  const rentalId = rentalOrder.rentals[0].id;
  const slots = [
    ['CHECK_IN', 'OVERVIEW'],
    ['CHECK_IN', 'DETAIL'],
    ['CHECK_OUT', 'OVERVIEW'],
    ['CHECK_OUT', 'DETAIL'],
  ];

  let lastPhotoId = null;
  for (const [phase, angle] of slots) {
    const fd = new FormData();
    fd.append('phase', phase);
    fd.append('angle', angle);
    fd.append('file', new Blob([PNG_SAMPLE], { type: 'image/png' }), `${phase}_${angle}.png`);

    const upRes = await sendJson(transport, `/rentals/${rentalId}/photos`, {
      method: 'POST',
      headers: playerAuth,
      body: fd,
    });
    assert.equal(upRes.status, 200, `Upload failed for ${phase}/${angle}: ${JSON.stringify(upRes.body)}`);
    lastPhotoId = upRes.body.data.id;
  }

  // Listing photos must omit internal Blob instances from JSON metadata
  const listRes = await sendJson(transport, `/rentals/${rentalId}/photos`, {
    method: 'GET',
    headers: playerAuth,
  });
  assert.equal(listRes.status, 200);
  assert.equal(listRes.body.data.length, 4);
  for (const item of listRes.body.data) {
    assert.equal('blob' in item, false, 'GET /rentals/{rid}/photos must not leak blob field');
    assert.ok(item.id && item.phase && item.angle && item.uploadedAt);
  }

  // Signed link returns 409 in mock mode; raw binary download returns exact PNG bytes
  const linkRes = await sendJson(transport, `/rentals/${rentalId}/photos/${lastPhotoId}/link`, {
    method: 'GET',
    headers: playerAuth,
  });
  assert.equal(linkRes.status, 409);

  const dlRes = await transport.send(`/rentals/${rentalId}/photos/${lastPhotoId}`, {
    method: 'GET',
    headers: playerAuth,
  });
  assert.equal(dlRes.status, 200);
  assert.equal(dlRes.headers.get('content-type'), 'image/png');
  const dlBytes = new Uint8Array(await dlRes.arrayBuffer());
  assert.deepEqual(dlBytes, PNG_SAMPLE);

  // Now check-out succeeds and marks booking completed
  const checkOutRes = await sendJson(transport, '/bookings/demo-confirmed/check-out', {
    method: 'POST',
    headers: ownerAuth,
  });
  assert.equal(checkOutRes.status, 200);
  assert.ok(checkOutRes.body.data.checkedOutAt);

  const finalBooking = await sendJson(transport, '/bookings/demo-confirmed', {
    method: 'GET',
    headers: playerAuth,
  });
  assert.equal(finalBooking.status, 200);
  assert.equal(finalBooking.body.data.status, 'completed');
});

test('client.js FormData upload, blob download, and 401 token refresh retry', async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  setAccessToken('tok-1');

  try {
    globalThis.fetch = async (url, init = {}) => {
      calls.push({ url: String(url), init });

      if (url.endsWith('/rentals/42/photos') && init.method === 'POST') {
        assert.ok(init.body instanceof FormData, 'Upload body should remain FormData');
        const headerKeys = Object.keys(init.headers || {}).map((k) => k.toLowerCase());
        assert.equal(
          headerKeys.includes('content-type'),
          false,
          'Multipart upload must not force JSON Content-Type',
        );
        assert.equal(init.headers.Authorization || init.headers.authorization, 'Bearer tok-1');
        return new Response(
          JSON.stringify({
            id: 99,
            phase: 'CHECK_IN',
            angle: 'OVERVIEW',
            uploadedAt: '2026-04-01T10:00:00Z',
          }),
          { status: 201, headers: { 'Content-Type': 'application/json' } },
        );
      }

      if (url.endsWith('/rentals/42/photos/99') && (!init.method || init.method === 'GET')) {
        return new Response(PNG_SAMPLE, {
          status: 200,
          headers: { 'Content-Type': 'image/png' },
        });
      }

      if (url.endsWith('/users/me')) {
        const auth = init.headers?.Authorization || init.headers?.authorization;
        if (auth === 'Bearer tok-1') {
          return new Response(
            JSON.stringify({ code: 'UNAUTHORIZED', message: 'Expired token' }),
            { status: 401, headers: { 'Content-Type': 'application/json' } },
          );
        }
        assert.equal(auth, 'Bearer tok-2');
        return new Response(
          JSON.stringify({
            id: 1,
            email: 'player@example.com',
            fullName: 'Nguyen Van A',
            phone: '0901234567',
            role: 'USER',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }

      if (url.endsWith('/auth/refresh') && init.method === 'POST') {
        return new Response(JSON.stringify({ accessToken: 'tok-2' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      throw new Error(`Unexpected fetch URL: ${init.method || 'GET'} ${url}`);
    };

    const up = await rentalPhotoApi.upload('42', {
      phase: 'CHECK_IN',
      angle: 'OVERVIEW',
      file: new Blob([PNG_SAMPLE], { type: 'image/png' }),
    });
    assert.equal(up.data.id, '99');

    const blob = await rentalPhotoApi.download('42', '99');
    assert.ok(blob instanceof Blob);
    assert.deepEqual(new Uint8Array(await blob.arrayBuffer()), PNG_SAMPLE);

    const me = await identityApi.me();
    assert.equal(me.data.id, '1');
    assert.equal(me.data.fullName, 'Nguyen Van A');
    assert.equal(me.data.phone, '0901234567');
    assert.equal(me.data.role, 'USER');

    // Total calls: upload (1) + download (1) + /users/me 401 (1) + /auth/refresh (1) + /users/me retry (1) = 5
    assert.equal(calls.length, 5);
  } finally {
    globalThis.fetch = originalFetch;
    setAccessToken(null);
  }
});
