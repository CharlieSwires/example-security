import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import 'bootstrap/dist/css/bootstrap.min.css';
import './styles.css';

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080/ExampleSecurity';

function basicAuthHeader(username, password) {
  return `Basic ${btoa(`${username}:${password}`)}`;
}

async function apiFetch(path, options = {}, credentials = null) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers ?? {})
  };

  if (credentials) {
    headers.Authorization = basicAuthHeader(credentials.username, credentials.password);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers
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

function roleBadge(role) {
  const classes = {
    SUPER: 'text-bg-danger',
    DEVELOPER: 'text-bg-warning',
    USER: 'text-bg-primary'
  };

  return <span className={`badge ${classes[role] ?? 'text-bg-secondary'} me-1`} key={role}>{role}</span>;
}

function LoginScreen({ onLogin }) {
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

      onLogin({ username, password, roles: auth.roles });
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
              <input
                className="form-control form-control-lg"
                value={username}
                onChange={event => setUsername(event.target.value)}
                autoComplete="username"
              />
            </div>

            <div className="mb-4">
              <label className="form-label">Password</label>
              <input
                className="form-control form-control-lg"
                type="password"
                value={password}
                onChange={event => setPassword(event.target.value)}
                autoComplete="current-password"
              />
            </div>

            <button className="btn btn-primary btn-lg w-100" disabled={busy}>
              {busy ? 'Signing in...' : 'Sign in'}
            </button>
          </form>

          <p className="small text-secondary mt-4 mb-0">
            For production, serve this over HTTPS and replace browser-stored passwords with secure cookies or token flow.
          </p>
        </div>
      </div>
    </main>
  );
}

function Dashboard({ session, onLogout }) {
  const isSuper = session.roles.includes('SUPER');
  const isDeveloper = session.roles.includes('DEVELOPER') || isSuper;
  const isUser = session.roles.includes('USER') || isSuper;

  return (
    <main>
      <nav className="navbar navbar-expand-lg bg-dark navbar-dark">
        <div className="container">
          <span className="navbar-brand fw-bold">ExampleSecurity</span>
          <div className="d-flex align-items-center gap-3">
            <span className="text-white-50">{session.username}</span>
            <button className="btn btn-outline-light btn-sm" onClick={onLogout}>Logout</button>
          </div>
        </div>
      </nav>

      <div className="container py-4">
        <div className="p-4 bg-light rounded-4 border mb-4">
          <h1 className="h3 fw-bold">Role dashboard</h1>
          <p className="mb-2">Your roles: {session.roles.map(roleBadge)}</p>
          <p className="text-secondary mb-0">SUPER users can manage users, developers and supers.</p>
        </div>

        <div className="row g-4">
          <EndpointCard
            title="/user"
            description="Available to USER and SUPER."
            enabled={isUser}
            session={session}
            path="/user"
          />

          <EndpointCard
            title="/developer"
            description="Available to DEVELOPER and SUPER."
            enabled={isDeveloper}
            session={session}
            path="/developer"
          />
        </div>

        {isSuper ? (
          <AdminPanel session={session} />
        ) : (
          <div className="alert alert-info mt-4">
            You are not SUPER, so the user administration screen is hidden.
          </div>
        )}
      </div>
    </main>
  );
}

