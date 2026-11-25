import { useEffect, useRef, useState } from 'react';
import { Camera, CheckCircle2, Eye, Package, Plus, Trash2, Upload } from 'lucide-react';
import { addonApi, bookingApi, rentalPhotoApi } from '../api/services';
import { useResource } from '../hooks/useResource';
import { Button, ErrorBox, Field, Loading, Modal } from './ui';
import { clockTime, displayDate, money } from '../../../../packages/shared/time';

const ORDER_STATUS_LABELS = {
  RESERVED: 'Đang giữ',
  DELIVERED: 'Đã giao',
  RELEASED: 'Đã hủy',
};

const PHOTO_SLOTS = [
  { phase: 'CHECK_IN', angle: 'OVERVIEW', label: 'Nhận đồ · Toàn cảnh' },
  { phase: 'CHECK_IN', angle: 'DETAIL', label: 'Nhận đồ · Chi tiết' },
  { phase: 'CHECK_OUT', angle: 'OVERVIEW', label: 'Trả đồ · Toàn cảnh' },
  { phase: 'CHECK_OUT', angle: 'DETAIL', label: 'Trả đồ · Chi tiết' },
];

const MAX_PHOTO_BYTES = 5 * 1024 * 1024;

export default function BookingServicesSection({
  booking,
  mine,
  owner,
  onBookingChanged,
  onSummaryChange,
}) {
  const services = useResource((signal) => addonApi.bookingServices(booking.id, signal), [
    booking.id,
  ]);
  const venueCatalog = useResource(
    (signal) =>
      mine && ['pending', 'confirmed'].includes(booking.status) && booking.venueId
        ? addonApi.venueServices(booking.venueId, signal)
        : Promise.resolve(null),
    [mine, booking.status, booking.venueId],
  );

  const [serviceId, setServiceId] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const [photoRefreshKey, setPhotoRefreshKey] = useState(0);
  const orderAttempt = useRef(null);

  const bs = services.data?.data;
  useEffect(() => {
    if (bs) onSummaryChange?.(bs);
  }, [bs, onSummaryChange]);

  const activeCatalog = (venueCatalog.data?.data || []).filter((s) => s.active);

  async function runAction(fn, message) {
    setBusy(true);
    setError(null);
    setNotice('');
    try {
      await fn();
      setNotice(message);
      services.reload();
      venueCatalog.reload();
      onBookingChanged?.();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function submitOrder(e) {
    e.preventDefault();
    if (!serviceId) return;
    const payload = { serviceId, quantity: Number(quantity) };
    const fingerprint = JSON.stringify(payload);
    if (orderAttempt.current?.fingerprint !== fingerprint) {
      orderAttempt.current = { fingerprint, key: crypto.randomUUID() };
    }
    setBusy(true);
    setError(null);
    setNotice('');
    try {
      await addonApi.reserveOrder(booking.id, payload, orderAttempt.current.key);
      orderAttempt.current = null;
      setServiceId('');
      setQuantity(1);
      setNotice('Đã đặt giữ dịch vụ kèm lịch.');
      services.reload();
      venueCatalog.reload();
      onBookingChanged?.();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  const canReserve =
    mine &&
    ['pending', 'confirmed'].includes(booking.status) &&
    !bs?.checkedOutAt &&
    activeCatalog.length > 0;

  return (
    <section className="surface booking-addons">
      <div className="section-heading">
        <h2>
          <Package size={20} /> Dịch vụ & nhận/trả sân
        </h2>
        <div className="button-row">
          {owner && booking.status === 'confirmed' && !bs?.checkedInAt && (
            <Button
              loading={busy}
              onClick={() =>
                runAction(() => bookingApi.checkIn(booking.id), 'Đã xác nhận check-in nhận sân.')
              }
            >
              Check-in nhận sân
            </Button>
          )}
          {(mine || owner) &&
            booking.status === 'confirmed' &&
            bs?.checkedInAt &&
            !bs?.checkedOutAt && (
              <Button
                loading={busy}
                onClick={() =>
                  runAction(
                    () => bookingApi.checkOut(booking.id),
                    'Đã check-out và hoàn tất lịch đặt.',
                  )
                }
              >
                Check-out & hoàn tất
              </Button>
            )}
        </div>
      </div>

      {services.loading ? (
        <Loading />
      ) : services.error ? (
        <ErrorBox error={services.error} retry={services.reload} />
      ) : (
        <>
          <div className="bill-breakdown">
            <div>
              <span>Tiền sân</span>
              <strong>{money(bs.courtTotal)}</strong>
            </div>
            <div>
              <span>Dịch vụ đã giao</span>
              <strong>{money(bs.deliveredServicesTotal)}</strong>
            </div>
            <div className="bill-total">
              <span>Tổng cộng</span>
              <strong>{money(bs.total)}</strong>
            </div>
          </div>

          {bs.checkedInAt && (
            <p className="info-note">
              Đã check-in lúc {clockTime(bs.checkedInAt)} ngày {displayDate(bs.checkedInAt)}
              {bs.checkedOutAt
                ? ` · Đã check-out lúc ${clockTime(bs.checkedOutAt)} ngày ${displayDate(bs.checkedOutAt)}`
                : '.'}
            </p>
          )}

          {canReserve && (
            <form className="addon-reserve-form" onSubmit={submitOrder}>
              <Field label="Đặt thêm dịch vụ tại sân">
                <select
                  required
                  value={serviceId}
                  onChange={(e) => setServiceId(e.target.value)}
                >
                  <option value="">Chọn đồ uống hoặc dụng cụ…</option>
                  {activeCatalog.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name} — {money(s.unitPrice)}
                      {s.kind === 'CONSUMABLE' && s.availableStock != null
                        ? ` (còn ${s.availableStock})`
                        : ''}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Số lượng">
                <input
                  required
                  type="number"
                  min={1}
                  max={100}
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                />
              </Field>
              <Button type="submit" loading={busy} disabled={!serviceId}>
                <Plus size={16} /> Đặt dịch vụ
              </Button>
            </form>
          )}

          <ErrorBox error={error} />
          {notice && (
            <p className="success-notice" role="status">
              {notice}
            </p>
          )}

          {!bs.orders.length ? (
            <p className="muted">Chưa có dịch vụ nào gắn với lịch đặt này.</p>
          ) : (
            <div className="addon-orders-list">
              {bs.orders.map((ord) => (
                <article className="addon-order-card" key={ord.id}>
                  <div className="addon-order-top">
                    <div>
                      <strong>
                        {ord.itemName} × {ord.quantity}
                      </strong>
                      <small className="muted">
                        {money(ord.unitPrice)} / đơn vị · Tổng {money(ord.total)}
                      </small>
                    </div>
                    <span className={`status addon-${ord.status.toLowerCase()}`}>
                      {ORDER_STATUS_LABELS[ord.status] || ord.status}
                    </span>
                  </div>

                  {ord.rentals.length > 0 && (
                    <div className="addon-rentals">
                      <small className="muted">
                        Mã thiết bị: {ord.rentals.map((r) => r.assetCode).join(', ')}
                      </small>
                    </div>
                  )}

                  {ord.status === 'RESERVED' && (
                    <div className="button-row">
                      {owner && bs.checkedInAt && (
                        <Button
                          variant="primary"
                          loading={busy}
                          onClick={() =>
                            runAction(
                              () =>
                                addonApi.deliverOrder(
                                  booking.id,
                                  ord.id,
                                  ord.kind === 'RENTAL'
                                    ? { assetIds: ord.rentals.map((r) => r.assetId) }
                                    : {},
                                ),
                              `Đã bàn giao ${ord.itemName}.`,
                            )
                          }
                        >
                          <CheckCircle2 size={15} />
                          {ord.kind === 'RENTAL' ? 'Bàn giao thiết bị' : 'Xác nhận đã giao'}
                        </Button>
                      )}
                      {(mine || owner) && (
                        <Button
                          variant="outline"
                          disabled={busy}
                          onClick={() =>
                            runAction(
                              () => addonApi.cancelOrder(booking.id, ord.id),
                              `Đã hủy món ${ord.itemName}.`,
                            )
                          }
                        >
                          <Trash2 size={15} /> Hủy món
                        </Button>
                      )}
                    </div>
                  )}

                  {ord.rentals
                    .filter((r) => r.handedOutAt != null)
                    .map((rental) => (
                      <RentalPhotosBox
                        key={`${rental.id}:${photoRefreshKey}`}
                        rental={rental}
                        mine={mine}
                        bookingStatus={booking.status}
                        onChanged={() => {
                          setPhotoRefreshKey((k) => k + 1);
                          services.reload();
                        }}
                      />
                    ))}
                </article>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  );
}

function RentalPhotosBox({ rental, mine, bookingStatus, onChanged }) {
  const photos = useResource((signal) => rentalPhotoApi.list(rental.id, signal), [rental.id]);
  const [busySlot, setBusySlot] = useState('');
  const [error, setError] = useState(null);
  const [preview, setPreview] = useState(null);

  useEffect(() => {
    return () => {
      if (preview?.isBlob && preview.url) URL.revokeObjectURL(preview.url);
    };
  }, [preview]);

  function closePreview() {
    if (preview?.isBlob && preview.url) URL.revokeObjectURL(preview.url);
    setPreview(null);
  }

  const list = photos.data?.data || [];
  const checkInCount = list.filter((p) => p.phase === 'CHECK_IN').length;
  const hasCheckOut = list.some((p) => p.phase === 'CHECK_OUT');
  const canUploadAny = mine && bookingStatus === 'confirmed' && !rental.returnedAt;

  async function handleUpload(phase, angle, file) {
    if (!file) return;
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      setError({ message: 'Chỉ chấp nhận ảnh JPEG hoặc PNG.' });
      return;
    }
    if (file.size > MAX_PHOTO_BYTES) {
      setError({ message: 'Ảnh vượt quá giới hạn 5 MB.' });
      return;
    }
    const slotKey = `${phase}:${angle}`;
    setBusySlot(slotKey);
    setError(null);
    try {
      await rentalPhotoApi.upload(rental.id, { phase, angle, file });
      photos.reload();
      onChanged?.();
    } catch (err) {
      setError(err);
    } finally {
      setBusySlot('');
    }
  }

  async function viewPhoto(photo, label) {
    setError(null);
    try {
      const res = await rentalPhotoApi.link(rental.id, photo.id);
      setPreview({ url: res.data.url, expiresAt: res.data.expiresAt, label, isBlob: false });
    } catch (err) {
      if (err?.status === 409) {
        try {
          const blob = await rentalPhotoApi.download(rental.id, photo.id);
          const url = URL.createObjectURL(blob);
          setPreview({ url, label, isBlob: true });
        } catch (dlErr) {
          setError(dlErr);
        }
      } else {
        setError(err);
      }
    }
  }

  return (
    <div className="rental-photos-box">
      <div className="rental-photos-header">
        <strong>
          <Camera size={15} /> Ảnh đối chiếu thiết bị {rental.assetCode}
        </strong>
        <small className="muted">
          {rental.returnedAt
            ? `Đã trả lúc ${clockTime(rental.returnedAt)}`
            : 'Cần đủ 2 ảnh nhận (Toàn cảnh + Chi tiết) rồi 2 ảnh trả trước khi check-out'}
        </small>
      </div>

      {photos.loading ? (
        <Loading />
      ) : photos.error ? (
        <ErrorBox error={photos.error} retry={photos.reload} />
      ) : (
        <div className="photo-grid">
          {PHOTO_SLOTS.map((slot) => {
            const found = list.find((p) => p.phase === slot.phase && p.angle === slot.angle);
            const slotKey = `${slot.phase}:${slot.angle}`;
            const locked =
              !canUploadAny ||
              (slot.phase === 'CHECK_IN' && hasCheckOut) ||
              (slot.phase === 'CHECK_OUT' && checkInCount < 2);
            return (
              <div className="photo-slot" key={slotKey}>
                <span className="photo-slot-label">{slot.label}</span>
                <small className="muted">
                  {found ? `Đã tải (${clockTime(found.uploadedAt)})` : 'Chưa có ảnh'}
                </small>
                <div className="photo-slot-actions">
                  {found && (
                    <button
                      type="button"
                      className="text-button"
                      onClick={() => viewPhoto(found, `${rental.assetCode} · ${slot.label}`)}
                    >
                      <Eye size={14} /> Xem ảnh
                    </button>
                  )}
                  {!locked && (
                    <label className="photo-upload-label">
                      <Upload size={14} />
                      <span>{busySlot === slotKey ? 'Đang tải…' : found ? 'Chụp lại' : 'Tải ảnh'}</span>
                      <input
                        type="file"
                        accept="image/jpeg,image/png"
                        disabled={Boolean(busySlot)}
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          e.target.value = '';
                          handleUpload(slot.phase, slot.angle, file);
                        }}
                      />
                    </label>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <ErrorBox error={error} />

      {preview && (
        <Modal title={preview.label} onClose={closePreview} wide>
          <img className="photo-preview-img" src={preview.url} alt={preview.label} />
          {!preview.isBlob && (
            <p className="muted">
              <a href={preview.url} target="_blank" rel="noreferrer" className="text-button">
                Mở ảnh gốc trong tab mới (link có hiệu lực 5 phút) →
              </a>
            </p>
          )}
        </Modal>
      )}
    </div>
  );
}
