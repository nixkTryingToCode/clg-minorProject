import { Fragment, useEffect, useState } from 'react';
import { api, downloadAuth } from '../api.js';
import { Alert, Bar, Spinner, StatusBadge, fmtDate, useLoad } from '../components.jsx';
import { IngestPanel } from './admin_more.jsx';

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
      <div className="grid three">
        <div className="card stat"><span>External jobs</span><b>{data.externalJobs}</b></div>
        <div className="card stat"><span>Auto-applications</span><b>{data.autoApplications}</b></div>
        <div className="card stat"><span>Evidence awaiting review</span><b>{data.pendingEvidence}</b></div>
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
      <IngestPanel onDone={reload} />
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

const CO_TONE = { ACTIVE: 's-selected', PENDING_VERIFICATION: 's-screening', REJECTED: 's-rejected', SUSPENDED: 's-withdrawn' };
const coLabel = (s) => (s || '').toLowerCase().replace(/_/g, ' ');

export function AdminCompanies() {
  const { data, error, loading, reload } = useLoad(() => api('/api/admin/companies'));
  const auto = useLoad(() => api('/api/admin/companies/auto-verify'));
  const [msg, setMsg] = useState({ kind: 'error', text: '' });
  const [openDocs, setOpenDocs] = useState(null);
  const [docs, setDocs] = useState(null);
  const [busy, setBusy] = useState(false);

  const setStatus = async (c, status) => {
    setMsg({ kind: 'error', text: '' });
    try {
      await api(`/api/admin/companies/${c.id}/status`, { method: 'PATCH', body: { status } });
      setMsg({ kind: 'success', text: `${c.name} is now ${coLabel(status)}.` });
      reload();
    } catch (e) { setMsg({ kind: 'error', text: e.message }); }
  };

  const toggleAuto = async () => {
    setBusy(true);
    setMsg({ kind: 'error', text: '' });
    try {
      const r = await api('/api/admin/companies/auto-verify', { method: 'PUT', body: { enabled: !auto.data.enabled } });
      setMsg({
        kind: 'success',
        text: r.enabled
          ? `Auto verification is on — ${r.verified} pending compan${r.verified === 1 ? 'y was' : 'ies were'} verified.`
          : 'Auto verification is off — new registrations now wait for your review.',
      });
      auto.reload();
      reload();
    } catch (e) { setMsg({ kind: 'error', text: e.message }); }
    finally { setBusy(false); }
  };

  const showDocs = async (c) => {
    if (openDocs === c.id) { setOpenDocs(null); setDocs(null); return; }
    setMsg({ kind: 'error', text: '' });
    try {
      setDocs(await api(`/api/admin/companies/${c.id}/documents`));
      setOpenDocs(c.id);
    } catch (e) { setMsg({ kind: 'error', text: e.message }); }
  };

  const grab = async (c, d) => {
    try { await downloadAuth(`/api/admin/companies/${c.id}/documents/${d.id}`, d.fileName); }
    catch (e) { setMsg({ kind: 'error', text: e.message }); }
  };

  const pending = (data || []).filter((c) => c.status === 'PENDING_VERIFICATION').length;
  return (
    <div className="stack-lg">
      <h1>Companies</h1>
      {auto.data && (
        <section className="card row between wrap">
          <div>
            <h3>Auto verification</h3>
            <p className="muted small" style={{ maxWidth: '48rem' }}>
              When on, new company registrations are verified immediately. Turn it off to review every company
              yourself — turning it back on verifies everything still waiting.
            </p>
          </div>
          <button className={`btn ${auto.data.enabled ? 'primary' : ''}`} disabled={busy} onClick={toggleAuto}>
            {auto.data.enabled ? 'On — turn off' : 'Off — turn on'}
          </button>
        </section>
      )}
      <Alert kind={msg.kind}>{msg.text}</Alert>
      <Alert>{error}</Alert>
      {loading ? <Spinner /> : (
        <>
          <p className="muted">{(data || []).length} compan{(data || []).length === 1 ? 'y' : 'ies'} · {pending} awaiting verification.</p>
          <div className="card table-wrap">
            <table>
              <thead><tr><th>Company</th><th>Domain</th><th>Owner</th><th>Members</th><th>Jobs</th><th>Docs</th><th>Status</th><th>Actions</th></tr></thead>
              <tbody>
                {(data || []).map((c) => (
                  <Fragment key={c.id}>
                    <tr>
                      <td>{c.name}<span className="muted small"><br />{c.location || '—'}</span></td>
                      <td>{c.emailDomain}</td>
                      <td>{c.ownerEmail || '—'}<span className="muted small"><br />{c.ownerName || ''}</span></td>
                      <td>{c.memberCount}</td>
                      <td>{c.jobCount}</td>
                      <td><button className="btn ghost" onClick={() => showDocs(c)}>{c.documentCount} {openDocs === c.id ? '▾' : '▸'}</button></td>
                      <td><span className={`badge ${CO_TONE[c.status] || ''}`}>{coLabel(c.status)}</span></td>
                      <td className="row gap wrap">
                        {c.status !== 'ACTIVE' && <button className="btn primary" onClick={() => setStatus(c, 'ACTIVE')}>Verify</button>}
                        {c.status === 'PENDING_VERIFICATION' && <button className="btn ghost" onClick={() => setStatus(c, 'REJECTED')}>Reject</button>}
                        {c.status === 'ACTIVE' && <button className="btn ghost danger" onClick={() => setStatus(c, 'SUSPENDED')}>Suspend</button>}
                      </td>
                    </tr>
                    {openDocs === c.id && (
                      <tr>
                        <td colSpan={8}>
                          {docs === null ? <Spinner label="Loading documents…" /> : docs.length === 0
                            ? <p className="muted small">No verification documents were uploaded.</p>
                            : docs.map((d) => (
                              <p key={d.id} className="small row between" style={{ maxWidth: '40rem' }}>
                                <span>{d.fileName} <span className="muted">· {d.mimeType || 'file'} · {fmtDate(d.uploadedAt)}</span></span>
                                <button className="btn ghost" onClick={() => grab(c, d)}>Download</button>
                              </p>
                            ))}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}

export function AdminAudit() {  const { data, error, loading } = useLoad(() => api('/api/admin/audit'));
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

const LABELS = { skills: 'Required skills', experience: 'Experience', projects: 'Projects / evidence', role: 'Role relevance', education: 'Education & certifications', semantic: 'Semantic similarity' };

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
