import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { Alert, Bar, Chips, STATUSES, ScoreRing, Spinner, StatusBadge, useLoad } from '../components.jsx';

export function RecruiterHome() {
  const { data, error, loading } = useLoad(() => api('/api/recruiter/stats'));
  if (loading) return <Spinner />;
  if (error) return <Alert>{error}</Alert>;
  const max = Math.max(1, ...Object.values(data.byStatus));
  return (
    <div className="stack-lg">
      <h1>Recruiter dashboard</h1>
      <div className="grid three">
        <div className="card stat"><span>Active jobs</span><b>{data.activeJobs}</b></div>
        <div className="card stat"><span>Total jobs</span><b>{data.totalJobs}</b></div>
        <div className="card stat"><span>Applications</span><b>{data.totalApplications}</b></div>
      </div>
      <div className="grid two">
        <section className="card stack">
          <h3>Applications by status</h3>
          {Object.entries(data.byStatus).map(([k, v]) => <Bar key={k} label={k} value={Math.round((v / max) * 100)} />)}
        </section>
        <section className="card stack">
          <h3>Top candidates by compatibility</h3>
          {data.topCandidates.length === 0 ? <p className="muted">No applicants yet.</p> : data.topCandidates.map((t) => (
            <Link key={t.applicationId} to={`/jobs/${t.jobId}/applicants`} className="row between">
              <span>{t.candidate} <span className="muted small">for {t.jobTitle}</span></span>
              <b>{t.score}%</b>
            </Link>
          ))}
        </section>
      </div>
      <section className="card stack">
        <h3>Applicants per job</h3>
        {data.perJob.length === 0 ? <p className="muted">You haven't posted any jobs yet. <Link to="/jobs/new">Post one</Link></p> : data.perJob.map((j) => (
          <Link key={j.jobId} to={`/jobs/${j.jobId}/applicants`} className="row between">
            <span>{j.title} {!j.active && <span className="badge s-withdrawn">inactive</span>}</span><b>{j.applicants}</b>
          </Link>
        ))}
      </section>
    </div>
  );
}

