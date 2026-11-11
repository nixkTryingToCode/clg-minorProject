import { useEffect, useState } from 'react';
import { NavLink, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { api, getToken, setToken } from './api.js';
import { AuthCtx } from './authContext.js';
import { NavMenu, NotificationBell, Spinner, can } from './components.jsx';
import { Login, Register } from './pages/auth.jsx';
import { CandidateHome, CandidateProfile, JobDetail, Jobs, MyApplications, Trajectory } from './pages/candidate.jsx';
import { AutoApply, Evidence, Resumes } from './pages/candidate_more.jsx';
import { FindCandidates } from './pages/recruiter_more.jsx';
import { AdminEvaluation, AdminEvidence } from './pages/admin_more.jsx';
import { Applicants, JobForm, RecruiterCompany, RecruiterHome, RecruiterJobs } from './pages/recruiter.jsx';
import { AdminAudit, AdminCompanies, AdminHome, AdminJobs, AdminSettings, AdminUsers } from './pages/admin.jsx';
import { Company, InviteAccept, Recruiters, RegisterCompany } from './pages/company.jsx';

const CANDIDATE_NAV = [
  ['/', 'Dashboard'],
  { label: 'My Profile', items: [['/profile', 'Profile'], ['/evidence', 'Evidence'], ['/applications', 'Applications']] },
  ['/jobs', 'Jobs'], ['/trajectory', 'Career Trajectory'], ['/resumes', 'Resumes'], ['/auto-apply', 'Auto-apply'],
];
const ADMIN_NAV = [
  ['/', 'Overview'], ['/users', 'Users'], ['/jobs', 'Jobs'], ['/companies', 'Companies'], ['/evidence', 'Evidence'],
  ['/evaluation', 'Evaluation'], ['/audit', 'Audit Log'], ['/settings', 'Scoring'],
];

/** Nav for recruiters and company admins; company permissions may hide individual entries. */
const hiringNav = (user) => {
  const items = [['/', 'Dashboard'], ['/jobs', 'My Jobs']];
  if (can(user, 'POST_JOB')) items.push(['/jobs/new', 'Post a Job']);
  if (can(user, 'VIEW_APPLICANTS')) items.push(['/candidates', 'Find Candidates']);
  if (user.role === 'RECRUITER') {
    items.push(['/company', 'Company']);
    if (user.companyId && can(user, 'MANAGE_RECRUITERS')) items.push(['/recruiters', 'Recruiters']);
  }
  if (user.role === 'COMPANY_ADMIN') {
    items.push({ label: 'Company', items: [['/company', 'Company'], ['/recruiters', 'Recruiters']] });
  }
  return items;
};

const navFor = (user) => {
  switch (user.role) {
    case 'CANDIDATE': return CANDIDATE_NAV;
    case 'RECRUITER':
    case 'COMPANY_ADMIN': return hiringNav(user);
    case 'ADMIN': return ADMIN_NAV;
    default: return [];
  }
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
          <Route path="/register-company" element={<RegisterCompany />} />
          <Route path="/invite/:token" element={<InviteAccept />} />
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
          <span className="brand">SNAP-HIRE</span>
          <nav>
            {navFor(user).map((entry) => (Array.isArray(entry) ? (
              <NavLink key={entry[0]} to={entry[0]} end={entry[0] === '/'} className={({ isActive }) => (isActive ? 'active' : '')}>{entry[1]}</NavLink>
            ) : (
              <NavMenu key={entry.label} label={entry.label} items={entry.items} />
            )))}
          </nav>
          <div className="who">
            <NotificationBell />
            <span className="muted small">{user.name} · {role.toLowerCase().replace(/_/g, ' ')}</span>
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
            <Route path="/evidence" element={<Evidence />} />
            <Route path="/resumes" element={<Resumes />} />
            <Route path="/auto-apply" element={<AutoApply />} />
          </>)}
          {(role === 'RECRUITER' || role === 'COMPANY_ADMIN') && (<>
            <Route path="/" element={<RecruiterHome />} />
            <Route path="/jobs" element={<RecruiterJobs />} />
            <Route path="/jobs/new" element={<JobForm />} />
            <Route path="/jobs/:id/edit" element={<JobForm />} />
            <Route path="/jobs/:id/applicants" element={<Applicants />} />
            <Route path="/candidates" element={<FindCandidates />} />
            <Route path="/recruiters" element={<Recruiters />} />
            <Route path="/company" element={role === 'COMPANY_ADMIN' ? <Company /> : <RecruiterCompany />} />
          </>)}
          {role === 'ADMIN' && (<>
            <Route path="/" element={<AdminHome />} />
            <Route path="/users" element={<AdminUsers />} />
            <Route path="/jobs" element={<AdminJobs />} />
            <Route path="/companies" element={<AdminCompanies />} />
            <Route path="/audit" element={<AdminAudit />} />
            <Route path="/evidence" element={<AdminEvidence />} />
            <Route path="/evaluation" element={<AdminEvaluation />} />
            <Route path="/settings" element={<AdminSettings />} />
          </>)}
          <Route path="/invite/:token" element={<InviteAccept />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </AuthCtx.Provider>
  );
}
