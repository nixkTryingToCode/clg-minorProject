import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { api } from './api.js';

export function Alert({ kind = 'error', children }) {
  if (!children) return null;
  return <div className={`alert ${kind}`}>{children}</div>;
}

export function Spinner({ label = 'Loading…' }) {
  return <p className="muted">{label}</p>;
}

export function Chips({ items, tone }) {
  const list = Array.isArray(items) ? items : (items || '').split(',').map((s) => s.trim()).filter(Boolean);
  if (!list.length) return <span className="muted">—</span>;
  return (
    <span className="chips">
      {list.map((s) => <span key={s} className={`chip ${tone || ''}`}>{s}</span>)}
    </span>
  );
}

export function ScoreRing({ value, size = 64 }) {
  const r = (size - 8) / 2;
  const c = 2 * Math.PI * r;
  const v = Math.max(0, Math.min(100, value ?? 0));
  const color = v >= 70 ? 'var(--good)' : v >= 40 ? 'var(--warn)' : 'var(--bad)';
  return (
    <svg width={size} height={size} role="img" aria-label={`${v} percent`}>
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--line)" strokeWidth="6" />
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth="6" strokeLinecap="round"
        strokeDasharray={c} strokeDashoffset={c * (1 - v / 100)} transform={`rotate(-90 ${size / 2} ${size / 2})`} />
      <text x="50%" y="50%" dominantBaseline="central" textAnchor="middle" fontSize={size / 3.4} fontWeight="700" fill="var(--text)">{v}%</text>
    </svg>
  );
}

export function Bar({ label, value }) {
  return (
    <div className="bar-row">
      <span>{label}</span>
      <div className="bar"><div style={{ width: `${value}%` }} /></div>
      <b>{value}%</b>
    </div>
  );
}

export function StatusBadge({ status }) {
  return <span className={`badge s-${(status || '').toLowerCase()}`}>{status}</span>;
}

export const STATUSES = ['APPLIED', 'SCREENING', 'SHORTLISTED', 'INTERVIEW', 'SELECTED', 'REJECTED', 'WITHDRAWN'];

export function useLoad(fn, deps = []) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let alive = true;
    setLoading(true);
    fn().then((d) => { if (alive) { setData(d); setError(''); } })
      .catch((e) => { if (alive) setError(e.message); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  return { data, error, loading, reload: () => setTick((t) => t + 1), setData };
}

export const fmtDate = (s) => (s ? new Date(s).toLocaleString() : '');

/** Compact relative time for list rows; the full timestamp stays in the title attribute. */
export const timeAgo = (s) => {
  if (!s) return '';
  const secs = (Date.now() - new Date(s).getTime()) / 1000;
  if (secs < 60) return 'just now';
  if (secs < 3600) return `${Math.floor(secs / 60)}m ago`;
  if (secs < 86400) return `${Math.floor(secs / 3600)}h ago`;
  if (secs < 604800) return `${Math.floor(secs / 86400)}d ago`;
  return new Date(s).toLocaleDateString();
};

/** Where a notification points: the exact application, the job's applicants page, or the evidence list. */
export const notificationLink = (n) => {
  if (!n) return '/';
  if (n.type === 'EVIDENCE') return '/evidence';
  if (n.type === 'NEW_APPLICATION' || n.type === 'WITHDRAWN') return n.jobId ? `/jobs/${n.jobId}/applicants` : '/jobs';
  if (n.type === 'STATUS' || n.type === 'AUTO_APPLY') return n.linkId ? `/applications?a=${n.linkId}` : '/applications';
  if (n.type === 'COMPANY_VERIFIED') return '/company';
  if (n.type === 'INVITE_ACCEPTED') return '/recruiters';
  return '/';
};

/**
 * Company permission check for the UI. Users without a company (and admins) have
 * full access, so only an explicit permissions list can restrict the view.
 */
export const can = (user, permission) =>
  !user || !Array.isArray(user.permissions) || user.permissions.includes(permission);

/**
 * Header dropdown that groups several routes behind one label.
 * Closes on selection, outside click and Escape; the trigger stays
 * highlighted while the location is inside one of the group's paths.
 */
export function NavMenu({ label, items }) {
  const [open, setOpen] = useState(false);
  const { pathname } = useLocation();
  const wrap = useRef(null);

  const active = items.some(([to]) => (to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(`${to}/`)));

  useEffect(() => { setOpen(false); }, [pathname]);

  useEffect(() => {
    if (!open) return undefined;
    const outside = (e) => { if (wrap.current && !wrap.current.contains(e.target)) setOpen(false); };
    const esc = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', outside);
    document.addEventListener('keydown', esc);
    return () => {
      document.removeEventListener('mousedown', outside);
      document.removeEventListener('keydown', esc);
    };
  }, [open]);

  return (
    <div className="nav-group" ref={wrap}>
      <button type="button" className={active ? 'active' : ''} aria-haspopup="menu" aria-expanded={open}
        onClick={() => setOpen((o) => !o)}>
        {label}<span className="caret" aria-hidden="true">▾</span>
      </button>
      {open && (
        <div className="nav-menu card" role="menu">
          {items.map(([to, text]) => (
            <NavLink key={to} to={to} end={to === '/'} role="menuitem" onClick={() => setOpen(false)}
              className={({ isActive }) => (isActive ? 'active' : '')}>{text}</NavLink>
          ))}
        </div>
      )}
    </div>
  );
}

export function NotificationBell() {
  const [data, setData] = useState({ items: [], unread: 0 });
  const [open, setOpen] = useState(false);
  const wrap = useRef(null);
  const load = () => api('/api/notifications').then(setData).catch(() => {});
  useEffect(() => {
    load();
    const t = setInterval(load, 60000);
    return () => clearInterval(t);
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    const outside = (e) => { if (wrap.current && !wrap.current.contains(e.target)) setOpen(false); };
    const esc = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', outside);
    document.addEventListener('keydown', esc);
    return () => {
      document.removeEventListener('mousedown', outside);
      document.removeEventListener('keydown', esc);
    };
  }, [open]);

  const toggle = async () => {
    const next = !open;
    setOpen(next);
    if (next && data.unread > 0) {
      try { await api('/api/notifications/read-all', { method: 'POST' }); setData({ ...data, unread: 0 }); } catch { /* ignore */ }
    }
  };
  return (
    <div className="bell" ref={wrap}>
      <button className="btn ghost" onClick={toggle} aria-label="Notifications" aria-haspopup="menu" aria-expanded={open}>🔔{data.unread > 0 && <span className="dot">{data.unread}</span>}</button>
      {open && (
        <div className="bell-menu card" role="menu">
          <div className="bell-head">
            <b>Notifications</b>
            {data.unread > 0 && <span className="muted small">{data.unread} new</span>}
          </div>
          <div className="bell-list-wrap">
            <div className="bell-list">
              {data.items.length === 0 ? <p className="muted small">Nothing yet.</p> : data.items.slice(0, 20).map((n) => (
                <Link key={n.id} to={notificationLink(n)} role="menuitem" onClick={() => setOpen(false)}
                  className="bell-item" title={fmtDate(n.createdAt)}>
                  <span className={`n-dot ${n.type}`} aria-hidden="true" />
                  <span className="bell-text">{n.message}<span className="muted small"> · {timeAgo(n.createdAt)}</span></span>
                </Link>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
