import { useState } from 'react';
import { api } from '../api.js';
import { Alert, Spinner, fmtDate, useLoad } from '../components.jsx';

/* ---------------- Evidence review ---------------- */
export function AdminEvidence() {
  const [status, setStatus] = useState('PENDING');
  const { data, error, loading, reload } = useLoad(() => api(`/api/admin/evidence?status=${status}`), [status]);
  const auto = useLoad(() => api('/api/admin/evidence/auto-verify'));
  const [msg, setMsg] = useState({ kind: 'error', text: '' });
  const [busy, setBusy] = useState(false);

  const review = async (id, s) => {
    const note = s === 'REJECTED' ? window.prompt('Reason (shown to the candidate):', '') : '';
    if (note === null) return;
    try {
      await api(`/api/admin/evidence/${id}`, { method: 'PATCH', body: { status: s, note } });
      setMsg({ kind: 'success', text: `Evidence marked ${s.toLowerCase()} — the candidate has been notified.` });
      reload();
    } catch (e) { setMsg({ kind: 'error', text: e.message }); }
  };

  const toggleAuto = async () => {
    setBusy(true);
    setMsg({ kind: 'error', text: '' });
    try {
      const r = await api('/api/admin/evidence/auto-verify', { method: 'PUT', body: { enabled: !auto.data.enabled } });
      setMsg({
        kind: 'success',
        text: r.enabled
          ? 'Auto checks are on — conclusive results now set the status directly.'
          : 'Auto checks are off — checks still run and annotate each item, but everything waits for your review.',
      });
      auto.reload();
      reload();
    } catch (e) { setMsg({ kind: 'error', text: e.message }); }
    finally { setBusy(false); }
  };

  return (
    <div className="stack-lg">
      <div className="row between"><h1>Evidence review</h1>
        <select value={status} onChange={(e) => setStatus(e.target.value)} style={{ width: 'auto' }}>
          <option>PENDING</option><option>VERIFIED</option><option>REJECTED</option>
        </select>
      </div>
      {auto.data && (
        <section className="card row between wrap">
          <div>
            <h3>Automatic checks</h3>
            <p className="muted small" style={{ maxWidth: '48rem' }}>
              GitHub and LeetCode links are checked through their public APIs; LinkedIn, Kaggle and
              HackerRank links through a reachability check (a blocked or timed-out check never
              rejects — it just waits here). When on, conclusive results set the status directly.
              Turn it off to review every item yourself; checks keep running and note what they found.
            </p>
          </div>
          <button className={`btn ${auto.data.enabled ? 'primary' : ''}`} disabled={busy} onClick={toggleAuto}>
            {auto.data.enabled ? 'On — turn off' : 'Off — turn on'}
          </button>
        </section>
      )}
      <Alert kind={msg.kind}>{msg.text}</Alert>
      <Alert>{error}</Alert>
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
