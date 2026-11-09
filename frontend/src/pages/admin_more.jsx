import { useState } from 'react';
import { api } from '../api.js';
import { Alert, Spinner, fmtDate, useLoad } from '../components.jsx';

/* ---------------- Evidence review ---------------- */
export function AdminEvidence() {
  const [status, setStatus] = useState('PENDING');
  const { data, error, loading, reload } = useLoad(() => api(`/api/admin/evidence?status=${status}`), [status]);
  const [err, setErr] = useState('');
  const review = async (id, s) => {
    const note = s === 'REJECTED' ? window.prompt('Reason (shown to the candidate):', '') : '';
    if (note === null) return;
    try { await api(`/api/admin/evidence/${id}`, { method: 'PATCH', body: { status: s, note } }); reload(); } catch (e) { setErr(e.message); }
  };
  return (
    <div className="stack-lg">
      <div className="row between"><h1>Evidence review</h1>
        <select value={status} onChange={(e) => setStatus(e.target.value)} style={{ width: 'auto' }}>
          <option>PENDING</option><option>VERIFIED</option><option>REJECTED</option>
        </select>
      </div>
      <Alert>{err || error}</Alert>
      {loading ? <Spinner /> : data.length === 0 ? <p className="muted">Nothing here.</p> : (
        <div className="stack">
          {data.map(({ evidence: e, candidate }) => (
            <div key={e.id} className="card row between top">
              <div>
                <h3>{e.title} <span className="badge">{e.type}</span></h3>
                <p className="small muted">{candidate} · added {fmtDate(e.createdAt)}{e.skill ? ` · supports ${e.skill}` : ''}</p>
                <a className="small" href={e.url} target="_blank" rel="noreferrer">{e.url}</a>
                {e.metadata && <p className="small muted">{e.metadata}</p>}
              </div>
              <div className="row gap wrap">
                <button className="btn primary" onClick={() => review(e.id, 'VERIFIED')}>Verify</button>
                <button className="btn ghost danger" onClick={() => review(e.id, 'REJECTED')}>Reject</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ---------------- Matching evaluation ---------------- */
export function AdminEvaluation() {
  const { data, error, loading, reload } = useLoad(() => api('/api/admin/evaluation'));
  if (loading) return <Spinner />;
  if (error) return <Alert>{error}</Alert>;
  return (
    <div className="stack-lg">
      <div className="row between"><h1>Matching evaluation</h1><button className="btn" onClick={reload}>Re-run</button></div>
      <p className="muted">{data.method}</p>
      <div className="grid four">
        <div className="card stat"><span>Personas evaluated</span><b>{data.personasEvaluated}</b></div>
        <div className="card stat"><span>Mean Precision@3</span><b>{data.meanPrecisionAt3}</b></div>
        <div className="card stat"><span>Hit rate @3</span><b>{data.hitRateAt3}</b></div>
        <div className="card stat"><span>Mean MRR</span><b>{data.meanMRR}</b></div>
      </div>
      <div className="card stat"><span>Mean NDCG@5</span><b>{data.meanNdcgAt5}</b></div>
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Candidate</th><th>Relevant jobs</th><th>Top 3 recommendations (✓ = relevant)</th><th>P@3</th><th>MRR</th><th>NDCG@5</th></tr></thead>
          <tbody>
            {data.rows.map((r) => (
              <tr key={r.candidate}>
                <td>{r.candidate}</td><td>{r.relevantJobs}</td><td>{r.top3.join(' · ')}</td>
                <td>{r.precisionAt3}</td><td>{r.mrr}</td><td>{r.ndcgAt5}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/* ---------------- Import external jobs (used by AdminJobs) ---------------- */
export function IngestPanel({ onDone }) {
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState({ kind: 'success', text: '' });
  const run = async () => {
    setBusy(true); setMsg({ kind: 'success', text: '' });
    try {
      const r = await api('/api/admin/ingest', { method: 'POST', body: { limit: 20 } });
      setMsg({ kind: 'success', text: `Imported ${r.imported} job(s), skipped ${r.skipped} (duplicates / non-tech). Source: ${r.source}` });
      onDone();
    } catch (e) { setMsg({ kind: 'error', text: e.message }); } finally { setBusy(false); }
  };
  return (
    <div className="card stack">
      <div className="row between">
        <div><h3>External job import</h3><p className="muted small">Pulls current tech jobs from a public job-board API into the common job model. Candidates get the link plus an optimised application package.</p></div>
        <button className="btn primary" onClick={run} disabled={busy}>{busy ? 'Importing…' : 'Import jobs'}</button>
      </div>
      <Alert kind={msg.kind}>{msg.text}</Alert>
    </div>
  );
}
