import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { Alert, ScoreRing, Spinner, fmtDate, useLoad } from '../components.jsx';

const TYPES = ['GITHUB', 'LEETCODE', 'HACKERRANK', 'KAGGLE', 'LINKEDIN', 'CERTIFICATE', 'PORTFOLIO', 'OTHER'];
const ROLES = ['Backend Developer', 'Full Stack Developer', 'Frontend Developer', 'Data Analyst', 'Data Scientist',
  'Machine Learning Engineer', 'DevOps Engineer', 'QA / Test Automation Engineer', 'Android Developer', 'Cloud Engineer'];

/* ---------------- Evidence & verification ---------------- */
export function Evidence() {
  const { data, error, loading, reload } = useLoad(() => api('/api/candidate/evidence'));
  const [form, setForm] = useState({ type: 'GITHUB', title: '', url: '', skill: '' });
  const [msg, setMsg] = useState({ kind: 'error', text: '' });
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const add = async (e) => {
    e.preventDefault();
    setBusy(true); setMsg({ kind: 'error', text: '' });
    try {
      const ev = await api('/api/candidate/evidence', { method: 'POST', body: form });
      setMsg({ kind: ev.status === 'VERIFIED' ? 'success' : 'error', text: ev.status === 'VERIFIED' ? 'Verified automatically ✔' : ev.status === 'REJECTED' ? `Could not verify: ${ev.metadata}` : 'Added. An admin will review it.' });
      setForm({ ...form, title: '', url: '', skill: '' });
      reload();
    } catch (err) { setMsg({ kind: 'error', text: err.message }); }
    finally { setBusy(false); }
  };
  const remove = async (id) => {
    try { await api(`/api/candidate/evidence/${id}`, { method: 'DELETE' }); reload(); } catch (err) { setMsg({ kind: 'error', text: err.message }); }
  };

  return (
    <div className="grid two-wide">
      <section className="stack">
        <h1>Evidence & verification</h1>
        <p className="muted">Back your skills with links. GitHub and LeetCode profiles are checked through their public APIs; LinkedIn, Kaggle and HackerRank links through a reachability check; certificates and other links are always reviewed by an admin. Recruiters see verified evidence labelled separately from self-declared skills.</p>
        <Alert>{error}</Alert>
        {loading ? <Spinner /> : data.items.length === 0 ? <p className="muted">No evidence yet.</p> : data.items.map((e) => (
          <div key={e.id} className="card">
            <div className="row between top">
              <div>
                <h3>{e.title} <span className="badge">{e.type}</span></h3>
                <a className="small" href={e.url} target="_blank" rel="noreferrer">{e.url}</a>
                {e.skill && <p className="small muted">Supports: {e.skill}</p>}
                {e.metadata && <p className="small muted">{e.metadata}</p>}
                {e.verifiedBy && <p className="small muted">Checked by: {e.verifiedBy} {e.checkedAt && `· ${fmtDate(e.checkedAt)}`}</p>}
              </div>
              <div className="row gap">
                <span className={`badge ${e.status === 'VERIFIED' ? 's-selected' : e.status === 'REJECTED' ? 's-rejected' : 's-screening'}`}>{e.status}</span>
                <button className="btn ghost danger" onClick={() => remove(e.id)}>Remove</button>
              </div>
            </div>
          </div>
        ))}
      </section>
      <aside className="stack">
        <section className="card stack">
          <h3>Evidence score</h3>
          {data && <div className="row gap"><ScoreRing value={data.evidenceScore} size={80} /><p className="muted small grow">Summarises how much of your profile is backed by evidence. It is not a measure of your worth.</p></div>}
        </section>
        <form className="card stack" onSubmit={add}>
          <h3>Add evidence</h3>
          <Alert kind={msg.kind}>{msg.text}</Alert>
          <label>Type<select value={form.type} onChange={set('type')}>{TYPES.map((t) => <option key={t}>{t}</option>)}</select></label>
          <label>Title<input required value={form.title} onChange={set('title')} placeholder="e.g. My GitHub profile" /></label>
          <label>Link (https)<input required value={form.url} onChange={set('url')} placeholder="https://github.com/your-username" /></label>
          <label>Skill it supports (optional)<input value={form.skill} onChange={set('skill')} placeholder="Java" /></label>
          <button className="btn primary" disabled={busy}>{busy ? 'Checking…' : 'Add'}</button>
        </form>
      </aside>
    </div>
  );
}

