import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import 'bootstrap/dist/css/bootstrap.min.css';
import './styles.css';

const API_BASE = import.meta.env.VITE_API_BASE ?? 'https://localhost:8080/ExampleSecurity';

const APP_ROLES = ['PATIENT', 'OFFICE', 'OFFICE_ADMIN', 'HQ', 'SUPER'];

function normaliseRole(role) {
  if (role === 'USER') return 'PATIENT';
  if (role === 'DEVELOPER') return 'OFFICE_ADMIN';
  return role;
}

function normaliseRoles(roles = []) {
  return [...new Set((roles ?? []).map(normaliseRole))];
}

function hasAnyRole(roles, allowed) {
  const normalised = normaliseRoles(roles);
  return allowed.some(role => normalised.includes(role));
}

function getCookie(name) {
  const cookies = document.cookie
    .split('; ')
    .filter(row => row.startsWith(name + '='));

  if (cookies.length === 0) {
    return undefined;
  }

  return cookies[cookies.length - 1].split('=')[1];
}
async function ensureCsrfToken() {
  const existing = getCookie('XSRF-TOKEN');
  if (existing) {
    return decodeURIComponent(existing);
  }

  const response = await fetch(`${API_BASE}/api/csrf`, {
    method: 'GET',
    credentials: 'include'
  });

  if (!response.ok) {
    throw new Error(`Could not obtain CSRF token: ${response.status}`);
  }

  const body = await response.json();
  return body.token ?? decodeURIComponent(getCookie('XSRF-TOKEN') ?? '');
}

async function apiFetch(path, options = {}) {
  const method = (options.method ?? 'GET').toUpperCase();

  const headers = {
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(options.headers ?? {})
  };

  if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
    headers['X-XSRF-TOKEN'] = await ensureCsrfToken();
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    method,
    headers,
    credentials: 'include'
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `${response.status} ${response.statusText}`);
  }

  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get('content-type') ?? '';
  return contentType.includes('application/json') ? response.json() : response.text();
}

function routeFromLocation() {
  const path = window.location.pathname;
  const params = new URLSearchParams(window.location.search);

  if (path === '/forgot-password') return { name: 'forgot' };
  if (path === '/reset-password') return { name: 'reset', token: params.get('token') ?? '' };

  return { name: 'main' };
}

function roleBadge(role) {
  const displayRole = normaliseRole(role);
  const classes = {
    PATIENT: 'text-bg-primary',
    OFFICE: 'text-bg-info',
    OFFICE_ADMIN: 'text-bg-warning',
    HQ: 'text-bg-dark',
    SUPER: 'text-bg-danger'
  };

  return <span className={`badge ${classes[displayRole] ?? 'text-bg-secondary'} me-1`} key={displayRole}>{displayRole}</span>;
}

