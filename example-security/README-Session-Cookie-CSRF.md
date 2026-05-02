# Session cookie + CSRF version

This version replaces the earlier Basic Auth/browser-stored-password design.

## What changed

- `POST /ExampleSecurity/api/login` still receives username/password.
- The backend verifies the password using the existing PBKDF2 salted hash.
- On success, Spring Security creates a server-side HTTP session.
- The browser receives a `JSESSIONID` cookie.
- The cookie is HttpOnly, so React/JavaScript cannot read it.
- React no longer stores the password in `sessionStorage`.
- React only stores a small display object: username and roles.
- API calls use `credentials: 'include'`.
- CSRF protection is enabled.
- React gets a CSRF token from `GET /ExampleSecurity/api/csrf` and sends it as `X-XSRF-TOKEN`.

## Local development

For local HTTP development, use:

```text
SESSION_COOKIE_SECURE=false
SESSION_COOKIE_SAME_SITE=lax
```

For HTTPS deployment, use:

```text
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=strict
```

If frontend and backend are on different HTTPS sites, `SameSite=strict` may be too restrictive. Use `lax` or `none` only with care. `SameSite=None` requires `Secure=true`.

## Important browser cleanup

The older app stored credentials under:

```text
example-security-session
```

This version removes that key, but if the page behaves oddly, clear browser session storage:

```javascript
sessionStorage.removeItem('example-security-session');
sessionStorage.removeItem('example-security-user');
location.reload();
```

## Run

```bash
cp backend/env.list.example backend/env.list
docker compose down
docker compose up --build
```


## Logout and unauthenticated /api/me fix

This ZIP includes two small fixes:

- unauthenticated `GET /ExampleSecurity/api/me` now returns `401 Unauthorized` instead of an awkward `403`.
- `POST /ExampleSecurity/api/logout` now returns `204 No Content` instead of redirecting the browser to `/ExampleSecurity/login?logout`.

That removes the DevTools errors caused by Spring Security's default logout redirect.
