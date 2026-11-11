import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import { Alert, Bar, Chips, ScoreRing, Spinner, StatusBadge, fmtDate, useLoad } from '../components.jsx';

/* ---------------- Dashboard ---------------- */
export function CandidateHome() {
  const score = useLoad(() => api('/api/candidate/score'));
  const recs = useLoad(() => api('/api/candidate/recommendations'));
  const apps = useLoad(() => api('/api/applications/mine'));
  const ev = useLoad(() => api('/api/candidate/evidence'));

  return (
    <div className="stack-lg">
      <h1>Your dashboard</h1>
      <div className="grid two">
        <section className="card">
          <h3>Profile health</h3>
          {score.loading ? <Spinner /> : score.error ? <Alert>{score.error}</Alert> : (
            <div className="row gap">
              <ScoreRing value={score.data.score} size={96} />
              <div className="grow">
                {Object.entries(score.data.categories).map(([k, v]) => (
                  <Bar key={k} label={k} value={Math.round((v / score.data.maxPoints[k]) * 100)} />
                ))}
              </div>
            </div>
          )}
          {score.data && score.data.suggestions.length > 0 && (
            <>
              <h4>How to improve</h4>
              <ul className="tips">{score.data.suggestions.slice(0, 4).map((s) => <li key={s}>{s}</li>)}</ul>
              <Link className="btn" to="/profile">Edit profile</Link>
            </>
          )}
        </section>
        <section className="card">
          <h3>Application pipeline</h3>
          {apps.loading ? <Spinner /> : apps.error ? <Alert>{apps.error}</Alert> : apps.data.length === 0 ? (
            <p className="muted">You haven't applied yet. Pick a job below.</p>
          ) : (
            <ul className="plain">
              {apps.data.slice(0, 5).map((a) => (
                <li key={a.application.id} className="row between">
                  <span>{a.jobTitle} <span className="muted small">· {a.company}</span></span>
                  <StatusBadge status={a.application.status} />
                </li>
              ))}
            </ul>
          )}
          <Link className="btn" to="/applications">All applications</Link>
        </section>
      </div>

      {ev.data && (
        <section className="card row between">
          <div>
            <h3>Evidence strength</h3>
            <p className="muted small">{ev.data.items.filter((e) => e.status === 'VERIFIED').length} verified · {ev.data.items.filter((e) => e.status === 'PENDING').length} pending. Verified evidence boosts your rank in recruiter views.</p>
          </div>
          <div className="row gap"><ScoreRing value={ev.data.evidenceScore} size={56} /><Link className="btn" to="/evidence">Manage evidence</Link></div>
        </section>
      )}

      <section>
        <div className="row between"><h2>Recommended jobs</h2><Link to="/jobs">Browse all →</Link></div>
        {recs.loading ? <Spinner /> : recs.error ? <Alert>{recs.error}</Alert> : recs.data.length === 0 ? (
          <p className="muted">No jobs yet.</p>
        ) : (
          <div className="grid three">{recs.data.slice(0, 6).map((v) => <JobCard key={v.job.id} view={v} />)}</div>
        )}
      </section>
    </div>
  );
}

function JobCard({ view }) {
  const { job, match, applied } = view;
  return (
    <Link to={`/jobs/${job.id}`} className="card job-card">
      <div className="row between top">
        <div>
          <h3>{job.title}</h3>
          <p className="muted small">{job.company} · {job.location || 'Anywhere'} · {job.employmentType}</p>
        </div>
        {match && <ScoreRing value={match.score} />}
      </div>
      <Chips items={job.requiredSkills} />
      {applied && <span className="badge s-applied">Applied</span>}
    </Link>
  );
}