function LoginScreen({ onLogin, onForgotPassword }) {
  const [username, setUsername] = useState('super');
  const [password, setPassword] = useState('ChangeThisPassword123!');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function login(event) {
    event.preventDefault();
    setBusy(true);
    setError('');

    try {
      const auth = await apiFetch('/api/login', {
        method: 'POST',
        body: JSON.stringify({ username, password })
      });

      setPassword('');
      onLogin({
        username: auth.username,
        roles: normaliseRoles(auth.roles ?? [])
      });
    } catch (err) {
      setError('Login failed. Check the username/password and that the backend is running.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <div className="card shadow-lg border-0 login-card">
        <div className="card-body p-4 p-md-5">
          <h1 className="h3 mb-2 fw-bold">ExampleSecurity</h1>
          <p className="text-secondary mb-4">Login to test Spring Security roles.</p>

          {error && <div className="alert alert-danger">{error}</div>}

          <form onSubmit={login}>
            <div className="mb-3">
              <label className="form-label">Username</label>
              <input className="form-control form-control-lg" value={username} onChange={event => setUsername(event.target.value)} autoComplete="username" />
            </div>

            <div className="mb-3">
              <label className="form-label">Password</label>
              <input className="form-control form-control-lg" type="password" value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" />
            </div>

            <button className="btn btn-primary btn-lg w-100" disabled={busy}>{busy ? 'Signing in...' : 'Sign in'}</button>
          </form>

          <button className="btn btn-link px-0 mt-3" onClick={onForgotPassword}>Forgot password?</button>

          <p className="small text-secondary mt-4 mb-0">
            Passwords are posted once to the backend; later API calls use the server-side HttpOnly session cookie.
          </p>
        </div>
      </div>
    </main>
  );
}

function ForgotPasswordScreen({ onBackToLogin }) {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);

    try {
      await apiFetch('/api/password/forgot', {
        method: 'POST',
        body: JSON.stringify({ email })
      });
    } finally {
      setBusy(false);
      setSent(true);
      setTimeout(onBackToLogin, 2000);
    }
  }

  return (
    <main className="login-page">
      <div className="card shadow-lg border-0 login-card">
        <div className="card-body p-4 p-md-5">
          <h1 className="h3 mb-2 fw-bold">Forgot password</h1>
          <p className="text-secondary mb-4">Enter your verified email address. If it matches a verified account, a reset link will be sent.</p>

          {sent && <div className="alert alert-info">If that email is verified, a password reset link has been sent. Returning to login...</div>}

          <form onSubmit={submit}>
            <label className="form-label">Email address</label>
            <input className="form-control form-control-lg mb-3" type="email" value={email} onChange={event => setEmail(event.target.value)} required />
            <button className="btn btn-primary btn-lg w-100" disabled={busy}>{busy ? 'Checking...' : 'Send reset link'}</button>
          </form>

          <button className="btn btn-link px-0 mt-3" onClick={onBackToLogin}>Back to login</button>
        </div>
      </div>
    </main>
  );
}

