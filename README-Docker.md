# Docker usage

This project now supports duplicate frontend and backend containers behind a local Nginx load balancer.
Backend login/session state is stored in MongoDB using Spring Session, so the containers do **not** need sticky sessions.

## Run locally

From the `example-security` folder:

```bash
docker compose up --build --scale backend=2 --scale frontend=2
```

Then open:

```text
https://localhost:5173
```

The backend API is exposed through the load balancer at:

```text
https://localhost:8080/ExampleSecurity
```

## Initial login

```text
username: value of INITIAL_SUPER_USERNAME
password: value of INITIAL_SUPER_PASSWORD
```

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

Only the load balancer publishes ports to the host:

```text
frontend load balancer: https://localhost:5173
backend load balancer:  https://localhost:8080/ExampleSecurity
```

The individual frontend/backend replicas are internal Docker services.
See [README-MongoDB-Authentication.md](README-MongoDB-Authentication.md) for
Atlas setup and network access.
