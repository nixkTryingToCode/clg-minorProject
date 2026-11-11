import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../authContext.js';
import { Alert } from '../components.jsx';

function Shell({ title, subtitle, children }) {
  return (
    <div className="auth-wrap">
      <div className="auth-hero">
        <h1>SNAP-HIRE</h1>
        <p>Recruitment and career trajectory platform.</p>
        <ul>
          <li>Upload a resume and get an editable structured profile</li>
          <li>See explainable job compatibility and skill gaps</li>
          <li>Recruiters get applicants ranked by job-specific fit</li>
        </ul>
      </div>
      <div className="card auth-card">
        <h2>{title}</h2>
        <p className="muted">{subtitle}</p>
        {children}
      </div>
    </div>
  );
}

export function Login() {
  const { login } = useAuth();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try { login(await api('/api/auth/login', { method: 'POST', body: form })); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };

  return (
    <Shell title="Welcome back" subtitle="Log in to continue">
      <form onSubmit={submit} className="stack">
        <Alert>{error}</Alert>
        <label>Email<input type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
        <label>Password<input type="password" required value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
        <button className="btn primary" disabled={busy}>{busy ? 'Signing in…' : 'Log in'}</button>
      </form>
      <p className="small">New here? <Link to="/register">Create an account</Link></p>
      <details className="small demo">
        <summary>Demo accounts</summary>
        <p>Candidate: <code>candidate@demo.com</code><br />Recruiter: <code>recruiter@demo.com</code><br />Password: <code>Demo@1234</code> (unless changed by the deployer)</p>
      </details>
    </Shell>
  );
}

export function Register() {
  const { login } = useAuth();
  const [form, setForm] = useState({ name: '', email: '', password: '', role: 'CANDIDATE', companyName: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try { login(await api('/api/auth/register', { method: 'POST', body: form })); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  };

  return (
    <Shell title="Create your account" subtitle="Choose how you will use the platform">
      <form onSubmit={submit} className="stack">
        <Alert>{error}</Alert>
        <div className="seg">
          <button type="button" className={form.role === 'CANDIDATE' ? 'on' : ''} onClick={() => setForm({ ...form, role: 'CANDIDATE' })}>I'm a candidate</button>
          <button type="button" className={form.role === 'RECRUITER' ? 'on' : ''} onClick={() => setForm({ ...form, role: 'RECRUITER' })}>I'm a recruiter</button>
        </div>
        <label>Full name<input required value={form.name} onChange={set('name')} /></label>
        <label>Email<input type="email" required value={form.email} onChange={set('email')} /></label>
        <label>Password (min 8 chars)<input type="password" required minLength={8} value={form.password} onChange={set('password')} /></label>
        {form.role === 'RECRUITER' && <label>Company name<input required value={form.companyName} onChange={set('companyName')} /></label>}
        <button className="btn primary" disabled={busy}>{busy ? 'Creating…' : 'Sign up'}</button>
      </form>
      <p className="small">Already registered? <Link to="/login">Log in</Link></p>
    </Shell>
  );
}