function ResetPasswordScreen({ token, onBackToLogin }) {
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setMessage('');
    setError('');

    if (password !== confirm) {
      setError('Passwords do not match.');
      return;
    }

    setBusy(true);

    try {
      await apiFetch('/api/password/reset', {
        method: 'POST',
        body: JSON.stringify({ token, password })
      });

      setPassword('');
      setConfirm('');
      setMessage('Password changed. You can now login.');
      setTimeout(onBackToLogin, 2000);
    } catch (err) {
      setError('The reset link is invalid or expired.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <div className="card shadow-lg border-0 login-card">
        <div className="card-body p-4 p-md-5">
          <h1 className="h3 mb-2 fw-bold">Change password</h1>
          <p className="text-secondary mb-4">Enter a new password for this account.</p>

          {message && <div className="alert alert-success">{message}</div>}
          {error && <div className="alert alert-danger">{error}</div>}

          <form onSubmit={submit}>
            <label className="form-label">New password</label>
            <input className="form-control form-control-lg mb-3" type="password" value={password} onChange={event => setPassword(event.target.value)} required />

            <label className="form-label">Confirm password</label>
            <input className="form-control form-control-lg mb-3" type="password" value={confirm} onChange={event => setConfirm(event.target.value)} required />

            <button className="btn btn-primary btn-lg w-100" disabled={busy || !token}>{busy ? 'Changing...' : 'Change password'}</button>
          </form>

          <button className="btn btn-link px-0 mt-3" onClick={onBackToLogin}>Back to login</button>
        </div>
      </div>
    </main>
  );
}

function Dashboard({ session, onLogout }) {
  const roles = normaliseRoles(session?.roles ?? []);
  const isSuper = roles.includes('SUPER');
  const canPatientRead = hasAnyRole(roles, ['PATIENT', 'OFFICE', 'OFFICE_ADMIN', 'HQ', 'SUPER']);
  const canOffice = hasAnyRole(roles, ['OFFICE', 'OFFICE_ADMIN', 'HQ', 'SUPER']);
  const canOfficeAdmin = hasAnyRole(roles, ['OFFICE_ADMIN', 'HQ', 'SUPER']);
  const canHq = hasAnyRole(roles, ['HQ', 'SUPER']);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const defaultScreen = canPatientRead ? 'patient' : canOffice ? 'office' : canOfficeAdmin ? 'admin' : canHq ? 'hq' : 'super';
  const [activeScreen, setActiveScreen] = useState(defaultScreen);

  const screens = [
    { key: 'patient', title: 'Patient portal', roles: ['PATIENT', 'OFFICE', 'OFFICE_ADMIN', 'HQ', 'SUPER'], enabled: canPatientRead },
    { key: 'office', title: 'Office / clinicians', roles: ['OFFICE', 'OFFICE_ADMIN', 'HQ', 'SUPER'], enabled: canOffice },
    { key: 'admin', title: 'Office admin', roles: ['OFFICE_ADMIN', 'HQ', 'SUPER'], enabled: canOfficeAdmin },
    { key: 'hq', title: 'HQ', roles: ['HQ', 'SUPER'], enabled: canHq },
    { key: 'super', title: 'Super admin', roles: ['SUPER'], enabled: isSuper }
  ];

  async function sendPasswordChangeLink() {
    setMessage('');
    setError('');

    try {
      const result = await apiFetch('/api/password/change-link', { method: 'POST' });
      setMessage(result.message);
    } catch (err) {
      setError('No verified email is available for this user, or the email could not be sent.');
    }
  }

  async function logout() {
    try {
      await apiFetch('/api/logout', { method: 'POST' });
    } finally {
      onLogout();
    }
  }

  function renderScreen() {
    switch (activeScreen) {
      case 'patient':
        return canPatientRead ? <PatientPortalScreen /> : <AccessPanel title="Patient portal" />;
      case 'office':
        return canOffice ? <OfficeClinicianScreen /> : <AccessPanel title="Office / clinicians" />;
      case 'admin':
        return canOfficeAdmin ? <OfficeAdminScreen /> : <AccessPanel title="Office admin" />;
      case 'hq':
        return canHq ? <HqScreen /> : <AccessPanel title="HQ" />;
      case 'super':
        return isSuper ? <SuperScreen session={session} /> : <AccessPanel title="Super admin" />;
      default:
        return null;
    }
  }

  return (
    <main>
      <nav className="navbar navbar-expand-lg bg-dark navbar-dark">
        <div className="container-fluid px-4">
          <span className="navbar-brand fw-bold">ExampleSecurity Ophthalmic Clinics</span>
          <div className="d-flex align-items-center gap-3">
            <span className="text-white-50">{session.username}</span>
            <button className="btn btn-outline-light btn-sm" onClick={logout}>Logout</button>
          </div>
        </div>
      </nav>

      <div className="container-fluid py-4 px-4">
        <div className="p-4 bg-light rounded-4 border mb-4">
          <div className="d-flex flex-column flex-lg-row justify-content-between gap-3">
            <div>
              <h1 className="h3 fw-bold mb-2">Ophthalmic clinic role dashboard</h1>
              <p className="mb-2">Your roles: {roles.map(roleBadge)}</p>
              <p className="text-secondary mb-0">
                PATIENT is read only. OFFICE is for clinicians. OFFICE_ADMIN manages a clinic. HQ spans clinics. SUPER controls system administration.
              </p>
            </div>
            <div className="align-self-start">
              <button className="btn btn-outline-secondary btn-sm" onClick={sendPasswordChangeLink}>
                Email me a change password link
              </button>
            </div>
          </div>
          {message && <div className="alert alert-success mt-3 mb-0">{message}</div>}
          {error && <div className="alert alert-danger mt-3 mb-0">{error}</div>}
        </div>

        <div className="screen-tabs card border-0 shadow-sm mb-4">
          <div className="card-body p-2">
            <div className="nav nav-pills flex-column flex-md-row gap-2" role="tablist">
              {screens.map(screen => (
                <button
                  key={screen.key}
                  className={`nav-link text-start ${activeScreen === screen.key ? 'active' : ''}`}
                  disabled={!screen.enabled}
                  onClick={() => setActiveScreen(screen.key)}
                >
                  <span className="fw-semibold d-block">{screen.title}</span>
                  <span className="small opacity-75">{screen.enabled ? `Allowed: ${screen.roles.join(', ')}` : 'Not available for your role'}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        {renderScreen()}
      </div>
    </main>
  );
}

function AccessPanel({ title }) {
  return <div className="alert alert-warning">You do not have permission to open the {title} screen.</div>;
}

function PatientPortalScreen() {
  return (
    <section className="row g-4">
      <div className="col-xl-4">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">Patient summary - read only</h2></div>
          <div className="card-body">
            <div className="patient-photo-placeholder mb-3">VA</div>
            <h3 className="h6 fw-bold">Vision and appointment record</h3>
            <p className="text-secondary">Patients can view their clinic details, documents and current prescriptions, but cannot alter clinical notes.</p>
            <dl className="row mb-0 small">
              <dt className="col-5">Next appointment</dt><dd className="col-7">Tuesday 09:30 - OCT review</dd>
              <dt className="col-5">Clinic</dt><dd className="col-7">Goole Ophthalmic Office</dd>
              <dt className="col-5">Status</dt><dd className="col-7"><span className="badge text-bg-success">Read only</span></dd>
            </dl>
          </div>
        </div>
      </div>

      <div className="col-xl-8">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">Patient documents</h2></div>
          <div className="card-body">
            <div className="table-responsive">
              <table className="table align-middle">
                <thead><tr><th>Document</th><th>Date</th><th>Clinician</th><th>Access</th></tr></thead>
                <tbody>
                  <tr><td>Prescription summary</td><td>13 Jun 2026</td><td>Office clinician</td><td><button className="btn btn-outline-primary btn-sm">View</button></td></tr>
                  <tr><td>OCT scan note</td><td>10 Jun 2026</td><td>Office clinician</td><td><button className="btn btn-outline-primary btn-sm">View</button></td></tr>
                  <tr><td>Aftercare instructions</td><td>10 Jun 2026</td><td>Office admin</td><td><button className="btn btn-outline-primary btn-sm">View</button></td></tr>
                </tbody>
              </table>
            </div>
            <div className="alert alert-info mb-0">Later this is where encrypted patient documents would be decrypted for authorised read-only viewing.</div>
          </div>
        </div>
      </div>
    </section>
  );
}

function OfficeClinicianScreen() {
  return (
    <section className="row g-4">
      <div className="col-xl-7">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3 d-flex justify-content-between align-items-center">
            <h2 className="h5 fw-bold mb-0">Today&apos;s ophthalmic clinic list</h2>
            <span className="badge text-bg-info">OFFICE</span>
          </div>
          <div className="card-body">
            <div className="table-responsive">
              <table className="table align-middle">
                <thead><tr><th>Time</th><th>Patient</th><th>Reason</th><th>Clinician action</th></tr></thead>
                <tbody>
                  <tr><td>09:00</td><td>Patient A</td><td>Visual acuity + refraction</td><td><button className="btn btn-primary btn-sm">Open record</button></td></tr>
                  <tr><td>09:30</td><td>Patient B</td><td>OCT review</td><td><button className="btn btn-primary btn-sm">Open record</button></td></tr>
                  <tr><td>10:00</td><td>Patient C</td><td>Contact lens check</td><td><button className="btn btn-primary btn-sm">Open record</button></td></tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <div className="col-xl-5">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">Clinical capture</h2></div>
          <div className="card-body">
            <label className="form-label">Patient search</label>
            <input className="form-control mb-3" placeholder="Name, NHS number, DOB or internal patient id" />
            <div className="row g-3">
              <div className="col-md-6"><label className="form-label">Right VA</label><input className="form-control" placeholder="6/6" /></div>
              <div className="col-md-6"><label className="form-label">Left VA</label><input className="form-control" placeholder="6/9" /></div>
              <div className="col-md-6"><label className="form-label">IOP right</label><input className="form-control" placeholder="mmHg" /></div>
              <div className="col-md-6"><label className="form-label">IOP left</label><input className="form-control" placeholder="mmHg" /></div>
            </div>
            <label className="form-label mt-3">Clinical notes</label>
            <textarea className="form-control" rows="4" placeholder="Encrypted clinical notes would be saved here." />
            <button className="btn btn-success mt-3">Save draft note</button>
          </div>
        </div>
      </div>
    </section>
  );
}

function OfficeAdminScreen() {
  return (
    <section className="row g-4">
      <div className="col-xl-4">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">Clinic administration</h2></div>
          <div className="card-body">
            <label className="form-label">Clinic / office name</label>
            <input className="form-control mb-3" defaultValue="Goole Ophthalmic Office" />
            <label className="form-label">Appointment length</label>
            <select className="form-select mb-3" defaultValue="30"><option value="15">15 minutes</option><option value="30">30 minutes</option><option value="45">45 minutes</option></select>
            <label className="form-label">Opening days</label>
            <div className="d-flex flex-wrap gap-2 mb-3">
              {['Mon', 'Tue', 'Wed', 'Thu', 'Fri'].map(day => <span className="badge text-bg-light border" key={day}>{day}</span>)}
            </div>
            <button className="btn btn-success">Save clinic settings</button>
          </div>
        </div>
      </div>

      <div className="col-xl-8">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">Office staff and appointments</h2></div>
          <div className="card-body">
            <div className="row g-3 mb-4">
              <Metric title="Clinicians" value="4" />
              <Metric title="Patients today" value="21" />
              <Metric title="Outstanding letters" value="7" />
            </div>
            <div className="table-responsive">
              <table className="table align-middle">
                <thead><tr><th>Staff member</th><th>Role</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  <tr><td>Optometrist 1</td><td>OFFICE</td><td><span className="badge text-bg-success">Active</span></td><td><button className="btn btn-outline-secondary btn-sm">Manage</button></td></tr>
                  <tr><td>Clinic manager</td><td>OFFICE_ADMIN</td><td><span className="badge text-bg-success">Active</span></td><td><button className="btn btn-outline-secondary btn-sm">Manage</button></td></tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function HqScreen() {
  return (
    <section className="row g-4">
      <div className="col-12">
        <div className="card border-0 shadow-sm">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">HQ clinic network overview</h2></div>
          <div className="card-body">
            <div className="row g-3 mb-4">
              <Metric title="Offices" value="12" />
              <Metric title="Open referrals" value="186" />
              <Metric title="Overdue follow-ups" value="9" />
              <Metric title="Audit exceptions" value="2" />
            </div>
            <div className="table-responsive">
              <table className="table align-middle">
                <thead><tr><th>Office</th><th>Region</th><th>Today&apos;s patients</th><th>Compliance</th><th>Action</th></tr></thead>
                <tbody>
                  <tr><td>Goole</td><td>East Yorkshire</td><td>21</td><td><span className="badge text-bg-success">Good</span></td><td><button className="btn btn-outline-primary btn-sm">View</button></td></tr>
                  <tr><td>Bradford</td><td>West Yorkshire</td><td>34</td><td><span className="badge text-bg-warning">Check</span></td><td><button className="btn btn-outline-primary btn-sm">View</button></td></tr>
                  <tr><td>Leeds</td><td>West Yorkshire</td><td>42</td><td><span className="badge text-bg-success">Good</span></td><td><button className="btn btn-outline-primary btn-sm">View</button></td></tr>
                </tbody>
              </table>
            </div>
            <div className="alert alert-secondary mb-0">HQ can report across offices without giving every office access to every other office&apos;s patient records.</div>
          </div>
        </div>
      </div>
    </section>
  );
}

function SuperScreen({ session }) {
  return (
    <section className="row g-4">
      <div className="col-xl-4">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">System controls</h2></div>
          <div className="card-body">
            <p className="text-secondary">SUPER is for system-wide setup, role management, encryption-key operations and emergency administration.</p>
            <ul className="list-group list-group-flush mb-3">
              <li className="list-group-item px-0">Create offices and HQ users</li>
              <li className="list-group-item px-0">Rotate encrypted private-key passphrases</li>
              <li className="list-group-item px-0">Review audit and security events</li>
            </ul>
            <button className="btn btn-outline-danger">Lock encryption keys</button>
          </div>
        </div>
      </div>
      <div className="col-xl-8">
        <AdminPanel session={session} />
      </div>
    </section>
  );
}

function Metric({ title, value }) {
  return (
    <div className="col-md-3 col-6">
      <div className="metric-card border rounded-3 p-3 bg-light h-100">
        <div className="small text-secondary">{title}</div>
        <div className="h3 fw-bold mb-0">{value}</div>
      </div>
    </div>
  );
}

function AdminPanel({ session }) {
  const blankForm = useMemo(() => ({ username: '', password: '', email: '', roles: ['PATIENT'] }), []);
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState(blankForm);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  async function loadUsers() {
    setError('');
    try {
      const data = await apiFetch('/api/admin/users', { method: 'GET' });
      setUsers(data);
    } catch (err) {
      setError('Could not load users.');
    }
  }

  useEffect(() => {
    loadUsers();
  }, []);

  function toggleRole(role) {
    const currentRoles = normaliseRoles(form.roles);
    const roles = currentRoles.includes(role)
      ? currentRoles.filter(item => item !== role)
      : [...currentRoles, role];

    setForm({ ...form, roles });
  }

  async function createUser(event) {
    event.preventDefault();
    setError('');
    setMessage('');

    try {
      await apiFetch('/api/admin/users', {
        method: 'POST',
        body: JSON.stringify(form)
      });

      setForm(blankForm);
      setMessage('User created. If an email was entered, a verification email has been sent.');
      await loadUsers();
    } catch (err) {
      setError('Could not create user. Username may already exist.');
    }
  }

  async function saveRoles(username, roles) {
    setError('');
    setMessage('');

    try {
      await apiFetch(`/api/admin/users/${encodeURIComponent(username)}/roles`, {
        method: 'PUT',
        body: JSON.stringify({ roles })
      });

      setMessage(`Roles updated for ${username}.`);
      await loadUsers();
    } catch (err) {
      setError(`Could not update roles for ${username}.`);
    }
  }

  async function proposeEmail(username, email) {
    setError('');
    setMessage('');

    try {
      await apiFetch(`/api/admin/users/${encodeURIComponent(username)}/email`, {
        method: 'PUT',
        body: JSON.stringify({ email })
      });

      setMessage(`Verification email sent to ${email}. It will only be saved after the link is clicked.`);
      await loadUsers();
    } catch (err) {
      setError(`Could not send verification email for ${username}.`);
    }
  }

  async function updatePassword(username, password) {
    setError('');
    setMessage('');

    try {
      await apiFetch(`/api/admin/users/${encodeURIComponent(username)}/password`, {
        method: 'PUT',
        body: JSON.stringify({ password })
      });

      setMessage(`Password updated for ${username}.`);
    } catch (err) {
      setError(`Could not update password for ${username}.`);
    }
  }

  async function deleteUser(username) {
    setError('');
    setMessage('');

    try {
      await apiFetch(`/api/admin/users/${encodeURIComponent(username)}`, { method: 'DELETE' });
      setMessage(`${username} deleted.`);
      await loadUsers();
    } catch (err) {
      setError(`Could not delete ${username}.`);
    }
  }

  return (
    <section className="mt-4">
      <div className="card border-0 shadow-sm">
        <div className="card-header bg-white py-3">
          <h2 className="h4 fw-bold mb-0">SUPER user administration - Patient / Office roles</h2>
        </div>

        <div className="card-body">
          {error && <div className="alert alert-danger">{error}</div>}
          {message && <div className="alert alert-success">{message}</div>}

          <form className="row g-3 align-items-end mb-4" onSubmit={createUser}>
            <div className="col-md-2">
              <label className="form-label">Username</label>
              <input className="form-control" value={form.username} onChange={event => setForm({ ...form, username: event.target.value })} required />
            </div>

            <div className="col-md-2">
              <label className="form-label">Password</label>
              <input className="form-control" type="password" value={form.password} onChange={event => setForm({ ...form, password: event.target.value })} required />
            </div>

            <div className="col-md-3">
              <label className="form-label">Proposed email</label>
              <input className="form-control" type="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} />
            </div>

            <div className="col-md-3">
              <label className="form-label">Roles</label>
              <div className="d-flex gap-3 flex-wrap">
                {APP_ROLES.map(role => (
                  <label className="form-check" key={role}>
                    <input className="form-check-input" type="checkbox" checked={form.roles.includes(role)} onChange={() => toggleRole(role)} />
                    <span className="form-check-label">{role}</span>
                  </label>
                ))}
              </div>
            </div>

            <div className="col-md-2"><button className="btn btn-success w-100">Add user</button></div>
          </form>

          <div className="table-responsive">
            <table className="table align-middle">
              <thead><tr><th>Username</th><th>Email</th><th>Patient/office roles</th><th>New password</th><th className="text-end">Actions</th></tr></thead>
              <tbody>
              {users.map(user => (
                <UserRow key={user.id} user={user} currentUsername={session.username} onSaveRoles={saveRoles} onProposeEmail={proposeEmail} onUpdatePassword={updatePassword} onDelete={deleteUser} />
              ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  );
}

function UserRow({ user, currentUsername, onSaveRoles, onProposeEmail, onUpdatePassword, onDelete }) {
  const [roles, setRoles] = useState(normaliseRoles(user.roles ?? []));
  const [newPassword, setNewPassword] = useState('');
  const [proposedEmail, setProposedEmail] = useState('');

  function toggle(role) {
    setRoles(roles.includes(role) ? roles.filter(item => item !== role) : [...roles, role]);
  }

  async function updatePassword() {
    if (!newPassword.trim()) return;
    await onUpdatePassword(user.username, newPassword);
    setNewPassword('');
  }

  async function sendVerification() {
    if (!proposedEmail.trim()) return;
    await onProposeEmail(user.username, proposedEmail);
    setProposedEmail('');
  }

  return (
    <tr>
      <td className="fw-semibold">{user.username}</td>
      <td>
        <div>{user.email ? user.email : <span className="text-secondary">No verified email</span>}</div>
        {user.emailVerified && <span className="badge text-bg-success">verified</span>}
        {user.pendingEmail && <div className="small text-warning">Pending: {user.pendingEmail}</div>}
        <div className="input-group input-group-sm mt-2">
          <input className="form-control" type="email" placeholder="Propose email" value={proposedEmail} onChange={event => setProposedEmail(event.target.value)} />
          <button className="btn btn-outline-secondary" onClick={sendVerification} disabled={!proposedEmail.trim()}>Verify</button>
        </div>
      </td>
      <td>
        <div className="d-flex gap-3 flex-wrap">
          {APP_ROLES.map(role => (
            <label className="form-check mb-0" key={role}>
              <input className="form-check-input" type="checkbox" checked={roles.includes(role)} onChange={() => toggle(role)} />
              <span className="form-check-label">{role}</span>
            </label>
          ))}
        </div>
      </td>
      <td>
        <input className="form-control form-control-sm" type="password" placeholder="New password" value={newPassword} onChange={event => setNewPassword(event.target.value)} />
      </td>
      <td className="text-end text-nowrap">
        <button className="btn btn-outline-primary btn-sm me-2" onClick={() => onSaveRoles(user.username, roles)}>Save roles</button>
        <button className="btn btn-outline-secondary btn-sm me-2" onClick={updatePassword} disabled={!newPassword.trim()}>Update pw</button>
        <button className="btn btn-outline-danger btn-sm" disabled={user.username === currentUsername} onClick={() => onDelete(user.username)}>Delete</button>
      </td>
    </tr>
  );
}

function App() {
  const [route, setRoute] = useState(routeFromLocation());
  const [session, setSession] = useState(() => {
    const saved = sessionStorage.getItem('example-security-user');
    return saved ? JSON.parse(saved) : null;
  });

  useEffect(() => {
    if (!session && route.name === 'main') {
      apiFetch('/api/me')
        .then(user => {
          const nextSession = { username: user.username, roles: normaliseRoles(user.roles ?? []) };
          sessionStorage.setItem('example-security-user', JSON.stringify(nextSession));
          setSession(nextSession);
        })
        .catch(() => {
          sessionStorage.removeItem('example-security-user');
        });
    }
  }, [route.name]);

  function navigate(path) {
    window.history.pushState({}, '', path);
    setRoute(routeFromLocation());
  }

  function login(nextSession) {
    sessionStorage.setItem('example-security-user', JSON.stringify(nextSession));
    sessionStorage.removeItem('example-security-session');
    setSession(nextSession);
  }

  function logout() {
    sessionStorage.removeItem('example-security-user');
    sessionStorage.removeItem('example-security-session');
    setSession(null);
  }

  if (route.name === 'forgot') return <ForgotPasswordScreen onBackToLogin={() => navigate('/')} />;
  if (route.name === 'reset') return <ResetPasswordScreen token={route.token} onBackToLogin={() => navigate('/')} />;

  return session
    ? <Dashboard session={session} onLogout={logout} />
    : <LoginScreen onLogin={login} onForgotPassword={() => navigate('/forgot-password')} />;
}

createRoot(document.getElementById('root')).render(<App />);
