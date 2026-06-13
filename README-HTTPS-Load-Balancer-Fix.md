# HTTPS load-balancer fix

This copy fixes the frontend load balancer problem that caused either:

- `502 Bad Gateway`, or
- `ERR_CONNECTION_REFUSED` on `localhost:5173`.

## What changed

1. The frontend Docker image is now a normal production-style nginx image serving the Vite build on port `80`.
2. The old broken `CMD ["npm", "run", "dev"]` was removed from the final nginx image. That command made the frontend container exit because the final image did not contain npm.
3. The load balancer now exposes HTTPS on:

```text
https://localhost:5173
```

4. The backend API load balancer exposes HTTPS on:

```text
https://localhost:8080/ExampleSecurity
```

5. TLS is terminated at the load balancer. Internally Docker uses HTTP:

```text
Browser -> HTTPS -> load-balancer -> HTTP -> frontend/backend containers
```

6. Spring Session still stores login session state in MongoDB/Atlas, so duplicate backend containers can share sessions.

## Run

```bash
docker compose down
docker compose up --build --scale backend=2 --scale frontend=2
```

Then open:

```text
https://localhost:5173
```

Important: include `https://`. If you type only `localhost:5173`, Chrome may try plain HTTP.

## Certificate warning

The zip includes a local self-signed certificate in `certs/` for localhost. Chrome will probably warn that it is not trusted. For local development, click through the advanced warning, or import the certificate into your local trust store.

## Mailpit vs Gmail

The included `env.list` uses Mailpit by default. Emails appear here:

```text
http://localhost:8025
```

To use Gmail SMTP, edit `env.list` and use a Google App Password:

```env
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your.address@gmail.com
MAIL_PASSWORD=your16charactergoogleapppassword
MAIL_FROM=your.address@gmail.com
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
MAIL_DEBUG=true
```

Then restart Docker.

## Check logs

```bash
docker compose ps
docker compose logs load-balancer --tail=100
docker compose logs frontend --tail=100
docker compose logs backend --tail=200
```
