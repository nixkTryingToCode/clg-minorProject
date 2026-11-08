import { useEffect, useState } from 'react';

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
