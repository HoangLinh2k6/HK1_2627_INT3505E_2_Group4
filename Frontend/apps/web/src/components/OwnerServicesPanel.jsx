import { useState } from 'react';
import { Package, Plus, Pencil, Wrench } from 'lucide-react';
import { addonApi } from '../api/services';
import { useResource } from '../hooks/useResource';
import { Button, Field, Modal, ErrorBox, EmptyState, Loading } from './ui';
import { money } from '../../../../packages/shared/time';

const KIND_LABELS = {
  CONSUMABLE: 'Đồ tiêu dùng',
  RENTAL: 'Thuê dụng cụ',
};

const ASSET_STATE_LABELS = {
  AVAILABLE: 'Sẵn sàng',
  MAINTENANCE: 'Bảo trì',
  RETIRED: 'Ngừng dùng',
};

export default function OwnerServicesPanel({ venueId }) {
  const services = useResource((signal) => addonApi.venueServices(venueId, signal), [venueId]);
  const [editing, setEditing] = useState(null);
  const [assetsFor, setAssetsFor] = useState(null);

  return (
    <>
      <div className="owner-toolbar">
        <span>{services.data?.data.length ?? '…'} dịch vụ tại cơ sở</span>
        <Button onClick={() => setEditing({})}>
          <Plus size={17} />
          Thêm dịch vụ
        </Button>
      </div>

      {services.loading ? (
        <Loading />
      ) : services.error ? (
        <ErrorBox error={services.error} retry={services.reload} />
      ) : !services.data.data.length ? (
        <EmptyState icon={Package} title="Cơ sở chưa có dịch vụ bổ sung">
          Thêm đồ uống hoặc dụng cụ cho thuê để khách đặt kèm lịch sân.
        </EmptyState>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Tên dịch vụ</th>
                <th>Loại</th>
                <th>Đơn giá</th>
                <th>Tồn kho / Khả dụng</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {services.data.data.map((s) => (
                <tr key={s.id}>
                  <td>
                    <strong>{s.name}</strong>
                  </td>
                  <td>{KIND_LABELS[s.kind] || s.kind}</td>
                  <td>{money(s.unitPrice)}</td>
                  <td>
                    {s.kind === 'CONSUMABLE'
                      ? `${s.availableStock ?? 0} / ${s.stockOnHand ?? 0}`
                      : 'Theo mã thiết bị'}
                  </td>
                  <td>{s.active ? 'Đang bán' : 'Tạm ngừng'}</td>
                  <td>
                    <div className="table-actions">
                      {s.kind === 'RENTAL' && (
                        <button
                          type="button"
                          className="text-button"
                          onClick={() => setAssetsFor(s)}
                        >
                          <Wrench size={15} /> Thiết bị
                        </button>
                      )}
                      <button
                        type="button"
                        className="icon-button"
                        aria-label={`Sửa ${s.name}`}
                        onClick={() => setEditing(s)}
                      >
                        <Pencil size={17} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <ServiceFormModal
          venueId={venueId}
          service={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            services.reload();
          }}
        />
      )}

      {assetsFor && (
        <AssetsModal
          venueId={venueId}
          service={assetsFor}
          onClose={() => {
            setAssetsFor(null);
            services.reload();
          }}
        />
      )}
    </>
  );
}

function ServiceFormModal({ venueId, service: s, onClose, onSaved }) {
  const isEdit = Boolean(s.id);
  const [name, setName] = useState(s.name || '');
  const [kind, setKind] = useState(s.kind || 'CONSUMABLE');
  const [unitPrice, setUnitPrice] = useState(s.unitPrice ?? 20000);
  const [stockOnHand, setStockOnHand] = useState(s.stockOnHand ?? 20);
  const [active, setActive] = useState(s.active !== undefined ? s.active : true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (isEdit) {
        await addonApi.updateVenueService(venueId, s.id, {
          name: name.trim(),
          unitPrice: Number(unitPrice),
          active,
          ...(s.kind === 'CONSUMABLE' ? { stockOnHand: Number(stockOnHand) } : {}),
        });
      } else {
        await addonApi.createVenueService(venueId, {
          name: name.trim(),
          kind,
          unitPrice: Number(unitPrice),
          ...(kind === 'CONSUMABLE' ? { stockOnHand: Number(stockOnHand) } : {}),
        });
      }
      onSaved();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={isEdit ? 'Cập nhật dịch vụ' : 'Thêm dịch vụ mới'} onClose={onClose}>
      <form className="form-stack" onSubmit={submit}>
        <Field label="Tên dịch vụ">
          <input
            required
            maxLength={120}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Nước suối 500ml hoặc Thuê vợt cầu lông"
          />
        </Field>
        {!isEdit ? (
          <Field label="Loại dịch vụ">
            <select value={kind} onChange={(e) => setKind(e.target.value)}>
              <option value="CONSUMABLE">Đồ tiêu dùng (quản lý theo số lượng kho)</option>
              <option value="RENTAL">Thuê dụng cụ (quản lý theo từng mã thiết bị)</option>
            </select>
          </Field>
        ) : (
          <p className="muted">Loại dịch vụ: {KIND_LABELS[s.kind] || s.kind}</p>
        )}
        <Field label="Đơn giá (VND)">
          <input
            required
            type="number"
            min={0}
            max={100000000}
            step={1000}
            value={unitPrice}
            onChange={(e) => setUnitPrice(e.target.value)}
          />
        </Field>
        {kind === 'CONSUMABLE' && (
          <Field label="Số lượng tồn kho">
            <input
              required
              type="number"
              min={0}
              max={1000000}
              value={stockOnHand}
              onChange={(e) => setStockOnHand(e.target.value)}
            />
          </Field>
        )}
        {isEdit && (
          <Field label="Trạng thái">
            <select
              value={active ? 'active' : 'inactive'}
              onChange={(e) => setActive(e.target.value === 'active')}
            >
              <option value="active">Đang bán</option>
              <option value="inactive">Tạm ngừng</option>
            </select>
          </Field>
        )}
        <ErrorBox error={error} />
        <Button type="submit" loading={busy}>
          Lưu dịch vụ
        </Button>
      </form>
    </Modal>
  );
}

function AssetsModal({ venueId, service, onClose }) {
  const assets = useResource(
    (signal) => addonApi.listAssets(venueId, service.id, signal),
    [venueId, service.id],
  );
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function addAsset(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await addonApi.createAsset(venueId, service.id, { code: code.trim() });
      setCode('');
      assets.reload();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function changeState(assetId, state) {
    setBusy(true);
    setError(null);
    try {
      await addonApi.updateAsset(venueId, service.id, assetId, { state });
      assets.reload();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={`Thiết bị · ${service.name}`} onClose={onClose} wide>
      <form className="asset-add-form" onSubmit={addAsset}>
        <Field label="Mã thiết bị mới (chữ, số, ., _, -)">
          <input
            required
            pattern="[A-Za-z0-9._-]{1,50}"
            maxLength={50}
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="VD: VOT-007"
          />
        </Field>
        <Button type="submit" loading={busy}>
          <Plus size={16} /> Thêm mã
        </Button>
      </form>

      <ErrorBox error={error} />

      {assets.loading ? (
        <Loading />
      ) : assets.error ? (
        <ErrorBox error={assets.error} retry={assets.reload} />
      ) : !assets.data.data.length ? (
        <EmptyState title="Chưa có thiết bị nào">
          Đăng ký từng mã thiết bị để hệ thống cấp phát khi khách thuê.
        </EmptyState>
      ) : (
        <div className="asset-list">
          {assets.data.data.map((a) => (
            <div className="asset-row" key={a.id}>
              <div>
                <strong>{a.code}</strong>
                <small className="muted">{ASSET_STATE_LABELS[a.state] || a.state}</small>
              </div>
              <select
                aria-label={`Trạng thái thiết bị ${a.code}`}
                disabled={busy}
                value={a.state}
                onChange={(e) => changeState(a.id, e.target.value)}
              >
                <option value="AVAILABLE">Sẵn sàng</option>
                <option value="MAINTENANCE">Bảo trì</option>
                <option value="RETIRED">Ngừng dùng</option>
              </select>
            </div>
          ))}
        </div>
      )}
    </Modal>
  );
}
