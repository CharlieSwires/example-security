# Google sign-in (OAuth 2.0 / OpenID Connect)

The application supports optional **Sign in with Google** for existing users. Google identifies the
person; MongoDB remains authoritative for the username, office and roles. A Google account cannot
create a user or grant itself access.

## Security model

- A user first signs in with the application password.
- The user must have a verified application email address.
- In **Multi-factor authentication / Google sign-in**, the user chooses **Link Google account**.
- The Google account's verified email must exactly match the verified application email.
- The immutable Google OpenID Connect `sub` claim is stored with a unique sparse MongoDB index.
- No Google access token or refresh token is retained; the application does not call Google APIs.
- Existing application MFA is still required after Google authentication when MFA is enabled.
- Roles and `officeId` are always loaded from the local `AppUser`; Google claims never assign access.
- Linking, unlinking, successful login, MFA continuation and rejected attempts are audited.

## Google Cloud setup

1. Create or select a Google Cloud project.
2. Configure the OAuth consent/branding screen.
3. Create an OAuth client of type **Web application**.
4. Add the production authorised redirect URI exactly as:

   `https://YOUR-DOMAIN/ExampleSecurity/login/oauth2/code/google`

5. For direct local backend development, add:

   `https://localhost:8080/ExampleSecurity/login/oauth2/code/google`

The scheme, host, port, context path and letter case must match exactly. Behind the Krystal load
balancer, retain the existing `Host`, `X-Forwarded-Host` and `X-Forwarded-Proto: https` forwarding.

## Backend environment

Add these values to `/etc/example-security/backend.env` on every backend node:

```properties
GOOGLE_OAUTH_ENABLED=true
GOOGLE_OAUTH_CLIENT_ID=replace-with-google-web-client-id
GOOGLE_OAUTH_CLIENT_SECRET=replace-with-google-web-client-secret
```

Restart/redeploy both backend nodes. The client ID is not a password, but the client secret must not
be committed to Git, included in a frontend build, or written to a public Compose file.

To disable Google login without removing any existing links, set `GOOGLE_OAUTH_ENABLED=false` and
restart the backend. The client ID and secret may be blank while Google login is disabled; password
and Authy/recovery-code login continue to work. When Google login is enabled, startup deliberately
fails with a clear configuration error if either credential is missing.

## Linking and use

1. Ensure the user's application email shows as verified.
2. Sign in with username/password.
3. Open the Security screen and choose **Link Google account**.
4. Select the Google account with the same email address.
5. Sign out. The login page now offers **Sign in with Google**.

An unlinked Google account receives a generic rejection and cannot enter the application. The user
can unlink Google from the same Security screen; password login is not affected.

## Operational notes

- `spring_sessions` must remain shared by all backend instances because the OAuth authorisation
  request and MFA continuation are stored server-side during the redirect flow.
- Use `SESSION_COOKIE_SAME_SITE=lax` in production. Google's top-level callback works with Lax;
  `Strict` can break the redirect session.
- Do not enable Google login until HTTPS and the public callback URI are correct.
- If a user's verified application email changes, an existing Google link remains tied to the
  immutable Google subject. Unlink and relink deliberately if the Google identity must change.
