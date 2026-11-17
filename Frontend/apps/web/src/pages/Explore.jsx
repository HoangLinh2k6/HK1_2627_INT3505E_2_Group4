import { Link, useSearchParams } from 'react-router-dom';
import {
  ArrowUpRight,
  Search,
  MapPin,
  CalendarDays,
  Star,
  ChevronLeft,
  ChevronRight,
  X,
} from 'lucide-react';
import { venueApi, sportApi } from '../api/services';
import { useResource } from '../hooks/useResource';
import { Button, ErrorBox, Loading, EmptyState, imageFor, districts } from '../components/ui';
import { localDate, addDays, money, bookingDate } from '../../../../packages/shared/time';

export default function Explore() {
  const [params, setParams] = useSearchParams();
  const date = bookingDate(params.get('date'));
  const query = {
    keyword: params.get('keyword') || '',
    district: params.get('district') || '',
    city: params.get('city') || '',
    sportTypeId: params.get('sportTypeId') || '',
    teamSize: params.get('teamSize') || '',
    maxPrice: params.get('maxPrice') || '',
    sort: params.get('sort') || '-rating,minPrice',
    page: params.get('page') || '1',
    limit: 6,
  };
  const resource = useResource((signal) => venueApi.list(query, signal), [params.toString()]);
  const sports = useResource((signal) => sportApi.list(signal), []);
  const hasFilters = Boolean(
    query.keyword || query.district || query.city || query.sportTypeId || query.teamSize || query.maxPrice,
  );

  function update(values) {
    const next = new URLSearchParams(params);
    next.delete('page');
    Object.entries(values).forEach(([key, value]) =>
      value ? next.set(key, value) : next.delete(key),
    );
    setParams(next, { preventScrollReset: true });
  }

  function search(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    update(Object.fromEntries(data));
    document.getElementById('pitch-results').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  return (
    <div className="container explore-page">
      <section className="explore-top">
        <div className="hero">
          <div className="hero-photo" />
          <div className="hero-content">
            <h1>
              Đủ đội rồi. <em>Tìm sân thôi.</em>
            </h1>
          </div>
        </div>

        <form
          className="search-panel"
          onSubmit={search}
          key={params.get('keyword') + ':' + params.get('district') + ':' + date}
        >
          <label className="search-field search-keyword">
            <Search size={18} aria-hidden="true" />
            <span>
              <small>Tìm sân</small>
              <input
                name="keyword"
                defaultValue={query.keyword}
                placeholder="Tên sân, địa chỉ…"
                aria-label="Tìm tên sân hoặc địa chỉ"
              />
            </span>
          </label>
          <label className="search-field">
            <MapPin size={18} aria-hidden="true" />
            <span>
              <small>Khu vực</small>
              <input
                name="district"
                list="district-suggestions"
                defaultValue={query.district}
                aria-label="Khu vực"
                placeholder="Tất cả khu vực"
              />
              <datalist id="district-suggestions">
                {districts.map((d) => (
                  <option key={d} value={d} />
                ))}
              </datalist>
            </span>
          </label>
          <label className="search-field">
            <CalendarDays size={18} aria-hidden="true" />
            <span>
              <small>Ngày ra sân</small>
              <input
                name="date"
                type="date"
                min={localDate()}
                max={addDays(localDate(), 30)}
                defaultValue={date}
                aria-label="Ngày ra sân"
                required
              />
            </span>
          </label>
          <Button type="submit">
            <Search size={16} aria-hidden="true" /> Tìm sân
          </Button>
        </form>

        <div className="quick-locations">
          <span>Gợi ý gần bạn:</span>
          {['Cầu Giấy', 'Nam Từ Liêm', 'Tây Hồ'].map((d) => (
            <button
              key={d}
              type="button"
              className={query.district === d ? 'active' : ''}
              onClick={() => update({ district: query.district === d ? '' : d })}
            >
              {d}
            </button>
          ))}
        </div>
      </section>

      <section id="pitch-results" className="results-section">
        <div className="section-heading">
          <h2>Cơ sở phù hợp</h2>
          <span className="results-count">
            {resource.data ? `${resource.data.meta.total} cơ sở` : 'Đang tìm…'}
          </span>
        </div>

        {/* Horizontal filter bar above full-width grid */}
        <div className="filter-bar" role="region" aria-label="Bộ lọc tìm kiếm">
          <div className="filter-chips" role="group" aria-label="Số người mỗi đội">
            {[
              ['', 'Tất cả loại sân'],
              ['5', 'Sân 5'],
              ['7', 'Sân 7'],
              ['11', 'Sân 11'],
            ].map(([val, label]) => (
              <button
                key={val || 'all'}
                type="button"
                className={query.teamSize === val ? 'chip selected' : 'chip'}
                aria-pressed={query.teamSize === val}
                onClick={() => update({ teamSize: val })}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="filter-controls">
            <select
              aria-label="Môn thể thao"
              value={query.sportTypeId}
              onChange={(e) => update({ sportTypeId: e.target.value, teamSize: '' })}
            >
              <option value="">Tất cả môn</option>
              {sports.data?.data.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>

            <input
              aria-label="Thành phố"
              list="city-suggestions"
              placeholder="Tất cả thành phố"
              value={query.city}
              onChange={(e) => update({ city: e.target.value, district: '' })}
            />
            <datalist id="city-suggestions">
              <option value="Hà Nội" />
              <option value="Hồ Chí Minh" />
              <option value="Đà Nẵng" />
            </datalist>

            <select
              aria-label="Giá sân tối đa"
              value={query.maxPrice}
              onChange={(e) => update({ maxPrice: e.target.value })}
            >
              <option value="">Mọi mức giá</option>
              <option value="300000">Đến 300.000đ/giờ</option>
              <option value="400000">Đến 400.000đ/giờ</option>
              <option value="600000">Đến 600.000đ/giờ</option>
            </select>

            <select
              aria-label="Sắp xếp sân"
              value={query.sort}
              onChange={(e) => update({ sort: e.target.value })}
            >
              <option value="-rating,minPrice">Đánh giá cao nhất</option>
              <option value="minPrice,name">Giá tăng dần</option>
              <option value="-minPrice,name">Giá giảm dần</option>
              <option value="name">Tên A → Z</option>
            </select>

            {hasFilters && (
              <button
                type="button"
                className="text-button filter-reset"
                onClick={() => setParams({ date })}
              >
                <X size={14} aria-hidden="true" /> Đặt lại
              </button>
            )}
          </div>
        </div>

        {sports.error && <ErrorBox error={sports.error} retry={sports.reload} />}

        {resource.loading ? (
          <Loading cards />
        ) : resource.error ? (
          <ErrorBox error={resource.error} retry={resource.reload} />
        ) : resource.data.data.length === 0 ? (
          <EmptyState
            icon={Search}
            title="Chưa tìm thấy sân phù hợp"
            action={
              <Button variant="outline" onClick={() => setParams({ date })}>
                Xóa bộ lọc
              </Button>
            }
          >
            Thử chọn khu vực khác hoặc mở rộng mức giá để xem thêm sân trống.
          </EmptyState>
        ) : (
          <>
            <div className="pitch-grid">
              {resource.data.data.map((venue) => (
                <VenueCard
                  key={venue.id}
                  venue={venue}
                  date={date}
                  teamSize={query.teamSize}
                  sportTypeId={query.sportTypeId}
                />
              ))}
            </div>
            <div className="pagination">
              <span>
                Trang {resource.data.meta.page} / {resource.data.meta.totalPages}
              </span>
              <div>
                <button
                  className="icon-button"
                  aria-label="Trang trước"
                  disabled={!resource.data.links.prev}
                  onClick={() => {
                    const p = new URLSearchParams(params);
                    p.set('page', String(Number(query.page) - 1));
                    setParams(p, { preventScrollReset: true });
                  }}
                >
                  <ChevronLeft size={18} aria-hidden="true" />
                </button>
                <span className="current-page">{query.page}</span>
                <button
                  className="icon-button"
                  aria-label="Trang tiếp theo"
                  disabled={!resource.data.links.next}
                  onClick={() => {
                    const p = new URLSearchParams(params);
                    p.set('page', String(Number(query.page) + 1));
                    setParams(p, { preventScrollReset: true });
                  }}
                >
                  <ChevronRight size={18} aria-hidden="true" />
                </button>
              </div>
            </div>
          </>
        )}
      </section>
    </div>
  );
}

function VenueCard({ venue: pitch, date, teamSize, sportTypeId }) {
  const href = `/co-so/${pitch.id}?${new URLSearchParams({ date, ...(teamSize ? { teamSize } : {}), ...(sportTypeId ? { sportTypeId } : {}) })}`;
  return (
    <article className="pitch-card">
      <Link className="card-image" to={href} tabIndex={-1} aria-hidden="true">
        <img src={imageFor(pitch.imageKey)} alt="" loading="lazy" />
        {pitch.rating >= 4.8 && <span className="card-pick">Nổi bật</span>}
      </Link>
      <div className="card-body">
        <div className="card-location">
          <MapPin size={14} aria-hidden="true" />
          <span>
            {pitch.district} · {pitch.courtCount} sân
          </span>
          <span className="card-rating">
            <Star size={14} fill="currentColor" aria-hidden="true" /> {pitch.rating.toFixed(1)}{' '}
            <small>({pitch.reviewCount})</small>
          </span>
        </div>
        <h3>
          <Link to={href}>{pitch.name}</Link>
        </h3>
        <p className="card-pitch-types">
          {pitch.courtTypes
            .map(
              (item) =>
                `${item.count} sân ${item.teamSize ? item.teamSize + ' người' : item.sportName}`,
            )
            .join(' · ') || 'Chưa có sân đang hoạt động'}
        </p>
        <div className="card-bottom">
          <div>
            <small>Giá từ</small>
            <strong>
              {pitch.minPrice === null ? 'Chưa có giá' : money(pitch.minPrice)}
              {pitch.minPrice !== null && <span> / giờ</span>}
            </strong>
          </div>
          <Link className="card-arrow" to={href} aria-label={`Xem ${pitch.name}`}>
            <ArrowUpRight size={18} aria-hidden="true" />
          </Link>
        </div>
      </div>
    </article>
  );
}