function EndpointCard({ title, description, enabled, session, path }) {
  const [result, setResult] = useState('');

  async function callEndpoint() {
    setResult('Loading...');
    try {
      const data = await apiFetch(path, { method: 'GET' }, session);
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
          <button className="btn btn-outline-primary" onClick={callEndpoint} disabled={!enabled}>
            Call endpoint
          </button>
          {result && <pre className="result-box mt-3">{result}</pre>}
        </div>
      </div>
    </div>
  );
}

function AdminPanel({ session }) {
  const blankForm = useMemo(() => ({ username: '', password: '', roles: ['USER'] }), []);
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState(blankForm);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  async function loadUsers() {
    setError('');
    try {
      const data = await apiFetch('/api/admin/users', { method: 'GET' }, session);
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
      }, session);

      setForm(blankForm);
      setMessage('User created.');
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
      }, session);

      setMessage(`Roles updated for ${username}.`);
      await loadUsers();
    } catch (err) {
      setError(`Could not update ${username}.`);
    }
  }

  async function deleteUser(username) {
    setError('');
    setMessage('');

    try {
      await apiFetch(`/api/admin/users/${encodeURIComponent(username)}`, {
        method: 'DELETE'
      }, session);

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
            <div className="col-md-3">
              <label className="form-label">Username</label>
              <input
                className="form-control"
                value={form.username}
                onChange={event => setForm({ ...form, username: event.target.value })}
                required
              />
            </div>

            <div className="col-md-3">
              <label className="form-label">Password</label>
              <input
                className="form-control"
                type="password"
                value={form.password}
                onChange={event => setForm({ ...form, password: event.target.value })}
                required
              />
            </div>

            <div className="col-md-4">
              <label className="form-label">Roles</label>
              <div className="d-flex gap-3 flex-wrap">
                {['USER', 'DEVELOPER', 'SUPER'].map(role => (
                  <label className="form-check" key={role}>
                    <input
                      className="form-check-input"
                      type="checkbox"
                      checked={form.roles.includes(role)}
                      onChange={() => toggleRole(role)}
                    />
                    <span className="form-check-label">{role}</span>
                  </label>
                ))}
              </div>
            </div>

            <div className="col-md-2">
              <button className="btn btn-success w-100">Add user</button>
            </div>
          </form>

          <div className="table-responsive">
            <table className="table align-middle">
              <thead>
              <tr>
                <th>Username</th>
                <th>Roles</th>
                <th className="text-end">Actions</th>
              </tr>
              </thead>
              <tbody>
              {users.map(user => (
                <UserRow
                  key={user.id}
                  user={user}
                  currentUsername={session.username}
                  onSaveRoles={saveRoles}
                  onDelete={deleteUser}
                />
              ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  );
}

function UserRow({ user, currentUsername, onSaveRoles, onDelete }) {
  const [roles, setRoles] = useState(user.roles);

  function toggle(role) {
    setRoles(roles.includes(role)
      ? roles.filter(item => item !== role)
      : [...roles, role]
    );
  }

  return (
    <tr>
      <td className="fw-semibold">{user.username}</td>
      <td>
        <div className="d-flex gap-3 flex-wrap">
          {['USER', 'DEVELOPER', 'SUPER'].map(role => (
            <label className="form-check mb-0" key={role}>
              <input
                className="form-check-input"
                type="checkbox"
                checked={roles.includes(role)}
                onChange={() => toggle(role)}
              />
              <span className="form-check-label">{role}</span>
            </label>
          ))}
        </div>
      </td>
      <td className="text-end">
        <button className="btn btn-outline-primary btn-sm me-2" onClick={() => onSaveRoles(user.username, roles)}>
          Save roles
        </button>
        <button
          className="btn btn-outline-danger btn-sm"
          disabled={user.username === currentUsername}
          onClick={() => onDelete(user.username)}
        >
          Delete
        </button>
      </td>
    </tr>
  );
}

function App() {
  const [session, setSession] = useState(() => {
    const saved = sessionStorage.getItem('example-security-session');
    return saved ? JSON.parse(saved) : null;
  });

  function login(nextSession) {
    sessionStorage.setItem('example-security-session', JSON.stringify(nextSession));
    setSession(nextSession);
  }

  function logout() {
    sessionStorage.removeItem('example-security-session');
    setSession(null);
  }

  return session
    ? <Dashboard session={session} onLogout={logout} />
    : <LoginScreen onLogin={login} />;
}

createRoot(document.getElementById('root')).render(<App />);