/* ---------------- Jobs list ---------------- */
export function Jobs() {
  const [params, setParams] = useSearchParams();
  const role = params.get('role') || '';
  const [f, setF] = useState({ q: '', location: '', type: '', source: '' });
  const [applied, setApplied] = useState(f);
  const qs = new URLSearchParams(Object.entries({ ...applied, role }).filter(([, v]) => v)).toString();
  const { data, error, loading } = useLoad(() => api(`/api/jobs${qs ? `?${qs}` : ''}`), [qs]);
  const allZero = data && data.length > 0 && data.every((v) => v.match && v.match.score === 0);

  return (
    <div className="stack-lg">
      <h1>Find jobs</h1>
      {role && (
        <div className="alert success row between">
          <span>Showing open roles for <b>{role}</b> ({data ? data.length : '…'})</span>
          <button className="btn ghost" onClick={() => setParams({})}>Clear</button>
        </div>
      )}
      {allZero && <Alert kind="error">Your compatibility is 0% because your profile is empty. <Link to="/profile">Add your skills and projects</Link> to see real matches.</Alert>}
      <form className="card filters" onSubmit={(e) => { e.preventDefault(); setApplied(f); }}>
        <input placeholder="Search title, company, keywords" value={f.q} onChange={(e) => setF({ ...f, q: e.target.value })} />
        <input placeholder="Location" value={f.location} onChange={(e) => setF({ ...f, location: e.target.value })} />
        <select value={f.type} onChange={(e) => setF({ ...f, type: e.target.value })}>
          <option value="">Any type</option>
          <option>Full-time</option><option>Part-time</option><option>Internship</option><option>Contract</option>
        </select>
        <select value={f.source} onChange={(e) => setF({ ...f, source: e.target.value })}>
          <option value="">All sources</option><option value="INTERNAL">Posted on SNAP-HIRE</option><option value="EXTERNAL">External listings</option>
        </select>
        <button className="btn primary">Search</button>
      </form>
      {loading ? <Spinner /> : error ? <Alert>{error}</Alert> : data.length === 0 ? <p className="muted">No jobs match your search.</p> : (
        <div className="grid three">{data.map((v) => <JobCard key={v.job.id} view={v} />)}</div>
      )}
    </div>
  );
}

