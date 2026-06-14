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


function withOfficeQuery(path, officeId) {
  if (!officeId) return path;
  const separator = path.includes('?') ? '&' : '?';
  return `${path}${separator}officeId=${encodeURIComponent(officeId)}`;
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
  const [password, setPassword] = useState('');
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
  const defaultScreen = isSuper ? 'super' : canHq ? 'hq' : canOfficeAdmin ? 'admin' : canOffice ? 'office' : 'patient';
  const [activeScreen, setActiveScreen] = useState(defaultScreen);
  const [offices, setOffices] = useState([]);
  const [selectedOfficeId, setSelectedOfficeId] = useState('');

  async function loadOfficesForContext() {
    if (!canHq) return;
    try {
      const data = await apiFetch('/api/hq/offices', { method: 'GET' });
      setOffices(data);
      if (!selectedOfficeId && data.length > 0) {
        setSelectedOfficeId(data[0].officeId);
      }
    } catch (err) {
      // Keep the dashboard usable if no offices have been created yet.
    }
  }

  useEffect(() => {
    loadOfficesForContext();
  }, [canHq]);

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
        return canOffice ? <OfficeClinicianScreen selectedOfficeId={isSuper ? selectedOfficeId : ''} /> : <AccessPanel title="Office / clinicians" />;
      case 'admin':
        return canOfficeAdmin ? <OfficeAdminScreen selectedOfficeId={isSuper ? selectedOfficeId : ''} /> : <AccessPanel title="Office admin" />;
      case 'hq':
        return canHq ? <HqScreen onOfficesChanged={loadOfficesForContext} /> : <AccessPanel title="HQ" />;
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
          {isSuper && (
            <div className="mt-3 p-3 bg-white rounded-3 border">
              <label className="form-label fw-semibold">SUPER active office context</label>
              <select className="form-select" value={selectedOfficeId} onChange={event => setSelectedOfficeId(event.target.value)}>
                <option value="">All offices / no office selected</option>
                {offices.map(office => (
                  <option key={office.officeId} value={office.officeId}>{office.displayName || office.officeId} ({office.officeId})</option>
                ))}
              </select>
              <div className="form-text">When SUPER opens Office or Office Admin tabs, this selected office is passed to the backend.</div>
            </div>
          )}
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
  const [appointments, setAppointments] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [expandedNotes, setExpandedNotes] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function loadAppointments() {
      setBusy(true);
      setError('');

      try {
        const data = await apiFetch('/api/patient/appointments', { method: 'GET' });
        if (cancelled) return;
        setAppointments(data);
        setSelectedId(data[0]?.id ?? null);
      } catch (err) {
        if (!cancelled) setError('Could not load the patient appointment documents.');
      } finally {
        if (!cancelled) setBusy(false);
      }
    }

    loadAppointments();
    return () => { cancelled = true; };
  }, []);

  const selectedAppointment = appointments.find(item => item.id === selectedId) ?? appointments[0] ?? null;

  function formatDate(value) {
    if (!value) return '';
    return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(`${value}T00:00:00`));
  }

  function toggleNote(index) {
    setExpandedNotes({
      ...expandedNotes,
      [`${selectedAppointment?.id}-${index}`]: !expandedNotes[`${selectedAppointment?.id}-${index}`]
    });
  }

  return (
    <section className="row g-4">
      <div className="col-xl-4">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3">
            <h2 className="h5 fw-bold mb-0">Appointments - read only</h2>
          </div>
          <div className="card-body">
            <p className="text-secondary">
              This is the patient landing screen. The patient can read appointments, prescriptions, clinician names and notes, but cannot change them.
            </p>

            {busy && <div className="alert alert-secondary">Loading appointments...</div>}
            {error && <div className="alert alert-danger">{error}</div>}
            {!busy && !error && appointments.length === 0 && <div className="alert alert-info">No appointment documents are available for this patient account.</div>}

            <div className="list-group appointment-list">
              {appointments.map(appointment => (
                <button
                  type="button"
                  key={appointment.id}
                  className={`list-group-item list-group-item-action ${selectedAppointment?.id === appointment.id ? 'active' : ''}`}
                  onClick={() => {
                    setSelectedId(appointment.id);
                    setExpandedNotes({});
                  }}
                >
                  <div className="d-flex justify-content-between gap-3">
                    <strong>{formatDate(appointment.appointmentDate)}</strong>
                    <span>{appointment.appointmentTime}</span>
                  </div>
                  <div>{appointment.appointmentType}</div>
                  <div className="small opacity-75">{appointment.clinician}</div>
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="col-xl-8">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3 d-flex justify-content-between align-items-center">
            <h2 className="h5 fw-bold mb-0">Appointment document</h2>
            <span className="badge text-bg-success">PATIENT read only</span>
          </div>
          <div className="card-body">
            {!selectedAppointment && !busy && <div className="alert alert-info">Select an appointment to open the document.</div>}

            {selectedAppointment && (
              <>
                <div className="row g-3 mb-4">
                  <div className="col-md-4">
                    <div className="document-field">
                      <div className="small text-secondary">Appointment</div>
                      <div className="fw-semibold">{formatDate(selectedAppointment.appointmentDate)} at {selectedAppointment.appointmentTime}</div>
                      <div>{selectedAppointment.appointmentType}</div>
                      <div className="small text-secondary">{selectedAppointment.clinicName}</div>
                    </div>
                  </div>
                  <div className="col-md-4">
                    <div className="document-field">
                      <div className="small text-secondary">Prescription</div>
                      <div>{selectedAppointment.prescription}</div>
                    </div>
                  </div>
                  <div className="col-md-4">
                    <div className="document-field">
                      <div className="small text-secondary">Clinician</div>
                      <div className="fw-semibold">{selectedAppointment.clinician}</div>
                    </div>
                  </div>
                </div>

                <h3 className="h6 fw-bold mb-3">Clinical notes</h3>
                <div className="table-responsive">
                  <table className="table align-middle patient-notes-table">
                    <thead>
                    <tr>
                      <th>Date created</th>
                      <th>Subject</th>
                      <th>Note</th>
                    </tr>
                    </thead>
                    <tbody>
                    {selectedAppointment.notes.map((note, index) => {
                      const key = `${selectedAppointment.id}-${index}`;
                      const expanded = !!expandedNotes[key];
                      return (
                        <tr key={key}>
                          <td className="text-nowrap">{formatDate(note.createdDate)}</td>
                          <td className="fw-semibold">{note.subject}</td>
                          <td>
                            {expanded && <p className="mb-2">{note.noteText}</p>}
                            <button className="btn btn-outline-primary btn-sm" onClick={() => toggleNote(index)}>
                              {expanded ? 'Less...' : 'More...'}
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                    </tbody>
                  </table>
                </div>

                <div className="alert alert-warning mb-0">
                  Read-only patient view: there are no save, edit, delete or note-entry controls on this screen.
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

function OfficeClinicianScreen({ selectedOfficeId = '' }) {
  const [appointments, setAppointments] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [prescription, setPrescription] = useState('');
  const [noteSubject, setNoteSubject] = useState('');
  const [noteDate, setNoteDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [noteText, setNoteText] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(true);

  async function loadAppointments() {
    setBusy(true);
    setError('');
    try {
      const data = await apiFetch(withOfficeQuery('/api/office/appointments', selectedOfficeId), { method: 'GET' });
      setAppointments(data);
      const nextSelected = data.find(item => item.id === selectedId) ?? data[0] ?? null;
      setSelectedId(nextSelected?.id ?? null);
      setPrescription(nextSelected?.prescription ?? '');
    } catch (err) {
      setError('Could not load office appointment documents.');
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    loadAppointments();
  }, [selectedOfficeId]);

  const selectedAppointment = appointments.find(item => item.id === selectedId) ?? null;

  function formatDate(value) {
    if (!value) return '';
    return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(`${value}T00:00:00`));
  }

  function selectAppointment(appointment) {
    setSelectedId(appointment.id);
    setPrescription(appointment.prescription ?? '');
    setNoteSubject('');
    setNoteText('');
    setMessage('');
    setError('');
  }

  async function saveClinicalDocument(event) {
    event.preventDefault();
    if (!selectedAppointment) return;

    setMessage('');
    setError('');

    try {
      const updated = await apiFetch(`/api/office/appointments/${encodeURIComponent(selectedAppointment.id)}/clinical`, {
        method: 'PUT',
        body: JSON.stringify({
          prescription,
          noteDate,
          noteSubject,
          noteText
        })
      });

      setAppointments(appointments.map(item => item.id === updated.id ? updated : item));
      setSelectedId(updated.id);
      setPrescription(updated.prescription ?? '');
      setNoteSubject('');
      setNoteText('');
      setMessage('Prescription and clinical note saved for this office appointment.');
    } catch (err) {
      setError('Could not save the clinical document. Check that this patient belongs to your office.');
    }
  }

  return (
    <section className="row g-4">
      <div className="col-xl-5">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3 d-flex justify-content-between align-items-center">
            <h2 className="h5 fw-bold mb-0">Office patient appointments</h2>
            <span className="badge text-bg-info">OFFICE</span>
          </div>
          <div className="card-body">
            <p className="text-secondary">Clinicians can update the prescription and add clinical notes only for patients attached to their own office.</p>
            {busy && <div className="alert alert-secondary">Loading appointments...</div>}
            {error && <div className="alert alert-danger">{error}</div>}
            {!busy && appointments.length === 0 && <div className="alert alert-info">No appointments are available for this office.</div>}

            <div className="list-group appointment-list">
              {appointments.map(appointment => (
                <button
                  type="button"
                  key={appointment.id}
                  className={`list-group-item list-group-item-action ${selectedAppointment?.id === appointment.id ? 'active' : ''}`}
                  onClick={() => selectAppointment(appointment)}
                >
                  <div className="d-flex justify-content-between gap-3">
                    <strong>{formatDate(appointment.appointmentDate)}</strong>
                    <span>{appointment.appointmentTime}</span>
                  </div>
                  <div>{appointment.patientDisplayName || appointment.patientUsername}</div>
                  <div className="small opacity-75">{appointment.appointmentType} · {appointment.clinician}</div>
                  {appointment.patientTelephone && <div className="small opacity-75">Tel: {appointment.patientTelephone}</div>}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="col-xl-7">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3">
            <h2 className="h5 fw-bold mb-0">Prescription and clinical notes</h2>
          </div>
          <div className="card-body">
            {!selectedAppointment && <div className="alert alert-info">Select an appointment to enter prescription and notes.</div>}
            {message && <div className="alert alert-success">{message}</div>}

            {selectedAppointment && (
              <form onSubmit={saveClinicalDocument}>
                <div className="row g-3 mb-3">
                  <div className="col-md-6">
                    <div className="document-field">
                      <div className="small text-secondary">Patient</div>
                      <div className="fw-semibold">{selectedAppointment.patientDisplayName || selectedAppointment.patientUsername}</div>
                      <div className="small text-secondary">Username: {selectedAppointment.patientUsername}</div>
                      {selectedAppointment.patientTelephone && <div className="small text-secondary">Tel: {selectedAppointment.patientTelephone}</div>}
                    </div>
                  </div>
                  <div className="col-md-6">
                    <div className="document-field">
                      <div className="small text-secondary">Appointment / clinician</div>
                      <div className="fw-semibold">{formatDate(selectedAppointment.appointmentDate)} at {selectedAppointment.appointmentTime}</div>
                      <div>{selectedAppointment.clinician}</div>
                    </div>
                  </div>
                </div>

                <label className="form-label">Prescription</label>
                <textarea className="form-control mb-3" rows="3" value={prescription} onChange={event => setPrescription(event.target.value)} placeholder="Enter spectacle/contact lens prescription or no-change note" />

                <div className="row g-3">
                  <div className="col-md-4">
                    <label className="form-label">Note date</label>
                    <input className="form-control" type="date" value={noteDate} onChange={event => setNoteDate(event.target.value)} />
                  </div>
                  <div className="col-md-8">
                    <label className="form-label">Subject heading</label>
                    <input className="form-control" value={noteSubject} onChange={event => setNoteSubject(event.target.value)} placeholder="e.g. Refraction completed" />
                  </div>
                </div>

                <label className="form-label mt-3">Clinical note</label>
                <textarea className="form-control" rows="5" value={noteText} onChange={event => setNoteText(event.target.value)} placeholder="Enter the clinical note for this appointment" />

                <div className="d-flex gap-2 mt-3">
                  <button className="btn btn-success">Save prescription / add note</button>
                  <button type="button" className="btn btn-outline-secondary" onClick={loadAppointments}>Reload</button>
                </div>
              </form>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

function OfficeAdminScreen({ selectedOfficeId = '' }) {
  const today = new Date().toISOString().slice(0, 10);
  const blankForm = {
    patientUsername: '',
    patientDisplayName: '',
    patientTelephone: '',
    officeId: selectedOfficeId || 'goole',
    appointmentDate: today,
    appointmentTime: '09:00',
    appointmentType: 'Sight test and refraction',
    clinicName: '',
    clinician: ''
  };

  const [appointments, setAppointments] = useState([]);
  const [form, setForm] = useState(blankForm);
  const [editingId, setEditingId] = useState(null);
  const [editForm, setEditForm] = useState({ appointmentDate: today, appointmentTime: '09:00', appointmentType: '', clinician: '' });
  const [movePatient, setMovePatient] = useState({ username: '', targetOfficeId: '' });
  const [moveClinician, setMoveClinician] = useState({ username: '', targetOfficeId: '' });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [patientLookupMessage, setPatientLookupMessage] = useState('');
  const [patientLookupBusy, setPatientLookupBusy] = useState(false);
  const [busy, setBusy] = useState(true);

  async function loadAppointments() {
    setBusy(true);
    setError('');
    try {
      const data = await apiFetch(withOfficeQuery('/api/office-admin/appointments', selectedOfficeId), { method: 'GET' });
      setAppointments(data);
    } catch (err) {
      setError('Could not load office appointments.');
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (selectedOfficeId) setForm(current => ({ ...current, officeId: selectedOfficeId }));
    loadAppointments();
  }, [selectedOfficeId]);

  function formatDate(value) {
    if (!value) return '';
    return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(`${value}T00:00:00`));
  }

  async function lookupPatient() {
    const username = form.patientUsername.trim();
    setPatientLookupMessage('');
    if (!username) {
      setForm({ ...form, patientDisplayName: '', patientTelephone: '' });
      return;
    }
    setPatientLookupBusy(true);
    setError('');
    try {
      const patient = await apiFetch(withOfficeQuery(`/api/office-admin/patients/${encodeURIComponent(username)}`, selectedOfficeId || form.officeId), { method: 'GET' });
      setForm(current => ({
        ...current,
        patientUsername: patient.username ?? username,
        patientDisplayName: patient.displayName ?? '',
        patientTelephone: patient.telephone ?? '',
        officeId: patient.officeId ?? current.officeId
      }));
      setPatientLookupMessage('Patient details loaded from the encrypted user record.');
    } catch (err) {
      setPatientLookupMessage('');
      setForm(current => ({ ...current, patientDisplayName: '', patientTelephone: '' }));
      setError('Could not find that PATIENT user in your office. Check the username and office boundary.');
    } finally {
      setPatientLookupBusy(false);
    }
  }

  async function createAppointment(event) {
    event.preventDefault();
    setMessage('');
    setError('');
    try {
      await apiFetch('/api/office-admin/appointments', { method: 'POST', body: JSON.stringify(form) });
      setForm({ ...blankForm, appointmentDate: form.appointmentDate, clinicName: form.clinicName, officeId: form.officeId });
      setMessage('Appointment created. OFFICE clinicians can now enter the prescription and notes.');
      await loadAppointments();
    } catch (err) {
      setError('Could not create appointment. Check patient username, clinician and office boundary.');
    }
  }

  function startEdit(appointment) {
    setEditingId(appointment.id);
    setEditForm({
      appointmentDate: appointment.appointmentDate ?? today,
      appointmentTime: appointment.appointmentTime ?? '09:00',
      appointmentType: appointment.appointmentType ?? '',
      clinician: appointment.clinician ?? ''
    });
    setMessage('');
    setError('');
  }

  async function saveAppointmentAdmin(event) {
    event.preventDefault();
    if (!editingId) return;
    setMessage('');
    setError('');
    try {
      await apiFetch(`/api/office-admin/appointments/${encodeURIComponent(editingId)}/admin`, {
        method: 'PUT',
        body: JSON.stringify(editForm)
      });
      setEditingId(null);
      setMessage('Appointment updated.');
      await loadAppointments();
    } catch (err) {
      setError('Could not update appointment.');
    }
  }

  async function deleteAppointment(id) {
    if (!confirm('Remove this appointment?')) return;
    setMessage('');
    setError('');
    try {
      await apiFetch(`/api/office-admin/appointments/${encodeURIComponent(id)}`, { method: 'DELETE' });
      setMessage('Appointment removed.');
      await loadAppointments();
    } catch (err) {
      setError('Could not remove appointment.');
    }
  }

  async function movePatientOffice(event) {
    event.preventDefault();
    setMessage('');
    setError('');
    try {
      const result = await apiFetch(`/api/office-admin/patients/${encodeURIComponent(movePatient.username)}/office`, {
        method: 'PUT',
        body: JSON.stringify({ targetOfficeId: movePatient.targetOfficeId })
      });
      setMessage(`Patient ${result.username} moved to ${result.officeId}. Existing appointment documents moved too.`);
      setMovePatient({ username: '', targetOfficeId: '' });
      await loadAppointments();
    } catch (err) {
      setError('Could not move patient. Check username, source office and target office id.');
    }
  }

  async function moveClinicianOffice(event) {
    event.preventDefault();
    setMessage('');
    setError('');
    try {
      const result = await apiFetch(`/api/office-admin/clinicians/${encodeURIComponent(moveClinician.username)}/office`, {
        method: 'PUT',
        body: JSON.stringify({ targetOfficeId: moveClinician.targetOfficeId })
      });
      setMessage(`Clinician/staff user ${result.username} moved to ${result.officeId}.`);
      setMoveClinician({ username: '', targetOfficeId: '' });
    } catch (err) {
      setError('Could not move clinician. Check username, source office and target office id.');
    }
  }

  return (
    <section className="row g-4">
      <div className="col-xl-4">
        <div className="card border-0 shadow-sm mb-4">
          <div className="card-header bg-white py-3 d-flex justify-content-between align-items-center">
            <h2 className="h5 fw-bold mb-0">Create appointment</h2>
            <span className="badge text-bg-warning">OFFICE_ADMIN</span>
          </div>
          <div className="card-body">
            <p className="text-secondary">Office admin creates appointments and assigns the clinician. Non-HQ users are restricted to their own office.</p>
            {message && <div className="alert alert-success">{message}</div>}
            {error && <div className="alert alert-danger">{error}</div>}
            <form onSubmit={createAppointment}>
              <label className="form-label">Patient username</label>
              <div className="input-group mb-2">
                <input className="form-control" value={form.patientUsername} onChange={event => { setPatientLookupMessage(''); setForm({ ...form, patientUsername: event.target.value, patientDisplayName: '', patientTelephone: '' }); }} onBlur={lookupPatient} required />
                <button type="button" className="btn btn-outline-primary" onClick={lookupPatient} disabled={patientLookupBusy || !form.patientUsername.trim()}>{patientLookupBusy ? 'Loading...' : 'Lookup'}</button>
              </div>
              {patientLookupMessage && <div className="form-text text-success mb-2">{patientLookupMessage}</div>}
              <label className="form-label">Patient display name</label>
              <input className="form-control mb-2" value={form.patientDisplayName} readOnly placeholder="Populated from patient username" />
              <label className="form-label">Patient telephone</label>
              <input className="form-control mb-2" value={form.patientTelephone} readOnly placeholder="Populated from patient username" />
              <label className="form-label">Office / clinic id</label>
              <input className="form-control mb-2" value={form.officeId} onChange={event => setForm({ ...form, officeId: event.target.value })} readOnly={!!selectedOfficeId} />
              <div className="row g-2">
                <div className="col-md-7"><label className="form-label">Appointment date</label><input className="form-control mb-2" type="date" value={form.appointmentDate} onChange={event => setForm({ ...form, appointmentDate: event.target.value })} required /></div>
                <div className="col-md-5"><label className="form-label">Time</label><input className="form-control mb-2" type="time" value={form.appointmentTime} onChange={event => setForm({ ...form, appointmentTime: event.target.value })} required /></div>
              </div>
              <label className="form-label">Appointment type</label>
              <input className="form-control mb-2" value={form.appointmentType} onChange={event => setForm({ ...form, appointmentType: event.target.value })} required />
              <label className="form-label">Clinic name</label>
              <input className="form-control mb-2" value={form.clinicName} onChange={event => setForm({ ...form, clinicName: event.target.value })} required />
              <label className="form-label">Clinician name</label>
              <input className="form-control mb-3" value={form.clinician} onChange={event => setForm({ ...form, clinician: event.target.value })} placeholder="e.g. Ms L. Bennett, Optometrist" required />
              <button className="btn btn-success w-100">Create appointment</button>
            </form>
          </div>
        </div>

        <div className="card border-0 shadow-sm">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">Move people between practices</h2></div>
          <div className="card-body">
            <form onSubmit={movePatientOffice} className="mb-4">
              <h3 className="h6 fw-bold">Move patient</h3>
              <input className="form-control mb-2" placeholder="Patient username" value={movePatient.username} onChange={event => setMovePatient({ ...movePatient, username: event.target.value })} required />
              <input className="form-control mb-2" placeholder="Target office id" value={movePatient.targetOfficeId} onChange={event => setMovePatient({ ...movePatient, targetOfficeId: event.target.value })} required />
              <button className="btn btn-outline-primary w-100">Move patient</button>
            </form>
            <form onSubmit={moveClinicianOffice}>
              <h3 className="h6 fw-bold">Move clinician/staff user</h3>
              <input className="form-control mb-2" placeholder="Clinician username" value={moveClinician.username} onChange={event => setMoveClinician({ ...moveClinician, username: event.target.value })} required />
              <input className="form-control mb-2" placeholder="Target office id" value={moveClinician.targetOfficeId} onChange={event => setMoveClinician({ ...moveClinician, targetOfficeId: event.target.value })} required />
              <button className="btn btn-outline-primary w-100">Move clinician</button>
            </form>
          </div>
        </div>
      </div>

      <div className="col-xl-8">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">Office appointment list</h2></div>
          <div className="card-body">
            {busy && <div className="alert alert-secondary">Loading appointments...</div>}
            <div className="table-responsive">
              <table className="table align-middle">
                <thead><tr><th>Date</th><th>Patient</th><th>Telephone</th><th>Office</th><th>Clinician</th><th>Status</th><th className="text-end">Actions</th></tr></thead>
                <tbody>
                {appointments.map(appointment => (
                  <React.Fragment key={appointment.id}>
                    <tr>
                      <td className="text-nowrap">{formatDate(appointment.appointmentDate)} {appointment.appointmentTime}</td>
                      <td><div className="fw-semibold">{appointment.patientDisplayName || appointment.patientUsername}</div><div className="small text-secondary">{appointment.patientUsername}</div></td>
                      <td>{appointment.patientTelephone || <span className="text-secondary">Not held</span>}</td>
                      <td>{appointment.officeId}</td>
                      <td>{appointment.clinician}</td>
                      <td>{appointment.prescription && !appointment.prescription.includes('not yet') ? <span className="badge text-bg-success">Entered</span> : <span className="badge text-bg-secondary">Waiting for OFFICE</span>}</td>
                      <td className="text-end text-nowrap"><button className="btn btn-outline-primary btn-sm me-2" onClick={() => startEdit(appointment)}>Edit</button><button className="btn btn-outline-danger btn-sm" onClick={() => deleteAppointment(appointment.id)}>Remove</button></td>
                    </tr>
                    {editingId === appointment.id && (
                      <tr><td colSpan="7">
                        <form className="row g-2 align-items-end bg-light p-3 rounded" onSubmit={saveAppointmentAdmin}>
                          <div className="col-md-3"><label className="form-label">Reschedule date</label><input className="form-control" type="date" value={editForm.appointmentDate} onChange={event => setEditForm({ ...editForm, appointmentDate: event.target.value })} /></div>
                          <div className="col-md-2"><label className="form-label">Time</label><input className="form-control" type="time" value={editForm.appointmentTime} onChange={event => setEditForm({ ...editForm, appointmentTime: event.target.value })} /></div>
                          <div className="col-md-3"><label className="form-label">Type</label><input className="form-control" value={editForm.appointmentType} onChange={event => setEditForm({ ...editForm, appointmentType: event.target.value })} /></div>
                          <div className="col-md-2"><label className="form-label">Clinician</label><input className="form-control" value={editForm.clinician} onChange={event => setEditForm({ ...editForm, clinician: event.target.value })} /></div>
                          <div className="col-md-2 d-flex gap-2"><button className="btn btn-success">Save</button><button type="button" className="btn btn-outline-secondary" onClick={() => setEditingId(null)}>Cancel</button></div>
                        </form>
                      </td></tr>
                    )}
                  </React.Fragment>
                ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function HqScreen({ onOfficesChanged = () => {} }) {
  const blankOffice = { officeId: '', username: '', password: '', displayName: '', address: '', telephone: '', email: '' };
  const [offices, setOffices] = useState([]);
  const [form, setForm] = useState(blankOffice);
  const [movePractice, setMovePractice] = useState({ fromOfficeId: '', toOfficeId: '' });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(true);

  async function loadOffices() {
    setBusy(true);
    setError('');
    try {
      const data = await apiFetch('/api/hq/offices', { method: 'GET' });
      setOffices(data);
    } catch (err) {
      setError('Could not load offices.');
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { loadOffices(); }, []);

  async function createOffice(event) {
    event.preventDefault();
    setMessage('');
    setError('');
    try {
      await apiFetch('/api/hq/offices', { method: 'POST', body: JSON.stringify(form) });
      setForm(blankOffice);
      setMessage('Office created. SUPER can now choose it as the active office context.');
      await loadOffices();
      await onOfficesChanged();
    } catch (err) {
      setError('Could not create office. Check office id, username and password are unique and complete.');
    }
  }

  async function deleteOffice(officeId) {
    if (!confirm(`Delete office ${officeId}? Existing patients and appointments keep their officeId unless you move them first.`)) return;
    setMessage('');
    setError('');
    try {
      await apiFetch(`/api/hq/offices/${encodeURIComponent(officeId)}`, { method: 'DELETE' });
      setMessage(`${officeId} deleted.`);
      await loadOffices();
      await onOfficesChanged();
    } catch (err) {
      setError(`Could not delete ${officeId}.`);
    }
  }

  async function movePracticePatients(event) {
    event.preventDefault();
    setMessage('');
    setError('');
    try {
      const result = await apiFetch('/api/hq/offices/move-patients', {
        method: 'POST',
        body: JSON.stringify(movePractice)
      });
      setMessage(`Moved ${result.patientsMoved} patients, ${result.cliniciansMoved} clinicians/staff users and ${result.appointmentsMoved} appointments from ${result.fromOfficeId} to ${result.toOfficeId}. The old office was left in place for manual review/deletion.`);
      setMovePractice({ fromOfficeId: '', toOfficeId: '' });
      await loadOffices();
      await onOfficesChanged();
    } catch (err) {
      setError('Could not move patients. Check both offices exist and are different.');
    }
  }

  return (
    <section className="row g-4">
      <div className="col-xl-4">
        <div className="card border-0 shadow-sm mb-4">
          <div className="card-header bg-white py-3 d-flex justify-content-between align-items-center"><h2 className="h5 fw-bold mb-0">Add office</h2><span className="badge text-bg-dark">HQ</span></div>
          <div className="card-body">
            <p className="text-secondary">HQ creates office accounts. Address and telephone are encrypted at rest; office id remains plaintext for routing, sorting and access checks.</p>
            {message && <div className="alert alert-success">{message}</div>}
            {error && <div className="alert alert-danger">{error}</div>}
            <form onSubmit={createOffice}>
              <label className="form-label">Office / clinic id</label><input className="form-control mb-2" value={form.officeId} onChange={event => setForm({ ...form, officeId: event.target.value })} placeholder="goole" required />
              <label className="form-label">Office username</label><input className="form-control mb-2" value={form.username} onChange={event => setForm({ ...form, username: event.target.value })} placeholder="goole-office" required />
              <label className="form-label">Office password</label><input className="form-control mb-2" type="password" value={form.password} onChange={event => setForm({ ...form, password: event.target.value })} required />
              <label className="form-label">Display name</label><input className="form-control mb-2" value={form.displayName} onChange={event => setForm({ ...form, displayName: event.target.value })} placeholder="Goole Ophthalmic Clinic" />
              <label className="form-label">Address</label><textarea className="form-control mb-2" rows="3" value={form.address} onChange={event => setForm({ ...form, address: event.target.value })} />
              <label className="form-label">Telephone</label><input className="form-control mb-2" value={form.telephone} onChange={event => setForm({ ...form, telephone: event.target.value })} />
              <label className="form-label">Email</label><input className="form-control mb-3" type="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} />
              <button className="btn btn-success w-100">Add office</button>
            </form>
          </div>
        </div>

        <div className="card border-0 shadow-sm">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">Move practice patients and clinicians</h2></div>
          <div className="card-body">
            <p className="text-secondary">Move all patients, office clinicians/staff and appointment documents from one practice to another. The old office record is not deleted automatically; delete it manually after checking the move.</p>
            <form onSubmit={movePracticePatients}>
              <label className="form-label">From office</label>
              <select className="form-select mb-2" value={movePractice.fromOfficeId} onChange={event => setMovePractice({ ...movePractice, fromOfficeId: event.target.value })} required>
                <option value="">Choose source office...</option>
                {offices.map(office => <option key={office.officeId} value={office.officeId}>{office.officeId} — {office.displayName}</option>)}
              </select>
              <label className="form-label">To office</label>
              <select className="form-select mb-2" value={movePractice.toOfficeId} onChange={event => setMovePractice({ ...movePractice, toOfficeId: event.target.value })} required>
                <option value="">Choose target office...</option>
                {offices.map(office => <option key={office.officeId} value={office.officeId}>{office.officeId} — {office.displayName}</option>)}
              </select>
              <div className="alert alert-warning small mb-3">The old office will remain after the move. Delete it manually from the office list once you have checked the transfer.</div>
              <button className="btn btn-outline-primary w-100">Move patients and clinicians</button>
            </form>
          </div>
        </div>
      </div>

      <div className="col-xl-8">
        <div className="card border-0 shadow-sm h-100">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">Office list</h2></div>
          <div className="card-body">
            {busy && <div className="alert alert-secondary">Loading offices...</div>}
            <div className="table-responsive">
              <table className="table align-middle">
                <thead><tr><th>Office</th><th>Login username</th><th>Address</th><th>Telephone</th><th>Email</th><th className="text-end">Actions</th></tr></thead>
                <tbody>{offices.map(office => (<tr key={office.officeId}><td><div className="fw-semibold">{office.displayName || office.officeId}</div><div className="small text-secondary">{office.officeId}</div></td><td>{office.username}</td><td>{office.address || <span className="text-secondary">Not held</span>}</td><td>{office.telephone || <span className="text-secondary">Not held</span>}</td><td>{office.email || <span className="text-secondary">Not held</span>}</td><td className="text-end"><button className="btn btn-outline-danger btn-sm" onClick={() => deleteOffice(office.officeId)}>Delete</button></td></tr>))}</tbody>
              </table>
            </div>
            <div className="alert alert-secondary mb-0">For practice closure, use “Move practice patients and clinicians” first so patient accounts, clinician/staff users and appointment documents transfer to the new office. Then delete the old office manually.</div>
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
        <div className="card border-0 shadow-sm mb-4">
          <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">System controls</h2></div>
          <div className="card-body">
            <p className="text-secondary">SUPER is for system-wide setup, role management, encryption-key operations and emergency administration.</p>
            <ul className="list-group list-group-flush mb-3">
              <li className="list-group-item px-0">Create offices and HQ users</li>
              <li className="list-group-item px-0">Rotate field-encryption 14-word strings</li>
              <li className="list-group-item px-0">Review audit and security events</li>
            </ul>
            <button className="btn btn-outline-danger">Lock encryption keys</button>
          </div>
        </div>
        <CryptoRotationPanel />
      </div>
      <div className="col-xl-8">
        <AdminPanel session={session} />
      </div>
    </section>
  );
}

function CryptoRotationPanel() {
  const [form, setForm] = useState({ oldPassphrase: '', newPassphrase: '', confirmNewPassphrase: '' });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function rotateKeys(event) {
    event.preventDefault();
    setMessage('');
    setError('');
    if (form.newPassphrase !== form.confirmNewPassphrase) {
      setError('The new 14-word strings do not match.');
      return;
    }
    if (!confirm('This will re-encrypt sensitive fields using the new 14-word string. When it completes, restart every backend container with FIELD_CRYPTO_PASSPHRASE set to the new string. Continue?')) return;
    setBusy(true);
    try {
      const result = await apiFetch('/api/admin/crypto/rotate', {
        method: 'POST',
        body: JSON.stringify({ oldPassphrase: form.oldPassphrase, newPassphrase: form.newPassphrase })
      });
      setMessage(`Rotation ${result.status}: ${result.usersRotated} users, ${result.officesRotated} offices, ${result.appointmentsRotated} appointments and ${result.notesRotated} notes re-encrypted. Restart backend containers with the new 14-word string.`);
      setForm({ oldPassphrase: '', newPassphrase: '', confirmNewPassphrase: '' });
    } catch (err) {
      setError('Could not rotate keys. It may already have been run, the old 14-word string may be wrong, or some old data cannot be decrypted.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card border-0 shadow-sm">
      <div className="card-header bg-white py-3"><h2 className="h5 fw-bold mb-0">Rotate 14-word field key</h2></div>
      <div className="card-body">
        <p className="text-secondary small">This rotates encrypted database fields from the old 14-word string to a new one. A rotation record is written first, so the same old→new rotation is refused if someone tries to run it again.</p>
        {message && <div className="alert alert-success small">{message}</div>}
        {error && <div className="alert alert-danger small">{error}</div>}
        <form onSubmit={rotateKeys}>
          <label className="form-label">Old 14-word string</label>
          <textarea className="form-control mb-2" rows="3" value={form.oldPassphrase} onChange={event => setForm({ ...form, oldPassphrase: event.target.value })} required />
          <label className="form-label">New 14-word string</label>
          <textarea className="form-control mb-2" rows="3" value={form.newPassphrase} onChange={event => setForm({ ...form, newPassphrase: event.target.value })} required />
          <label className="form-label">Confirm new 14-word string</label>
          <textarea className="form-control mb-3" rows="3" value={form.confirmNewPassphrase} onChange={event => setForm({ ...form, confirmNewPassphrase: event.target.value })} required />
          <button className="btn btn-warning w-100" disabled={busy}>{busy ? 'Rotating...' : 'Rotate field-encryption key'}</button>
        </form>
        <div className="alert alert-secondary small mt-3 mb-0">Do not leave old/new strings in env files or browser history. After success, update FIELD_CRYPTO_PASSPHRASE and restart all backend containers.</div>
      </div>
    </div>
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
  const blankForm = useMemo(() => ({ username: '', password: '', email: '', officeId: 'goole', displayName: '', telephone: '', roles: ['PATIENT'] }), []);
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

            <div className="col-md-2">
              <label className="form-label">Proposed email</label>
              <input className="form-control" type="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} />
            </div>

            <div className="col-md-2">
              <label className="form-label">Actual name</label>
              <input className="form-control" value={form.displayName} onChange={event => setForm({ ...form, displayName: event.target.value })} />
            </div>

            <div className="col-md-2">
              <label className="form-label">Telephone</label>
              <input className="form-control" value={form.telephone} onChange={event => setForm({ ...form, telephone: event.target.value })} />
            </div>

            <div className="col-md-2">
              <label className="form-label">Office ID</label>
              <input className="form-control" value={form.officeId} onChange={event => setForm({ ...form, officeId: event.target.value })} placeholder="goole" />
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
              <thead><tr><th>Username</th><th>Actual name / telephone</th><th>Email</th><th>Office</th><th>Patient/office roles</th><th>New password</th><th className="text-end">Actions</th></tr></thead>
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
        <div>{user.displayName || <span className="text-secondary">No actual name</span>}</div>
        {user.telephone && <div className="small text-secondary">{user.telephone}</div>}
      </td>
      <td>
        <div>{user.email ? user.email : <span className="text-secondary">No verified email</span>}</div>
        {user.emailVerified && <span className="badge text-bg-success">verified</span>}
        {user.pendingEmail && <div className="small text-warning">Pending: {user.pendingEmail}</div>}
        <div className="input-group input-group-sm mt-2">
          <input className="form-control" type="email" placeholder="Propose email" value={proposedEmail} onChange={event => setProposedEmail(event.target.value)} />
          <button className="btn btn-outline-secondary" onClick={sendVerification} disabled={!proposedEmail.trim()}>Verify</button>
        </div>
      </td>
      <td>{user.officeId ? <span className="badge text-bg-light border">{user.officeId}</span> : <span className="text-secondary">Global / none</span>}</td>
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
