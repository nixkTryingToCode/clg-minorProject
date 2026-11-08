import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { Alert, Bar, Spinner, StatusBadge, fmtDate, useLoad } from '../components.jsx';

export function AdminHome() {
  const { data, error, loading } = useLoad(() => api('/api/admin/stats'));
  if (loading) return <Spinner />;
  if (error) return <Alert>{error}</Alert>;
  const max = Math.max(1, ...Object.values(data.applicationsByStatus));
  return (
    <div className="stack-lg">
      <h1>Platform overview</h1>
      <div className="grid four">
        <div className="card stat"><span>Candidates</span><b>{data.candidates}</b></div>
        <div className="card stat"><span>Recruiters</span><b>{data.recruiters}</b></div>
        <div className="card stat"><span>Jobs (active)</span><b>{data.jobs} ({data.activeJobs})</b></div>
        <div className="card stat"><span>Applications</span><b>{data.applications}</b></div>
      </div>
      <section className="card stack">
        <h3>Applications by status</h3>
        {Object.entries(data.applicationsByStatus).map(([k, v]) => <Bar key={k} label={k} value={Math.round((v / max) * 100)} />)}
      </section>
    </div>
  );
}

export function AdminUsers() {
  const { data, error, loading, reload } = useLoad(() => api('/api/admin/users'));
  const [err, setErr] = useState('');
  const toggle = async (u) => {
    try { await api(`/api/admin/users/${u.id}/enabled`, { method: 'PATCH', body: { enabled: !u.enabled } }); reload(); }
    catch (e) { setErr(e.message); }
  };
  return (
    <div className="stack-lg">
      <h1>Users</h1>
      <Alert>{err || error}</Alert>
      {loading ? <Spinner /> : (
        <div className="card table-wrap">
          <table>
            <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Joined</th><th>Status</th><th /></tr></thead>
            <tbody>
              {data.map((u) => (
                <tr key={u.id}>
                  <td>{u.name}</td><td>{u.email}</td><td>{u.role}</td><td>{fmtDate(u.createdAt)}</td>
                  <td>{u.enabled ? 'Active' : 'Disabled'}</td>
                  <td><button className="btn ghost" onClick={() => toggle(u)}>{u.enabled ? 'Disable' : 'Enable'}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export function AdminJobs() {
  const { data, error, loading, reload } = useLoad(() => api('/api/admin/jobs'));
  const [err, setErr] = useState('');
  const toggle = async (j) => {
    try { await api(`/api/admin/jobs/${j.id}/active`, { method: 'PATCH', body: { enabled: !j.active } }); reload(); }
    catch (e) { setErr(e.message); }
  };
  const remove = async (j) => {
    if (!window.confirm(`Delete "${j.title}"?`)) return;
    try { await api(`/api/jobs/${j.id}`, { method: 'DELETE' }); reload(); } catch (e) { setErr(e.message); }
  };
  return (
    <div className="stack-lg">
      <h1>Job moderation</h1>
      <Alert>{err || error}</Alert>
      {loading ? <Spinner /> : (
        <div className="card table-wrap">
          <table>
            <thead><tr><th>Title</th><th>Company</th><th>Source</th><th>Posted</th><th>State</th><th /></tr></thead>
            <tbody>
              {data.map((j) => (
                <tr key={j.id}>
                  <td>{j.title}</td><td>{j.company}</td><td>{j.source}</td><td>{fmtDate(j.createdAt)}</td>
                  <td>{j.active ? <StatusBadge status="SELECTED" /> : <StatusBadge status="WITHDRAWN" />}</td>
                  <td className="row gap">
                    <button className="btn ghost" onClick={() => toggle(j)}>{j.active ? 'Deactivate' : 'Activate'}</button>
                    <button className="btn ghost danger" onClick={() => remove(j)}>Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export function AdminAudit() {
  const { data, error, loading } = useLoad(() => api('/api/admin/audit'));
  return (
    <div className="stack-lg">
      <h1>Audit log</h1>
      <p className="muted">Latest 100 important actions.</p>
      <Alert>{error}</Alert>
      {loading ? <Spinner /> : (
        <div className="card table-wrap">
          <table>
            <thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Entity</th><th>Details</th></tr></thead>
            <tbody>
              {data.map((l) => (
                <tr key={l.id}><td>{fmtDate(l.createdAt)}</td><td>{l.actorId ?? '-'}</td><td>{l.action}</td><td>{l.entity} {l.entityId ?? ''}</td><td>{l.details}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

const LABELS = { skills: 'Required skills', experience: 'Experience', projects: 'Projects / evidence', role: 'Role relevance', education: 'Education & certifications' };

export function AdminSettings() {
  const { data, error, loading } = useLoad(() => api('/api/admin/weights'));
  const [w, setW] = useState(null);
  const [msg, setMsg] = useState({ kind: 'error', text: '' });
  useEffect(() => { if (data) setW(data); }, [data]);
  if (loading || !w) return error ? <Alert>{error}</Alert> : <Spinner />;
  const total = Object.values(w).reduce((a, b) => a + Number(b || 0), 0);
  const save = async (e) => {
    e.preventDefault();
    try {
      const body = Object.fromEntries(Object.entries(w).map(([k, v]) => [k, Number(v)]));
      setW(await api('/api/admin/weights', { method: 'PUT', body }));
      setMsg({ kind: 'success', text: 'Weights saved. New scores use them immediately.' });
    } catch (err) { setMsg({ kind: 'error', text: err.message }); }
  };
  return (
    <form className="card stack narrow" onSubmit={save}>
      <h1>Compatibility scoring weights</h1>
      <p className="muted">Relative importance of each factor (they are normalised, so they don't have to add to 100). Current total: {total}.</p>
      <Alert kind={msg.kind}>{msg.text}</Alert>
      {Object.keys(LABELS).map((k) => (
        <label key={k}>{LABELS[k]}: <b>{w[k]}</b>
          <input type="range" min="0" max="100" value={w[k]} onChange={(e) => setW({ ...w, [k]: e.target.value })} />
        </label>
      ))}
      <button className="btn primary">Save weights</button>
    </form>
  );
}
