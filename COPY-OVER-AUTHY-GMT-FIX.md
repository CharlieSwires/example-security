# Authy/TOTP UTC clock fix - copy-over instructions

Copy the files in this ZIP over the matching paths in the `example-security`
repository, preserving the directory structure.

## What changed

- `MfaService` now uses an injected `Clock.systemUTC()` for RFC 6238 TOTP.
- The backend container and JVM are explicitly pinned to UTC.
- All three Compose modes pass `TZ=Etc/UTC` to the backend.
- Regression tests cover the UK BST-to-GMT transition on 25 October 2026.
- A one-hour acceptance window was deliberately not introduced because it
  would materially weaken MFA.
- `README-Authy-MFA.md` now contains clock diagnostics and recovery steps.

## Apply and restart

For the default local build:

```bash
docker compose down
docker compose up --build -d
```

Then compare UTC time on the host and backend:

```bash
date -u
docker compose exec backend date -u
docker compose exec backend java -XshowSettings:properties -version 2>&1 | grep user.timezone
```

The UTC times must agree to within a few seconds and the JVM must report UTC.
On Windows, if the container remains an hour wrong, quit Docker Desktop, run
`wsl --shutdown`, restart Docker Desktop and recreate the containers.

Also enable automatic date/time and automatic time zone on the phone running
Authy.
