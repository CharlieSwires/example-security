# Docker usage

The default build runs one frontend and one backend without a separate Nginx
load-balancer container. The frontend's Nginx serves React, terminates local
HTTPS and proxies the API. Replica mode adds the load-balancer automatically.
Backend login/session state is stored in Atlas using Spring Session, so replicas
do **not** need sticky sessions.

## Run locally

From the `example-security` folder:

```bash
./scripts_docker_compose_up.sh
```

Then open:

```text
https://localhost:5173
```

The backend API is proxied by the frontend Nginx at:

```text
https://localhost:8080/ExampleSecurity
```

## Initial login

```text
username: value of INITIAL_SUPER_USERNAME
password: value of INITIAL_SUPER_PASSWORD
```

## Replica mode

Pass backend and frontend replica counts to the launcher:

```bash
./scripts_docker_compose_up.sh 2 2
```

If either count is greater than one, the launcher uses
`docker-compose.scaled.yml`. Only then is the separate Nginx load-balancer
started. Running `docker compose up --scale ...` directly against the default
file is intentionally unsupported because every replica would contend for the
same host ports.

## Required external services

Create `env.list` from `env.list.example`. Docker Compose does not run MongoDB
or Mailpit. Set an Atlas application-user URI:

```text
MONGODB_URI=mongodb+srv://USERNAME:URL_ENCODED_PASSWORD@YOUR_CLUSTER.mongodb.net/example_security?retryWrites=true&w=majority
```

Session documents are stored in:

```text
spring_sessions
```

Also set all `MAIL_*` values for an authenticated external SMTP service. The
backend fails startup if the URI is not `mongodb+srv://`, if SMTP points to
Mailpit/localhost, or if required values still contain placeholders.

## Ports

In default mode the single frontend Nginx publishes both browser-facing ports.
In replica mode only the load-balancer publishes them:

```text
frontend: https://localhost:5173
backend:  https://localhost:8080/ExampleSecurity
```

In replica mode the individual frontend/backend containers remain internal
Docker services.
See [README-MongoDB-Authentication.md](README-MongoDB-Authentication.md) for
Atlas setup and network access.
