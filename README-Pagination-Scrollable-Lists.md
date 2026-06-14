# Pagination and scrollable 50-row lists

This build changes the long clinical/admin lists to backend pagination with a fixed page size of 50 rows.

## Backend

The following list endpoints now return a `PageResponse<T>` object instead of a raw JSON array:

- `GET /api/patient/appointments?page=0`
- `GET /api/office/appointments?page=0`
- `GET /api/office-admin/appointments?page=0`
- `GET /api/hq/offices?page=0`
- `GET /api/admin/users?page=0`

The page size is deliberately fixed at 50 server-side to avoid accidental huge responses.

Response shape:

```json
{
  "content": [],
  "page": 0,
  "size": 50,
  "totalElements": 123,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

## Frontend

The UI unwraps `content`, displays up to 50 rows, and shows First / Previous / Next / Last controls. The visible list area is scrollable using `scrollable-list-50`, so 50 rows do not have to fit on the screen at once.

Changed screens:

- Patient appointments
- Office clinician appointment list
- Office admin appointment list
- HQ office list
- SUPER user administration list
