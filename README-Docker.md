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
http://localhost:5173
```

The backend API is exposed through the load balancer at:

```text
http://localhost:8080/ExampleSecurity
```

## Default login

```text
username: super
password: ChangeThisPassword123!
```

## Local MongoDB

The included `env.list` uses:

```text
MONGODB_URI=mongodb://mongo:27017/example_security
```

Session documents are stored in:

```text
spring_sessions
```

## MongoDB Atlas

Replace `MONGODB_URI` in `env.list` with your Atlas connection string, for example:

```text
MONGODB_URI=mongodb+srv://USERNAME:PASSWORD@cluster.example.mongodb.net/example_security?retryWrites=true&w=majority
```

For Atlas-only use, you may remove the `mongo` service from `docker-compose.yml`.

## Ports

Only the load balancer publishes ports to the host:

```text
frontend load balancer: http://localhost:5173
backend load balancer:  http://localhost:8080/ExampleSecurity
mailpit UI:             http://localhost:8025
mongo local port:       localhost:27017
```

The individual frontend/backend replicas are internal Docker services.
