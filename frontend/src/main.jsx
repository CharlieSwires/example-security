import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import 'bootstrap/dist/css/bootstrap.min.css';
import './styles.css';

const API_BASE = import.meta.env.VITE_API_BASE ?? 'https://localhost:8080/ExampleSecurity';

function getCookie(name) {
  return document.cookie
    .split('; ')
    .find(row => row.startsWith(name + '='))
    ?.split('=')[1];
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
  const classes = {
    SUPER: 'text-bg-danger',
    DEVELOPER: 'text-bg-warning',
    USER: 'text-bg-primary'
  };

  return <span className={`badge ${classes[role] ?? 'text-bg-secondary'} me-1`} key={role}>{role}</span>;
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
        roles: auth.roles ?? []
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
  const roles = session?.roles ?? [];
  const isSuper = roles.includes('SUPER');
  const isDeveloper = roles.includes('DEVELOPER') || isSuper;
  const isUser = roles.includes('USER') || isSuper;
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

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

  return (
    <main>
      <nav className="navbar navbar-expand-lg bg-dark navbar-dark">
        <div className="container">
          <span className="navbar-brand fw-bold">ExampleSecurity</span>
          <div className="d-flex align-items-center gap-3">
            <span className="text-white-50">{session.username}</span>
            <button className="btn btn-outline-light btn-sm" onClick={logout}>Logout</button>
          </div>
        </div>
      </nav>

      <div className="container py-4">
        <div className="p-4 bg-light rounded-4 border mb-4">
          <h1 className="h3 fw-bold">Role dashboard</h1>
          <p className="mb-2">Your roles: {roles.map(roleBadge)}</p>
          <button className="btn btn-outline-secondary btn-sm" onClick={sendPasswordChangeLink}>
            Email me a change password link
          </button>
          {message && <div className="alert alert-success mt-3 mb-0">{message}</div>}
          {error && <div className="alert alert-danger mt-3 mb-0">{error}</div>}
        </div>

        <div className="row g-4">
          <EndpointCard title="/user" description="Available to USER and SUPER." enabled={isUser} path="/user" />
          <EndpointCard title="/developer" description="Available to DEVELOPER and SUPER." enabled={isDeveloper} path="/developer" />
        </div>

        {isSuper ? (
          <AdminPanel session={session} />
        ) : (
          <div className="alert alert-info mt-4">You are not SUPER, so the user administration screen is hidden.</div>
        )}
      </div>
    </main>
  );
}

function EndpointCard({ title, description, enabled, path }) {
  const [result, setResult] = useState('');

  async function callEndpoint() {
    setResult('Loading...');
    try {
      const data = await apiFetch(path, { method: 'GET' });
      setResult(JSON.stringify(data, null, 2));
    } catch (err) {
      setResult(`Access denied or error:\n${err.message}`);
    }
  }

  return (
    <div className="col-md-6">
      <div className="card h-100 border-0 shadow-sm">
        <div className="card-body">
          <h2 className="h5 fw-bold">{title}</h2>
          <p className="text-secondary">{description}</p>
          <button className="btn btn-outline-primary" onClick={callEndpoint} disabled={!enabled}>Call endpoint</button>
          {result && <pre className="result-box mt-3">{result}</pre>}
        </div>
      </div>
    </div>
  );
}

function AdminPanel({ session }) {
  const blankForm = useMemo(() => ({ username: '', password: '', email: '', roles: ['USER'] }), []);
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
    const roles = form.roles.includes(role)
      ? form.roles.filter(item => item !== role)
      : [...form.roles, role];

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
          <h2 className="h4 fw-bold mb-0">SUPER user administration</h2>
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
                {['USER', 'DEVELOPER', 'SUPER'].map(role => (
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
              <thead><tr><th>Username</th><th>Email</th><th>Roles</th><th>New password</th><th className="text-end">Actions</th></tr></thead>
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
  const [roles, setRoles] = useState(user.roles ?? []);
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
          {['USER', 'DEVELOPER', 'SUPER'].map(role => (
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
          const nextSession = { username: user.username, roles: user.roles ?? [] };
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
