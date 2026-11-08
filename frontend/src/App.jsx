import { useEffect, useState } from 'react';
import { NavLink, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { api, getToken, setToken } from './api.js';
import { AuthCtx } from './authContext.js';
import { Spinner } from './components.jsx';
import { Login, Register } from './pages/auth.jsx';
import { CandidateHome, CandidateProfile, JobDetail, Jobs, MyApplications, Trajectory } from './pages/candidate.jsx';
import { Applicants, JobForm, RecruiterCompany, RecruiterHome, RecruiterJobs } from './pages/recruiter.jsx';
import { AdminAudit, AdminHome, AdminJobs, AdminSettings, AdminUsers } from './pages/admin.jsx';


const NAV = {
  CANDIDATE: [['/', 'Dashboard'], ['/profile', 'My Profile'], ['/jobs', 'Jobs'], ['/trajectory', 'Career Trajectory'], ['/applications', 'Applications']],
  RECRUITER: [['/', 'Dashboard'], ['/jobs', 'My Jobs'], ['/jobs/new', 'Post a Job'], ['/company', 'Company']],
  ADMIN: [['/', 'Overview'], ['/users', 'Users'], ['/jobs', 'Jobs'], ['/audit', 'Audit Log'], ['/settings', 'Scoring']],
};

export default function App() {
  const [user, setUser] = useState(null);
  const [booting, setBooting] = useState(!!getToken());
  const navigate = useNavigate();

  useEffect(() => {
    if (!getToken()) return;
    api('/api/auth/me').then(setUser).catch(() => setToken(null)).finally(() => setBooting(false));
  }, []);

  useEffect(() => {
    const h = () => { setUser(null); navigate('/login'); };
    window.addEventListener('auth-expired', h);
    return () => window.removeEventListener('auth-expired', h);
  }, [navigate]);

  const login = (res) => { setToken(res.token); setUser(res.user); navigate('/'); };
  const logout = () => { setToken(null); setUser(null); navigate('/login'); };

  if (booting) return <div className="center-screen"><Spinner label="Starting up…" /></div>;

  if (!user) {
    return (
      <AuthCtx.Provider value={{ user, login, logout }}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </AuthCtx.Provider>
    );
  }

  const role = user.role;
  return (
    <AuthCtx.Provider value={{ user, login, logout }}>
      <header className="topbar">
        <div className="topbar-inner">
          <span className="brand">🎯 HireLens</span>
          <nav>
            {NAV[role].map(([to, label]) => (
              <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}>{label}</NavLink>
            ))}
          </nav>
          <div className="who">
            <span className="muted small">{user.name} · {role.toLowerCase()}</span>
            <button className="btn ghost" onClick={logout}>Log out</button>
          </div>
        </div>
      </header>
      <main className="container">
        <Routes>
          {role === 'CANDIDATE' && (<>
            <Route path="/" element={<CandidateHome />} />
            <Route path="/profile" element={<CandidateProfile />} />
            <Route path="/jobs" element={<Jobs />} />
            <Route path="/jobs/:id" element={<JobDetail />} />
            <Route path="/trajectory" element={<Trajectory />} />
            <Route path="/applications" element={<MyApplications />} />
          </>)}
          {role === 'RECRUITER' && (<>
            <Route path="/" element={<RecruiterHome />} />
            <Route path="/jobs" element={<RecruiterJobs />} />
            <Route path="/jobs/new" element={<JobForm />} />
            <Route path="/jobs/:id/edit" element={<JobForm />} />
            <Route path="/jobs/:id/applicants" element={<Applicants />} />
            <Route path="/company" element={<RecruiterCompany />} />
          </>)}
          {role === 'ADMIN' && (<>
            <Route path="/" element={<AdminHome />} />
            <Route path="/users" element={<AdminUsers />} />
            <Route path="/jobs" element={<AdminJobs />} />
            <Route path="/audit" element={<AdminAudit />} />
            <Route path="/settings" element={<AdminSettings />} />
          </>)}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </AuthCtx.Provider>
  );
}
