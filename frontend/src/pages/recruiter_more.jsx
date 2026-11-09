import { useState } from 'react';
import { api } from '../api.js';
import { Alert, Chips, ScoreRing, Spinner, useLoad } from '../components.jsx';

/* ---------------- Candidate search ---------------- */
export function FindCandidates() {
  const myJobs = useLoad(() => api('/api/jobs/mine'));
  const [f, setF] = useState({ skills: '', minExp: '', location: '', q: '', jobId: '', verifiedOnly: false });
  const [qs, setQs] = useState('');
  const { data, error, loading } = useLoad(() => api(`/api/recruiter/candidates${qs}`), [qs]);

  const search = (e) => {
    e.preventDefault();
    const p = new URLSearchParams();
    Object.entries(f).forEach(([k, v]) => { if (v !== '' && v !== false) p.set(k, v); });
    setQs(p.toString() ? `?${p}` : '');
  };

  return (
    <div className="stack-lg">
      <div>
        <h1>Find candidates</h1>
        <p className="muted">Searches candidates who chose to be discoverable. Pick one of your jobs to rank them by compatibility. Contact details are shared only when they apply.</p>
      </div>
      <form className="card filters" onSubmit={search}>
        <input placeholder="Must have skills (comma separated)" value={f.skills} onChange={(e) => setF({ ...f, skills: e.target.value })} />
        <input placeholder="Keyword (name, headline)" value={f.q} onChange={(e) => setF({ ...f, q: e.target.value })} />
        <input placeholder="Location" value={f.location} onChange={(e) => setF({ ...f, location: e.target.value })} />
        <input type="number" min="0" step="0.5" placeholder="Min. years" value={f.minExp} onChange={(e) => setF({ ...f, minExp: e.target.value })} />
        <select value={f.jobId} onChange={(e) => setF({ ...f, jobId: e.target.value })}>
          <option value="">Rank by evidence</option>
          {(myJobs.data || []).map(({ job }) => <option key={job.id} value={job.id}>Rank for: {job.title}</option>)}
        </select>
        <label className="check"><input type="checkbox" checked={f.verifiedOnly} onChange={(e) => setF({ ...f, verifiedOnly: e.target.checked })} /> Verified evidence only</label>
        <button className="btn primary">Search</button>
      </form>
      <Alert>{error}</Alert>
      {loading ? <Spinner /> : data.length === 0 ? <p className="muted">No candidates match.</p> : (
        <div className="grid two">
          {data.map((c) => (
            <div key={c.userId} className="card stack">
              <div className="row between top">
                <div>
                  <h3>{c.name}</h3>
                  <p className="muted small">{c.headline || 'No headline'} · {c.location || 'Location n/a'} · {c.experienceYears ?? 0} yrs</p>
                </div>
                {c.match && <ScoreRing value={c.match.score} />}
              </div>
              <Chips items={c.skills} />
              <p className="small">
                {c.verifiedEvidence > 0 ? <span className="badge s-selected">{c.verifiedEvidence} verified evidence</span> : <span className="badge s-withdrawn">self-declared only</span>}
                {' '}<span className="muted">evidence score {c.evidenceScore}</span>
              </p>
              {c.match && c.match.missing.length > 0 && <p className="small muted">Missing for this job: {c.match.missing.join(', ')}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
