import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../authContext.js';
import { Alert, Spinner, can, fmtDate, useLoad } from '../components.jsx';
import { Shell } from './auth.jsx';

/** The eight granular permissions a company can grant its members. */
const PERMS = [
  ['POST_JOB', 'Post new jobs'],
  ['EDIT_JOB', 'Edit their own jobs'],
  ['EDIT_ANY_JOB', 'Edit any job of the company'],
  ['VIEW_APPLICANTS', 'View applicants and rankings'],
  ['UPDATE_STATUS', 'Move candidates through the pipeline'],
  ['MANAGE_RECRUITERS', 'Invite and manage recruiters'],
  ['REASSIGN_JOBS', 'Reassign jobs between members'],
  ['VIEW_ANALYTICS', 'View company analytics'],
];
const permLabel = (p) => (PERMS.find(([k]) => k === p) || [p, p])[1];
const ALL_PERMS = PERMS.map(([k]) => k);

const STATUS_TONE = {
  ACTIVE: 's-selected',
  PENDING_VERIFICATION: 's-screening',
  REJECTED: 's-rejected',
  SUSPENDED: 's-withdrawn',
};
const prettyStatus = (s) => (s || '').toLowerCase().replace(/_/g, ' ');

/* ---------------- public pages ---------------- */

export function RegisterCompany() {
  const { login } = useAuth();
  const [form, setForm] = useState({
    ownerName: '', email: '', password: '', name: '', emailDomain: '',
    website: '', industry: '', location: '', description: '',
  });
  const [logo, setLogo] = useState(null);
  const [docs, setDocs] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      const fd = new FormData();
      Object.entries(form).forEach(([k, v]) => { if (v) fd.append(k, v); });
      if (logo) fd.append('logo', logo);
      docs.forEach((f) => fd.append('documents', f));
      login(await api('/api/companies/register', { method: 'POST', form: fd }));
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };

  return (
    <Shell wide title="Register your company" subtitle="Companies are verified by the platform team before they can recruit">
      <form onSubmit={submit} className="stack">
        <Alert>{error}</Alert>
        <h4>Your admin account</h4>
        <label>Full name<input required value={form.ownerName} onChange={set('ownerName')} /></label>
        <div className="grid two">
          <label>Work email<input type="email" required value={form.email} onChange={set('email')} /></label>
          <label>Password (min 8 chars)<input type="password" required minLength={8} value={form.password} onChange={set('password')} /></label>
        </div>
        <h4>Company details</h4>
        <div className="grid two">
          <label>Company name<input required value={form.name} onChange={set('name')} /></label>
          <label>Email domain<input required value={form.emailDomain} onChange={set('emailDomain')} placeholder="yourcompany.com" /></label>
        </div>
        <div className="grid two">
          <label>Website<input value={form.website} onChange={set('website')} placeholder="https://yourcompany.com" /></label>
          <label>Industry<input value={form.industry} onChange={set('industry')} /></label>
        </div>
        <label>Location<input value={form.location} onChange={set('location')} /></label>
        <label>Description<textarea rows={4} value={form.description} onChange={set('description')} /></label>
        <div className="grid two">
          <label>Logo (optional)<input type="file" accept="image/*" onChange={(e) => setLogo(e.target.files?.[0] || null)} /></label>
          <label>Verification documents (optional)<input type="file" multiple onChange={(e) => setDocs(Array.from(e.target.files || []))} /></label>
        </div>
        <button className="btn primary" disabled={busy}>{busy ? 'Submitting…' : 'Register company'}</button>
        <p className="muted small">
          Your company starts as <b>pending verification</b>. Once a platform admin approves it, you can invite
          recruiters and post jobs.
        </p>
      </form>
      <p className="small">Looking for a job instead? <Link to="/register">Create a candidate account</Link></p>
    </Shell>
  );
}