export function RecruiterJobs() {
  const { data, error, loading, reload } = useLoad(() => api('/api/jobs/mine'));
  const [err, setErr] = useState('');
  const toggle = async (j) => {
    try {
      await api(`/api/jobs/${j.id}`, { method: 'PUT', body: { ...j, active: !j.active } });
      reload();
    } catch (e) { setErr(e.message); }
  };
  const remove = async (j) => {
    if (!window.confirm(`Delete "${j.title}"? Applications will be marked withdrawn.`)) return;
    try { await api(`/api/jobs/${j.id}`, { method: 'DELETE' }); reload(); } catch (e) { setErr(e.message); }
  };
  return (
    <div className="stack-lg">
      <div className="row between"><h1>My jobs</h1><Link className="btn primary" to="/jobs/new">+ Post a job</Link></div>
      <Alert>{err || error}</Alert>
      {loading ? <Spinner /> : data && data.length === 0 ? <p className="muted">No jobs yet.</p> : (
        <div className="stack">
          {(data || []).map(({ job: j, applicantCount }) => (
            <div key={j.id} className="card">
              <div className="row between top">
                <div>
                  <h3>{j.title} {!j.active && <span className="badge s-withdrawn">inactive</span>}</h3>
                  <p className="muted small">{j.location || 'Anywhere'} · {j.employmentType} · {applicantCount} applicant(s)</p>
                  <Chips items={j.requiredSkills} />
                </div>
                <div className="row gap wrap">
                  <Link className="btn primary" to={`/jobs/${j.id}/applicants`}>Applicants</Link>
                  <Link className="btn" to={`/jobs/${j.id}/edit`}>Edit</Link>
                  <button className="btn ghost" onClick={() => toggle(j)}>{j.active ? 'Unpublish' : 'Publish'}</button>
                  <button className="btn ghost danger" onClick={() => remove(j)}>Delete</button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

const EMPTY = { title: '', company: '', description: '', requiredSkills: '', location: '', employmentType: 'Full-time', minExperience: 0, externalUrl: '', active: true };

export function JobForm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!id) { setForm(EMPTY); return; }
    api(`/api/jobs/${id}`).then(({ job }) => setForm({ ...EMPTY, ...job, externalUrl: job.externalUrl || '', location: job.location || '' })).catch((e) => setError(e.message));
  }, [id]);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      const body = { ...form, minExperience: Number(form.minExperience) || 0 };
      await api(id ? `/api/jobs/${id}` : '/api/jobs', { method: id ? 'PUT' : 'POST', body });
      navigate('/jobs');
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };

  return (
    <form className="card stack narrow" onSubmit={submit}>
      <h1>{id ? 'Edit job' : 'Post a job'}</h1>
      <Alert>{error}</Alert>
      <label>Job title<input required value={form.title} onChange={set('title')} /></label>
      <label>Company (defaults to your company)<input value={form.company || ''} onChange={set('company')} /></label>
      <label>Description<textarea required rows={7} value={form.description} onChange={set('description')} /></label>
      <label>Required skills (comma separated)<input value={form.requiredSkills || ''} onChange={set('requiredSkills')} placeholder="Java, Spring Boot, PostgreSQL" /></label>
      <Chips items={form.requiredSkills} />
      <div className="grid two">
        <label>Location<input value={form.location} onChange={set('location')} /></label>
        <label>Type
          <select value={form.employmentType} onChange={set('employmentType')}>
            <option>Full-time</option><option>Part-time</option><option>Internship</option><option>Contract</option>
          </select>
        </label>
        <label>Minimum experience (years)<input type="number" min="0" step="0.5" value={form.minExperience} onChange={set('minExperience')} /></label>
        <label>External apply link (optional)<input value={form.externalUrl} onChange={set('externalUrl')} /></label>
      </div>
      <label className="check"><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Published (visible to candidates)</label>
      <button className="btn primary" disabled={busy}>{busy ? 'Saving…' : id ? 'Save changes' : 'Publish job'}</button>
    </form>
  );
}

export function Applicants() {
  const { id } = useParams();
  const job = useLoad(() => api(`/api/jobs/${id}`), [id]);
  const { data, error, loading, reload } = useLoad(() => api(`/api/applications/job/${id}`), [id]);
  const [f, setF] = useState({ status: '', minScore: 0, skill: '' });
  const [open, setOpen] = useState(null);
  const [err, setErr] = useState('');

  const setStatus = async (appId, status) => {
    try { await api(`/api/applications/${appId}/status`, { method: 'PATCH', body: { status } }); reload(); }
    catch (e) { setErr(e.message); }
  };

  const rows = (data || []).filter((r) =>
    (!f.status || r.application.status === f.status) &&
    r.match.score >= Number(f.minScore || 0) &&
    (!f.skill || (r.profile.skills || '').toLowerCase().includes(f.skill.toLowerCase())));

  return (
    <div className="stack-lg">
      <Link to="/jobs" className="small">← My jobs</Link>
      <h1>Applicants{job.data ? ` · ${job.data.job.title}` : ''}</h1>
      <p className="muted">Ranked by job-specific compatibility. Scores explain themselves — final decisions stay with you.</p>
      <div className="card filters">
        <select value={f.status} onChange={(e) => setF({ ...f, status: e.target.value })}>
          <option value="">All statuses</option>{STATUSES.map((s) => <option key={s}>{s}</option>)}
        </select>
        <label className="inline">Min score {f.minScore}%<input type="range" min="0" max="100" value={f.minScore} onChange={(e) => setF({ ...f, minScore: e.target.value })} /></label>
        <input placeholder="Has skill…" value={f.skill} onChange={(e) => setF({ ...f, skill: e.target.value })} />
      </div>
      <Alert>{err || error}</Alert>
      {loading ? <Spinner /> : rows.length === 0 ? <p className="muted">No applicants match.</p> : (
        <div className="stack">
          {rows.map((r, i) => (
            <div key={r.application.id} className="card">
              <div className="row between top">
                <div className="row gap">
                  <span className="rank">#{i + 1}</span>
                  <ScoreRing value={r.match.score} />
                  <div>
                    <h3>{r.candidateName}</h3>
                    <p className="muted small">{r.candidateEmail} · {r.profile.experienceYears ?? 0} yrs · {r.profile.location || 'Location n/a'}</p>
                  </div>
                </div>
                <div className="row gap wrap">
                  <StatusBadge status={r.application.status} />
                  <select value={r.application.status} disabled={r.application.status === 'WITHDRAWN'} onChange={(e) => setStatus(r.application.id, e.target.value)}>
                    {STATUSES.filter((s) => s !== 'WITHDRAWN' && s !== 'APPLIED').concat(r.application.status === 'APPLIED' ? ['APPLIED'] : [], r.application.status === 'WITHDRAWN' ? ['WITHDRAWN'] : [])
                      .map((s) => <option key={s}>{s}</option>)}
                  </select>
                  <button className="btn ghost" onClick={() => setOpen(open === r.application.id ? null : r.application.id)}>{open === r.application.id ? 'Hide' : 'Details'}</button>
                </div>
              </div>
              <div className="split">
                <div><h4>Matched</h4><Chips items={r.match.matched} tone="good" /></div>
                <div><h4>Missing</h4><Chips items={r.match.missing} tone="bad" /></div>
              </div>
              {open === r.application.id && (
                <div className="stack detail">
                  {Object.entries(r.match.breakdown).map(([k, v]) => <Bar key={k} label={k} value={v} />)}
                  <h4>Candidate-declared skills <span className="badge s-withdrawn">self-declared</span></h4>
                  <Chips items={r.profile.skills} />
                  {r.profile.headline && <><h4>Headline</h4><p>{r.profile.headline}</p></>}
                  {r.profile.summary && <><h4>Summary</h4><p className="pre">{r.profile.summary}</p></>}
                  {r.profile.education && <><h4>Education</h4><p className="pre">{r.profile.education}</p></>}
                  {r.profile.experience && <><h4>Experience</h4><p className="pre">{r.profile.experience}</p></>}
                  {r.profile.projects && <><h4>Projects</h4><p className="pre">{r.profile.projects}</p></>}
                  {r.application.coverLetter && <><h4>Cover letter</h4><p className="pre">{r.application.coverLetter}</p></>}
                  {r.profile.resumeFileName && <p className="muted small">Resume imported: {r.profile.resumeFileName}</p>}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export function RecruiterCompany() {
  const { data, error, loading } = useLoad(() => api('/api/recruiter/profile'));
  const [form, setForm] = useState(null);
  const [msg, setMsg] = useState({ kind: 'error', text: '' });
  useEffect(() => { if (data) setForm(data); }, [data]);
  if (loading || !form) return error ? <Alert>{error}</Alert> : <Spinner />;
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const save = async (e) => {
    e.preventDefault();
    try { setForm(await api('/api/recruiter/profile', { method: 'PUT', body: form })); setMsg({ kind: 'success', text: 'Saved.' }); }
    catch (err) { setMsg({ kind: 'error', text: err.message }); }
  };
  return (
    <form className="card stack narrow" onSubmit={save}>
      <h1>Company profile</h1>
      <Alert kind={msg.kind}>{msg.text}</Alert>
      <label>Company name<input required value={form.companyName || ''} onChange={set('companyName')} /></label>
      <label>Your designation<input value={form.designation || ''} onChange={set('designation')} /></label>
      <label>Website<input value={form.website || ''} onChange={set('website')} /></label>
      <label>Location<input value={form.location || ''} onChange={set('location')} /></label>
      <button className="btn primary">Save</button>
    </form>
  );
}
