# HQ office management and SUPER office context

This build adds office accounts and a SUPER office selector.

## Office accounts

HQ and SUPER can call:

- `GET /api/hq/offices`
- `POST /api/hq/offices`
- `DELETE /api/hq/offices/{officeId}`

An office account contains:

- office id, stored in plaintext for routing/access checks
- office login username
- PBKDF2 password hash and salt
- display name
- encrypted address
- encrypted telephone
- email address

The office id remains plaintext because patient and appointment ownership checks need to filter by office efficiently.

## UI changes

The HQ tab now has an Add Office form and office list.

SUPER users get an active office selector at the top of the dashboard. When a SUPER user opens the Office or Office Admin tabs, the selected office id is passed as `officeId=...` to the backend so the lists and patient lookup are scoped to that office.

Normal OFFICE and OFFICE_ADMIN users remain restricted to their own `officeId` from the user record.