export function InviteAccept() {
  const { token } = useParams();
  const { login } = useAuth();
  const { data, error, loading } = useLoad(() => api(`/api/invitations/${token}`), [token]);
  const [form, setForm] = useState({ name: '', password: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  if (loading) return <div className="center-screen"><Spinner label="Checking your invitation…" /></div>;
  if (error) {
    return (
      <Shell title="Invitation" subtitle="">
        <Alert>{error}</Alert>
        <p className="small"><Link to="/login">Go to login</Link></p>
      </Shell>
    );
  }
  if (!data.valid) {
    const why = data.status === 'EXPIRED' ? 'expired'
      : data.status === 'REVOKED' ? 'revoked'
        : data.status === 'ACCEPTED' ? 'already been used'
          : 'is no longer active';
    return (
      <Shell title="Invitation" subtitle={data.companyName || ''}>
        <Alert>This invitation has {why}.</Alert>
        <p className="small"><Link to="/login">Log in instead</Link></p>
      </Shell>
    );
  }
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setErr('');
    try { login(await api(`/api/invitations/${token}`, { method: 'POST', body: form })); }
    catch (ex) { setErr(ex.message); }
    finally { setBusy(false); }
  };
  return (
    <Shell title={`Join ${data.companyName}`} subtitle={`Invited as ${data.role === 'COMPANY_ADMIN' ? 'company admin' : 'recruiter'} · ${data.email}`}>
      <form onSubmit={submit} className="stack">
        <Alert>{err}</Alert>
        <label>Your name<input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
        <label>Choose a password (min 8 chars)<input type="password" required minLength={8} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
        <div className="stack" style={{ gap: '.4rem' }}>
          <h4>What you'll be able to do</h4>
          <span className="chips">{data.permissions.map((p) => <span className="chip" key={p}>{permLabel(p)}</span>)}</span>
        </div>
        <button className="btn primary" disabled={busy}>{busy ? 'Joining…' : 'Accept invitation'}</button>
      </form>
      <p className="small muted">
        Already have an account on SNAP-HIRE? Log in first and reopen this link — we'll confirm your existing
        password instead of setting a new one.
      </p>
    </Shell>
  );
}

/* ---------------- company profile (/company) ---------------- */

export function Company() {
  const { user } = useAuth();
  const { data, error, loading } = useLoad(() => api('/api/companies/mine'));
  const stats = useLoad(
    () => (can(user, 'VIEW_ANALYTICS') ? api('/api/companies/mine/analytics') : Promise.resolve(null)),
    [user && user.companyId],
  );

  if (loading) return <Spinner />;
  if (error) return <Alert>{error}</Alert>;
  const c = data.company;

  return (
    <div className="stack-lg">
      <div className="row between top wrap">
        <div className="row gap">
          {c.logoBase64
            ? <img className="co-logo" alt="" src={`data:${c.logoMime || 'image/png'};base64,${c.logoBase64}`} />
            : <div className="co-logo ph">{(c.name || '?').slice(0, 1)}</div>}
          <div>
            <h1>{c.name}</h1>
            <p className="muted small">{c.emailDomain}{c.website ? ` · ${c.website}` : ''}{c.location ? ` · ${c.location}` : ''}</p>
          </div>
        </div>
        <span className={`badge ${STATUS_TONE[c.status] || ''}`}>{prettyStatus(c.status)}</span>
      </div>

      {c.status === 'PENDING_VERIFICATION' && (
        <div className="alert warn">
          <b>Waiting for verification.</b> A platform admin is reviewing your registration. Invitations, job
          postings and applicant access unlock as soon as your company is verified.
        </div>
      )}
      {c.status === 'REJECTED' && <div className="alert error">Your company registration was rejected. Please contact the platform admin.</div>}
      {c.status === 'SUSPENDED' && <div className="alert error">Your company is suspended. Recruiting features are disabled.</div>}
      {c.status === 'ACTIVE' && c.verifiedAt && (
        <div className="alert success">Verified {fmtDate(c.verifiedAt)}{c.verifiedBy ? ` by ${c.verifiedBy}` : ''}.</div>
      )}

      <div className="grid four">
        <div className="card stat"><span>Status</span><b>{prettyStatus(c.status)}</b></div>
        <div className="card stat"><span>Members</span><b>{data.members.length}</b></div>
        <div className="card stat"><span>Jobs</span><b>{data.jobCount}</b></div>
        <div className="card stat"><span>Documents</span><b>{c.documentCount}</b></div>
      </div>

      <div className="grid two">
        <section className="card stack">
          <h3>Company profile</h3>
          <p className="small"><b>Industry:</b> {c.industry || '—'}</p>
          <p className="small"><b>Domain:</b> {c.emailDomain}</p>
          <p className="small"><b>Website:</b> {c.website || '—'}</p>
          <p className="small"><b>Location:</b> {c.location || '—'}</p>
          <p className="muted small pre">{c.description || 'No description provided.'}</p>
        </section>
        <section className="card stack">
          <h3>Verification documents</h3>
          {data.documents.length === 0
            ? <p className="muted small">No documents were uploaded with this registration.</p>
            : data.documents.map((d) => (
              <p key={d.id} className="small row between">
                <span>{d.fileName}</span>
                <span className="muted">uploaded {fmtDate(d.uploadedAt)}</span>
              </p>
            ))}
        </section>
      </div>

      <section className="card stack">
        <h3>Members ({data.members.length})</h3>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Access</th><th>Joined</th></tr></thead>
            <tbody>
              {data.members.map((m) => (
                <tr key={m.id}>
                  <td>{m.name}{m.isMe ? ' (you)' : ''}</td>
                  <td>{m.email}</td>
                  <td>{m.role === 'COMPANY_ADMIN' ? 'Company admin' : 'Recruiter'}</td>
                  <td><span className={`badge ${m.status === 'ACTIVE' ? 's-selected' : 's-rejected'}`}>{prettyStatus(m.status)}</span></td>
                  <td className="small">{m.role === 'COMPANY_ADMIN' ? 'all permissions' : m.permissions.length ? `${m.permissions.length} of 8` : 'none'}</td>
                  <td className="small">{fmtDate(m.joinedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {can(user, 'VIEW_ANALYTICS') && (
        <section className="card stack">
          <h3>Company analytics</h3>
          {stats.loading ? <Spinner /> : stats.error ? <Alert>{stats.error}</Alert> : stats.data && (<>
            <div className="grid four">
              <div className="card stat"><span>Jobs</span><b>{stats.data.totalJobs} ({stats.data.activeJobs} active)</b></div>
              <div className="card stat"><span>Applications</span><b>{stats.data.totalApplications}</b></div>
              <div className="card stat"><span>Hires</span><b>{stats.data.hires}</b></div>
              <div className="card stat"><span>Members</span><b>{stats.data.members}</b></div>
            </div>
            {stats.data.perJob.length === 0
              ? <p className="muted small">No jobs posted under this company yet.</p>
              : (
                <div className="table-wrap">
                  <table>
                    <thead><tr><th>Job</th><th>State</th><th>Applicants</th><th>Interview</th><th>Selected</th></tr></thead>
                    <tbody>
                      {stats.data.perJob.map((j) => (
                        <tr key={j.jobId}>
                          <td>{can(user, 'VIEW_APPLICANTS')
                            ? <Link to={`/jobs/${j.jobId}/applicants`}>{j.title}</Link>
                            : j.title}</td>
                          <td>{j.active ? <span className="badge s-selected">active</span> : <span className="badge s-withdrawn">inactive</span>}</td>
                          <td>{j.applicants}</td>
                          <td>{j.byStatus.INTERVIEW + j.byStatus.SELECTED}</td>
                          <td>{j.byStatus.SELECTED}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
          </>)}
        </section>
      )}
    </div>
  );
}

/* ---------------- invitations & membership (/recruiters) ---------------- */

export function Recruiters() {
  const { user } = useAuth();
  const { data, error, loading, reload } = useLoad(() => api('/api/companies/mine'));
  const active = user && user.companyStatus === 'ACTIVE' && can(user, 'MANAGE_RECRUITERS');
  const invites = useLoad(() => (active ? api('/api/companies/mine/invitations') : Promise.resolve([])), [active]);
  const [form, setForm] = useState({ email: '', role: 'RECRUITER' });
  const [perms, setPerms] = useState(ALL_PERMS);
  const [msg, setMsg] = useState({ kind: 'error', text: '' });
  const [copied, setCopied] = useState(null);
  const [editing, setEditing] = useState(null);
  const [editPerms, setEditPerms] = useState([]);

  if (loading) return <Spinner />;
  if (error) return <Alert>{error}</Alert>;
  if (!user || !user.companyId) return <Alert>You don't belong to a company yet. Your company admin can invite you.</Alert>;

  const togglePerm = (list, setList, p) => setList(list.includes(p) ? list.filter((x) => x !== p) : [...list, p]);

  const send = async (e) => {
    e.preventDefault();
    setMsg({ kind: 'error', text: '' });
    try {
      await api('/api/companies/mine/invitations', {
        method: 'POST',
        body: { email: form.email, role: form.role, permissions: form.role === 'COMPANY_ADMIN' ? [] : perms },
      });
      setMsg({ kind: 'success', text: `Invitation created for ${form.email}. Copy the link below and share it.` });
      setForm({ email: '', role: 'RECRUITER' });
      invites.reload();
    } catch (err) { setMsg({ kind: 'error', text: err.message }); }
  };

  const inviteLink = (t) => `${window.location.origin}/invite/${t}`;
  const copy = async (t) => {
    const link = inviteLink(t);
    try {
      await navigator.clipboard.writeText(link);
      setCopied(t);
      setTimeout(() => setCopied(null), 1600);
    } catch {
      window.prompt('Copy this invitation link:', link);
    }
  };
  const revoke = async (inv) => {
    if (!window.confirm(`Revoke the invitation for ${inv.email}?`)) return;
    try { await api(`/api/companies/mine/invitations/${inv.id}`, { method: 'DELETE' }); invites.reload(); }
    catch (err) { setMsg({ kind: 'error', text: err.message }); }
  };
  const saveMember = async (m) => {
    try {
      await api(`/api/companies/mine/members/${m.id}`, { method: 'PATCH', body: { permissions: editPerms } });
      setEditing(null);
      reload();
    } catch (err) { setMsg({ kind: 'error', text: err.message }); }
  };
  const toggleMember = async (m) => {
    try {
      await api(`/api/companies/mine/members/${m.id}`, { method: 'PATCH', body: { status: m.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' } });
      reload();
    } catch (err) { setMsg({ kind: 'error', text: err.message }); }
  };

  return (
    <div className="stack-lg">
      <h1>Recruiters</h1>
      {user.companyStatus !== 'ACTIVE' && (
        <div className="alert warn">
          <b>Your company is {prettyStatus(user.companyStatus)}.</b> Invitations become available once the company
          is verified.
        </div>
      )}
      <Alert kind={msg.kind}>{msg.text}</Alert>

      {active && (
        <form className="card stack" onSubmit={send}>
          <h3>Invite someone to your company</h3>
          <div className="grid two">
            <label>Email<input type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="person@yourcompany.com" /></label>
            <label>Role
              <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
                <option value="RECRUITER">Recruiter</option>
                <option value="COMPANY_ADMIN">Company admin</option>
              </select>
            </label>
          </div>
          {form.role === 'RECRUITER' && (
            <div className="stack" style={{ gap: '.4rem' }}>
              <h4>Permissions (they can be changed later)</h4>
              <div className="perm-grid">
                {PERMS.map(([key, label]) => (
                  <label className="check" key={key}>
                    <input type="checkbox" checked={perms.includes(key)} onChange={() => togglePerm(perms, setPerms, key)} />
                    {label}
                  </label>
                ))}
              </div>
            </div>
          )}
          {form.role === 'COMPANY_ADMIN' && <p className="muted small">Company admins receive every permission automatically.</p>}
          <button className="btn primary" disabled={form.role === 'RECRUITER' && perms.length === 0}>Create invitation</button>
          <p className="muted small">No password is set for them — the invitee chooses their own when they open the link (valid for 7 days).</p>
        </form>
      )}

      {active && (
        <section className="card stack">
          <h3>Invitations</h3>
          {invites.loading ? <Spinner /> : invites.error ? <Alert>{invites.error}</Alert> :
            (invites.data || []).length === 0 ? <p className="muted small">No invitations yet.</p> : (
              <div className="stack">
                {invites.data.map((inv) => (
                  <div key={inv.id} className="row between top wrap invite-row">
                    <div>
                      <p className="small"><b>{inv.email}</b> · {inv.role === 'COMPANY_ADMIN' ? 'company admin' : 'recruiter'}</p>
                      <p className="small muted">
                        <span className={`badge ${inv.status === 'ACCEPTED' ? 's-selected' : inv.status === 'PENDING' ? 's-screening' : 's-withdrawn'}`}>{(inv.status || '').toLowerCase()}</span>
                        {' '}{inv.status === 'PENDING' ? `expires ${fmtDate(inv.expiresAt)}` : inv.acceptedAt ? `accepted ${fmtDate(inv.acceptedAt)}` : ''}
                      </p>
                    </div>
                    <div className="row gap wrap">
                      {inv.status === 'PENDING' && (<>
                        <button className="btn" onClick={() => copy(inv.token)}>{copied === inv.token ? 'Copied ✓' : 'Copy link'}</button>
                        <button className="btn ghost danger" onClick={() => revoke(inv)}>Revoke</button>
                      </>)}
                    </div>
                  </div>
                ))}
              </div>
            )}
        </section>
      )}

      <section className="card stack">
        <h3>Members ({data.members.length})</h3>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Permissions</th><th /></tr></thead>
            <tbody>
              {data.members.map((m) => (
                <tr key={m.id}>
                  <td>{m.name}{m.isMe ? ' (you)' : ''}</td>
                  <td>{m.email}</td>
                  <td>{m.role === 'COMPANY_ADMIN' ? 'Company admin' : 'Recruiter'}</td>
                  <td><span className={`badge ${m.status === 'ACTIVE' ? 's-selected' : 's-rejected'}`}>{prettyStatus(m.status)}</span></td>
                  <td className="small">
                    {editing === m.id ? (
                      <div className="perm-grid">
                        {PERMS.map(([key, label]) => (
                          <label className="check" key={key}>
                            <input type="checkbox" checked={editPerms.includes(key)} onChange={() => togglePerm(editPerms, setEditPerms, key)} />
                            {label}
                          </label>
                        ))}
                      </div>
                    ) : m.role === 'COMPANY_ADMIN'
                      ? 'all permissions'
                      : (m.permissions.length ? m.permissions.map(permLabel).join(', ') : 'none')}
                  </td>
                  <td className="row gap">
                    {editing === m.id ? (<>
                      <button className="btn primary" onClick={() => saveMember(m)}>Save</button>
                      <button className="btn ghost" onClick={() => setEditing(null)}>Cancel</button>
                    </>) : (<>
                      {!m.isMe && m.role !== 'COMPANY_ADMIN' && (
                        <button className="btn ghost" onClick={() => { setEditing(m.id); setEditPerms(m.permissions); }}>Permissions</button>
                      )}
                      {!m.isMe && (
                        <button className="btn ghost" onClick={() => toggleMember(m)}>{m.status === 'ACTIVE' ? 'Suspend' : 'Activate'}</button>
                      )}
                    </>)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
