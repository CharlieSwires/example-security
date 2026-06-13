# Load balanced frontend/backend with MongoDB-backed sessions

This version externalises the login/session state into MongoDB using Spring Session MongoDB.
That means you can run more than one backend container behind a load balancer without sticky sessions.

## What is stored in MongoDB / Atlas

- Users, roles, email verification state and password-reset state were already MongoDB documents.
- Login throttling and audit events were already MongoDB documents.
- HTTP sessions are now stored in the `spring_sessions` collection by default.
- The browser still holds only the `JSESSIONID` cookie and the CSRF cookie. The actual Spring Security authentication context is shared through MongoDB.

## Local run with duplicate containers

Create or edit `env.list` in the project root:

```text
MONGODB_URI=mongodb://mongo:27017/example_security
INITIAL_SUPER_USERNAME=super
INITIAL_SUPER_PASSWORD=ChangeThisPassword123!
CORS_ALLOWED_ORIGINS=http://localhost:5173
FRONTEND_BASE_URL=http://localhost:5173
BACKEND_BASE_URL=http://localhost:8080/ExampleSecurity
SESSION_COOKIE_SECURE=false
SESSION_COOKIE_SAME_SITE=lax
```

Then run two frontends and two backends behind Nginx:

```bash
docker compose up --build --scale backend=2 --scale frontend=2
```

Open:

```text
http://localhost:5173
```

The frontend load balancer listens on `5173`.
The backend API load balancer listens on `8080`.

## Using MongoDB Atlas

In `env.list`, replace the local MongoDB URI with your Atlas URI:

```text
MONGODB_URI=mongodb+srv://USERNAME:PASSWORD@cluster.example.mongodb.net/example_security?retryWrites=true&w=majority
```

For a pure Atlas setup, you can remove or ignore the local `mongo` service.

## Production notes

For a real deployment, terminate HTTPS at a proper load balancer such as AWS ALB, Azure Application Gateway, Caddy, Traefik, Cloudflare or an Nginx reverse proxy with real certificates. Then set:

```text
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=lax
```

Because sessions are in MongoDB, sticky sessions are not required, although sticky sessions are still harmless.

## CPU and Java threading

You do not need one virtual processor per container. Java is multithreaded, and each backend JVM can handle many simultaneous HTTP requests using its request thread pool. Multiple containers are mainly useful for:

- resilience: one container can restart while another keeps serving;
- rolling deployments;
- spreading load across CPU cores or machines;
- isolating memory failures.

On a small machine, start with one frontend and one backend. On a 4-core machine, two backend containers is a sensible first test. Do not create more JVM containers than the CPU and RAM can support, because each JVM has its own heap and overhead.
