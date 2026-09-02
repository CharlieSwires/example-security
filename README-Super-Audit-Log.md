# SUPER audit log screen

The SUPER administration screen now displays the persisted MongoDB security audit log while preserving the existing Authy MFA implementation.

## Access control

The React component exists only inside the existing SUPER screen. More importantly, the backend endpoint is `GET /api/admin/audit-events`, so the existing Spring Security `/api/admin/**` rule enforces `ROLE_SUPER`. Hiding the UI is not relied on for security.

## Ordering and paging

Events are returned newest first, sorted by `timestamp` descending and then MongoDB `id` descending for deterministic ordering. The endpoint returns 50 records per page using the project's existing `PageResponse` format.

## Displayed fields

All fields from the `security_audit_events` collection are visible:

- MongoDB ID
- timestamp
- event type
- actor
- target
- client IP
- user agent
- success
- reason
- details map

The table scrolls horizontally and vertically, uses a sticky header, and wraps long user-agent, details and ID values rather than truncating them. A Refresh button reloads the current page.

Audit persistence must remain enabled with `SECURITY_AUDIT_PERSIST=true` for new events to appear.