/* ---------------- Resume versions ---------------- */
export function Resumes() {
  const { data, error, loading, reload } = useLoad(() => api('/api/candidate/resume-versions'));
  const [role, setRole] = useState(ROLES[0]);
  const [open, setOpen] = useState(null);
  const [msg, setMsg] = useState('');

  const create = async () => {
    setMsg('');
    try { await api('/api/candidate/resume-versions/role', { method: 'POST', body: { role } }); reload(); } catch (e) { setMsg(e.message); }
  };
  const remove = async (id) => {
    try { await api(`/api/candidate/resume-versions/${id}`, { method: 'DELETE' }); reload(); } catch (e) { setMsg(e.message); }
  };
  const download = (v) => {
    const blob = new Blob([v.content], { type: 'text/plain' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `${v.title.replace(/[^a-z0-9]+/gi, '_')}.txt`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  return (
    <div className="stack-lg">
      <div>
        <h1>My resume versions</h1>
        <p className="muted">Tailored versions generated from your profile. They only re-order and emphasise what you already have — nothing is invented. Job-specific versions are created from a job page.</p>
      </div>
      <div className="card filters">
        <select value={role} onChange={(e) => setRole(e.target.value)}>{ROLES.map((r) => <option key={r}>{r}</option>)}</select>
        <button className="btn primary" onClick={create}>Create trajectory resume</button>
      </div>
      <Alert>{msg || error}</Alert>
      {loading ? <Spinner /> : data.length === 0 ? <p className="muted">No versions yet. Create one above or optimise your resume on a <Link to="/jobs">job page</Link>.</p> : (
        <div className="stack">
          {data.map((v) => (
            <div key={v.id} className="card">
              <div className="row between top">
                <div>
                  <h3>{v.title}</h3>
                  <p className="muted small">{v.jobId ? `Job-specific (job #${v.jobId})` : `Trajectory: ${v.targetRole}`} · {fmtDate(v.createdAt)}</p>
                </div>
                <div className="row gap wrap">
                  <button className="btn ghost" onClick={() => setOpen(open === v.id ? null : v.id)}>{open === v.id ? 'Hide' : 'View'}</button>
                  <button className="btn ghost" onClick={() => download(v)}>Download</button>
                  <button className="btn ghost danger" onClick={() => remove(v.id)}>Delete</button>
                </div>
              </div>
              {open === v.id && <pre className="resume">{v.content}</pre>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ---------------- Auto-apply ---------------- */
export function AutoApply() {
  const { data, error, loading } = useLoad(() => api('/api/candidate/auto-apply'));
  const [form, setForm] = useState(null);
  const [msg, setMsg] = useState({ kind: 'error', text: '' });
  const [busy, setBusy] = useState(false);
  if (data && !form) setForm({ ...data, enabled: !!data.enabled, locations: data.locations || '', jobTypes: data.jobTypes || '', roleKeywords: data.roleKeywords || '' });
  if (loading || !form) return error ? <Alert>{error}</Alert> : <Spinner />;
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const save = async (e) => {
    e.preventDefault();
    setBusy(true); setMsg({ kind: 'error', text: '' });
    try {
      await api('/api/candidate/auto-apply', { method: 'PUT', body: { ...form, minScore: Number(form.minScore), maxPerDay: Number(form.maxPerDay) } });
      setMsg({ kind: 'success', text: 'Rule saved.' });
    } catch (err) { setMsg({ kind: 'error', text: err.message }); } finally { setBusy(false); }
  };
  const run = async () => {
    setBusy(true); setMsg({ kind: 'error', text: '' });
    try {
      const r = await api('/api/candidate/auto-apply/run', { method: 'POST' });
      setMsg({ kind: 'success', text: r.count === 0 ? 'No new eligible jobs right now.' : `Applied to ${r.count} job(s): ${r.applied.join(', ')}` });
    } catch (err) { setMsg({ kind: 'error', text: err.message }); } finally { setBusy(false); }
  };

  return (
    <form className="card stack narrow" onSubmit={save}>
      <h1>Auto-apply</h1>
      <p className="muted">When on, we apply for you to jobs posted on SNAP-HIRE that meet your rules — each with a job-specific optimised resume and a draft cover letter. We never auto-submit to external websites.</p>
      <Alert kind={msg.kind}>{msg.text}</Alert>
      <label className="check"><input type="checkbox" checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} /> Enable auto-apply</label>
      <label>Minimum compatibility: <b>{form.minScore}%</b><input type="range" min="30" max="100" value={form.minScore} onChange={set('minScore')} /></label>
      <label>Maximum applications per day<input type="number" min="1" max="10" value={form.maxPerDay} onChange={set('maxPerDay')} /></label>
      <label>Locations (comma separated, remote jobs always qualify)<input value={form.locations} onChange={set('locations')} placeholder="Bengaluru, Delhi" /></label>
      <label>Job types (comma separated)<input value={form.jobTypes} onChange={set('jobTypes')} placeholder="Full-time, Internship" /></label>
      <label>Title must contain one of (comma separated)<input value={form.roleKeywords} onChange={set('roleKeywords')} placeholder="java, backend" /></label>
      <div className="row gap wrap">
        <button className="btn primary" disabled={busy}>Save rule</button>
        <button type="button" className="btn" onClick={run} disabled={busy}>Run now</button>
        <Link className="btn ghost" to="/applications">See my applications</Link>
      </div>
      <p className="muted small">Safeguards: duplicate protection, daily limit, minimum score, every action is logged and you get a notification for each application. You can withdraw any application.</p>
    </form>
  );
}