/* ---------------- Job detail + apply ---------------- */
export function JobDetail() {
  const { id } = useParams();
  const { data, error, loading, reload } = useLoad(() => api(`/api/jobs/${id}`), [id]);
  const impact = useLoad(() => api(`/api/jobs/${id}/impact`), [id]);
  const [letter, setLetter] = useState('');
  const [opt, setOpt] = useState(null);
  const [prep, setPrep] = useState(null);
  const [msg, setMsg] = useState({ kind: 'error', text: '' });
  const [busy, setBusy] = useState(false);

  if (loading) return <Spinner />;
  if (error) return <Alert>{error}</Alert>;
  const { job, match, applied } = data;
  const external = job.source === 'EXTERNAL';

  const fail = (e) => setMsg({ kind: 'error', text: e.message });
  const draft = async () => {
    setMsg({ kind: 'error', text: '' });
    try { setLetter((await api(`/api/jobs/${id}/cover-letter`, { method: 'POST' })).coverLetter); } catch (e) { fail(e); }
  };
  const optimise = async () => {
    setBusy(true); setMsg({ kind: 'error', text: '' });
    try {
      const o = await api(`/api/jobs/${id}/optimize`, { method: 'POST' });
      setOpt(o);
      setLetter(o.coverLetter);
    } catch (e) { fail(e); } finally { setBusy(false); }
  };
  const submit = async () => {
    setBusy(true); setMsg({ kind: 'error', text: '' });
    try {
      await api('/api/applications', { method: 'POST', body: { jobId: job.id, coverLetter: letter, resumeVersionId: opt ? opt.resumeVersionId : null, mode: external ? 'EXTERNAL_ASSISTED' : 'MANUAL' } });
      setMsg({ kind: 'success', text: external ? 'Tracked! Remember to complete your application on the employer\'s site.' : 'Application submitted! Track it under Applications.' });
      reload();
    } catch (e) { fail(e); } finally { setBusy(false); }
  };
  const loadPrep = async () => {
    try { setPrep(await api(`/api/jobs/${id}/interview-prep`)); } catch (e) { fail(e); }
  };
  const copy = (text) => { try { navigator.clipboard.writeText(text); setMsg({ kind: 'success', text: 'Copied to clipboard.' }); } catch { /* ignore */ } };

  return (
    <div className="grid two-wide">
      <div className="stack">
        <section className="card stack">
          <Link to="/jobs" className="small">← Back to jobs</Link>
          <h1>{job.title} {external && <span className="badge s-screening">External listing</span>}</h1>
          <p className="muted">{job.company} · {job.location || 'Anywhere'} · {job.employmentType} · Min. experience {job.minExperience || 0} yrs</p>
          <h4>Required skills</h4>
          <Chips items={job.requiredSkills} />
          <h4>Description</h4>
          <p className="pre">{job.description}</p>
        </section>

        {impact.data && impact.data.length > 0 && (
          <section className="card stack">
            <h3>Close your skill gaps</h3>
            <p className="muted small">Estimated change in your compatibility for this job if you add each skill (only add skills you genuinely learn).</p>
            <table>
              <thead><tr><th>Skill</th><th>Potential gain</th><th>Learn</th></tr></thead>
              <tbody>
                {impact.data.map((i) => (
                  <tr key={i.skill}><td>{i.skill}</td><td><b className="good-text">+{i.gain}%</b></td><td><a href={i.learn} target="_blank" rel="noreferrer">Start learning ↗</a></td></tr>
                ))}
              </tbody>
            </table>
          </section>
        )}

        <section className="card stack">
          <div className="row between">
            <h3>Interview preparation</h3>
            {!prep && <button className="btn" onClick={loadPrep}>Generate questions</button>}
          </div>
          {prep && (
            <>
              {prep.technical.map((t) => (
                <div key={t.skill}>
                  <h4>{t.skill} {t.gap && <span className="badge s-rejected">gap</span>}</h4>
                  <ul className="tips">{t.questions.map((q) => <li key={q}>{q}</li>)}</ul>
                </div>
              ))}
              <h4>Behavioural</h4>
              <ul className="tips">{prep.behavioural.map((q) => <li key={q}>{q}</li>)}</ul>
              {prep.gapTips.length > 0 && <><h4>About your gaps</h4><ul className="tips">{prep.gapTips.map((q) => <li key={q}>{q}</li>)}</ul></>}
            </>
          )}
        </section>
      </div>

      <aside className="stack">
        <section className="card stack">
          <h3>Your compatibility</h3>
          <div className="row gap"><ScoreRing value={match.score} size={88} /><p className="muted small grow">Job-specific score based on your profile. Weights are set by the platform admin.</p></div>
          {Object.entries(match.breakdown).map(([k, v]) => <Bar key={k} label={k} value={v} />)}
          <h4>Matched skills</h4><Chips items={match.matched} tone="good" />
          <h4>Skills to build</h4><Chips items={match.missing} tone="bad" />
          <ul className="tips">{match.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
        </section>

        <section className="card stack">
          <h3>{external ? 'Application package' : 'Apply'}</h3>
          <Alert kind={msg.kind}>{msg.text}</Alert>
          {external && <p className="muted small">This job lives on another site, so we never submit for you. Get your optimised resume and cover letter here, apply on the employer's page, then track it.</p>}
          <button className="btn" type="button" onClick={optimise} disabled={busy}>{opt ? 'Re-optimise resume' : 'Optimise my resume for this job'}</button>
          {opt && (
            <div className="stack">
              <ul className="tips">{opt.suggestions.map((t) => <li key={t}>{t}</li>)}</ul>
              <details><summary>Optimised resume (saved under Resumes)</summary>
                <pre className="resume">{opt.resumeText}</pre>
                <button className="btn ghost" onClick={() => copy(opt.resumeText)}>Copy</button>
              </details>
            </div>
          )}
          {external && <a className="btn" href={job.externalUrl} target="_blank" rel="noreferrer">Open original listing ↗</a>}
          {applied ? <p>✅ {external ? 'You are tracking this application.' : 'You have applied to this job.'} <Link to="/applications">View status</Link></p> : (
            <>
              <label>Cover letter (optional)
                <textarea rows={8} value={letter} onChange={(e) => setLetter(e.target.value)} placeholder="Write your own, or generate a draft from your profile." />
              </label>
              <div className="row gap wrap">
                <button className="btn" type="button" onClick={draft}>Generate draft</button>
                <button className="btn primary" type="button" onClick={submit} disabled={busy}>{busy ? 'Working…' : external ? "I've applied — track it" : 'Submit application'}</button>
              </div>
              <p className="muted small">Drafts only use facts already in your profile. Review before sending.</p>
            </>
          )}
        </section>
      </aside>
    </div>
  );
}

/* ---------------- Applications ---------------- */
export function MyApplications() {
  const { data, error, loading, reload } = useLoad(() => api('/api/applications/mine'));
  const [open, setOpen] = useState(null);
  const [events, setEvents] = useState([]);
  const [err, setErr] = useState('');

  const toggle = async (id) => {
    if (open === id) { setOpen(null); return; }
    try { setEvents(await api(`/api/applications/${id}/events`)); setOpen(id); } catch (e) { setErr(e.message); }
  };
  const withdraw = async (id) => {
    if (!window.confirm('Withdraw this application?')) return;
    try { await api(`/api/applications/${id}/withdraw`, { method: 'POST' }); reload(); } catch (e) { setErr(e.message); }
  };

  return (
    <div className="stack-lg">
      <h1>My applications</h1>
      <Alert>{err || error}</Alert>
      {loading ? <Spinner /> : data && data.length === 0 ? <p className="muted">No applications yet. <Link to="/jobs">Find a job</Link></p> : (
        <div className="stack">
          {(data || []).map(({ application: a, jobTitle, company, jobSource, externalUrl }) => (
            <div key={a.id} className="card">
              <div className="row between top">
                <div>
                  <h3>{jobTitle} {a.mode === 'AUTO' && <span className="badge s-screening">auto-applied</span>} {a.mode === 'EXTERNAL_ASSISTED' && <span className="badge s-withdrawn">applied externally</span>}</h3>
                  <p className="muted small">{company} · applied {fmtDate(a.createdAt)} {jobSource === 'EXTERNAL' && externalUrl && <a href={externalUrl} target="_blank" rel="noreferrer">· original listing ↗</a>}</p>
                </div>
                <div className="row gap"><ScoreRing value={a.score} size={52} /><StatusBadge status={a.status} /></div>
              </div>
              <div className="row gap wrap">
                <button className="btn ghost" onClick={() => toggle(a.id)}>{open === a.id ? 'Hide history' : 'History'}</button>
                {!['WITHDRAWN', 'SELECTED', 'REJECTED'].includes(a.status) && <button className="btn ghost danger" onClick={() => withdraw(a.id)}>Withdraw</button>}
              </div>
              {open === a.id && (
                <ul className="timeline">
                  {events.map((e) => <li key={e.id}><b>{e.eventType}</b> — {e.details} <span className="muted small">{fmtDate(e.createdAt)}</span></li>)}
                </ul>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ---------------- Career trajectory ---------------- */
export function Trajectory() {
  const { data, error, loading } = useLoad(() => api('/api/candidate/trajectories'));
  return (
    <div className="stack-lg">
      <div>
        <h1>Career trajectory</h1>
        <p className="muted">Several possible directions based on your skills, projects and preferred roles. Pick what fits you — these are suggestions, not prescriptions.</p>
      </div>
      {loading ? <Spinner /> : error ? <Alert>{error}</Alert> : (
        <div className="grid two">
          {data.map((t) => (
            <section key={t.role} className="card stack">
              <div className="row between top">
                <h3>{t.role} {t.preferred && <span className="badge s-shortlisted">Your preference</span>}</h3>
                <ScoreRing value={t.fit} />
              </div>
              <h4>You already have</h4><Chips items={t.strengths} tone="good" />
              <h4>Skills to add</h4><Chips items={t.missingSkills} tone="bad" />
              {Object.keys(t.learningLinks).length > 0 && (
                <p className="small">Learn: {Object.entries(t.learningLinks).map(([k, u]) => <a key={k} href={u} target="_blank" rel="noreferrer" className="mr">{k} ↗</a>)}</p>
              )}
              <h4>Suggested next steps</h4>
              <ul className="tips">{t.suggestedProjects.map((p) => <li key={p}>{p}</li>)}</ul>
              {t.openJobCount > 0
                ? <Link className="btn" to={`/jobs?role=${encodeURIComponent(t.role)}`}>View {t.openJobCount} open role{t.openJobCount === 1 ? '' : 's'} →</Link>
                : <p className="muted small">No open roles for this path right now.</p>}
            </section>
          ))}
        </div>
      )}
    </div>
  );
}

/* ---------------- Profile editor + resume import ---------------- */
const TEXT_FIELDS = [
  ['headline', 'Headline', 'input'], ['location', 'Location', 'input'], ['phone', 'Phone', 'input'],
  ['preferredRoles', 'Preferred roles (comma separated)', 'input'],
  ['summary', 'Summary', 'area'], ['education', 'Education', 'area'], ['experience', 'Experience / internships', 'area'],
  ['projects', 'Projects', 'area'], ['certifications', 'Certifications & achievements', 'area'],
];

export function CandidateProfile() {
  const { data, error, loading } = useLoad(() => api('/api/candidate/profile'));
  const [form, setForm] = useState(null);
  const [draft, setDraft] = useState(null);
  const [msg, setMsg] = useState({ kind: 'error', text: '' });
  const [busy, setBusy] = useState(false);
  const [score, setScore] = useState(null);

  useEffect(() => { if (data) setForm({ ...data, experienceYears: data.experienceYears ?? '' }); }, [data]);
  useEffect(() => { api('/api/candidate/score').then(setScore).catch(() => {}); }, []);

  if (loading || !form) return error ? <Alert>{error}</Alert> : <Spinner />;
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const upload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    setMsg({ kind: 'error', text: '' });
    const fd = new FormData();
    fd.append('file', file);
    try {
      const p = await api('/api/candidate/resume/parse', { method: 'POST', form: fd });
      setDraft(p);
      const next = { ...form, resumeText: p.text, resumeFileName: p.fileName, profileSource: 'RESUME_CONFIRMED' };
      // never silently overwrite: only fill blank fields; differing values are offered as suggestions
      ['summary', 'education', 'experience', 'projects', 'certifications', 'phone'].forEach((k) => { if (!next[k] && p[k]) next[k] = p[k]; });
      if ((next.experienceYears === '' || next.experienceYears == null) && p.experienceYears) next.experienceYears = p.experienceYears;
      const merged = new Set([...(next.skills || '').split(',').map((s) => s.trim()).filter(Boolean), ...p.skills]);
      next.skills = [...merged].join(', ');
      setForm(next);
      setMsg({ kind: 'success', text: `Resume read. Review the fields below (${p.skills.length} skills detected), fix anything wrong, then click Save profile.` });
    } catch (err) { setMsg({ kind: 'error', text: err.message }); }
    e.target.value = '';
  };

  const save = async (e) => {
    e.preventDefault();
    setBusy(true); setMsg({ kind: 'error', text: '' });
    try {
      const body = { ...form, experienceYears: form.experienceYears === '' ? null : Number(form.experienceYears) };
      const res = await api('/api/candidate/profile', { method: 'PUT', body });
      setForm({ ...res.profile, experienceYears: res.profile.experienceYears ?? '' });
      setScore(res.score);
      setDraft(null);
      setMsg({ kind: 'success', text: 'Profile saved.' });
    } catch (err) { setMsg({ kind: 'error', text: err.message }); }
    finally { setBusy(false); }
  };

  const suggestion = (k) => (draft && draft[k] && form[k] && draft[k].trim() !== form[k].trim()) ? draft[k] : null;

  return (
    <div className="grid two-wide">
      <form className="card stack" onSubmit={save}>
        <h1>My profile</h1>
        <Alert kind={msg.kind}>{msg.text}</Alert>
        <div className="upload">
          <b>Import from resume</b>
          <p className="muted small">PDF or DOCX, up to 5 MB. We extract skills, education, experience and projects into editable fields. Nothing is saved until you click Save.</p>
          <input type="file" accept=".pdf,.docx" onChange={upload} />
          {form.resumeFileName && <p className="small">Last imported: {form.resumeFileName} {form.profileSource === 'RESUME_CONFIRMED' && <span className="badge s-screening">resume-derived, reviewed by you</span>}</p>}
        </div>

        {TEXT_FIELDS.map(([k, label, type]) => (
          <label key={k}>{label}
            {type === 'input' ? <input value={form[k] || ''} onChange={set(k)} /> : <textarea rows={k === 'summary' ? 3 : 5} value={form[k] || ''} onChange={set(k)} />}
            {suggestion(k) && <button type="button" className="link small" onClick={() => setForm({ ...form, [k]: draft[k] })}>Resume has a different value — use it instead</button>}
          </label>
        ))}
        <label>Years of experience<input type="number" min="0" max="50" step="0.5" value={form.experienceYears} onChange={set('experienceYears')} /></label>
        <label>Skills (comma separated)<textarea rows={3} value={form.skills || ''} onChange={set('skills')} /></label>
        <Chips items={form.skills} />
        <label className="check"><input type="checkbox" checked={form.openToRecruiters !== false} onChange={(e) => setForm({ ...form, openToRecruiters: e.target.checked })} /> Let recruiters discover my profile in candidate search</label>
        <button className="btn primary" disabled={busy}>{busy ? 'Saving…' : 'Save profile'}</button>
      </form>

      <aside className="card stack">
        <h3>Profile score</h3>
        {score ? (
          <>
            <div className="row gap"><ScoreRing value={score.score} size={88} /><p className="muted small grow">Project-defined quality score. It is not an official ATS score.</p></div>
            {Object.entries(score.categories).map(([k, v]) => <Bar key={k} label={k} value={Math.round((v / score.maxPoints[k]) * 100)} />)}
            <ul className="tips">{score.suggestions.map((s) => <li key={s}>{s}</li>)}</ul>
          </>
        ) : <Spinner />}
        <p className="muted small">Skills you list are <b>self-declared</b>; recruiters see them labelled as such.</p>
      </aside>
    </div>
  );
}
