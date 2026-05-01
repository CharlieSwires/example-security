# Docker usage

This adds container support for the `example-security` project.

## Files added

```text
example-security/
├── .dockerignore
├── docker-compose.yml
├── backend/
│   └── Dockerfile
└── frontend/
    ├── Dockerfile
    └── nginx.conf
```

## Run everything locally

From the `example-security` folder:

```bash
docker compose up --build
```

Then open:

```text
http://localhost:5173
```

The backend runs at:

```text
http://localhost:8080/ExampleSecurity
```

## Default login

```text
username: super
password: ChangeThisPassword123!
```

## Using local MongoDB

By default, Docker Compose starts a local MongoDB container and uses:

```text
mongodb://mongo:27017/example_security
```

## Using MongoDB Atlas instead

Set `MONGODB_URI` before starting Docker Compose.

### Windows PowerShell

```powershell
$env:MONGODB_URI="mongodb+srv://USERNAME:PASSWORD@cluster.example.mongodb.net/example_security?retryWrites=true&w=majority"
docker compose up --build
```

### Git Bash / Linux / macOS

```bash
export MONGODB_URI='mongodb+srv://USERNAME:PASSWORD@cluster.example.mongodb.net/example_security?retryWrites=true&w=majority'
docker compose up --build
```

You can remove the `mongo` service from `docker-compose.yml` if you only use MongoDB Atlas.

## Build just the backend

```bash
cd backend
docker build -t example-security-backend .
docker run -p 8080:8080 -e MONGODB_URI="mongodb://host.docker.internal:27017/example_security" example-security-backend
```

## Build just the frontend

```bash
cd frontend
docker build -t example-security-frontend --build-arg VITE_API_BASE=http://localhost:8080/ExampleSecurity .
docker run -p 5173:80 example-security-frontend
```

## HTTPS note

This Docker setup runs HTTP locally for development.

For deployment, put the containers behind a TLS-terminating reverse proxy or load balancer such as Nginx, Traefik, Caddy, AWS ALB, Azure Application Gateway, Cloudflare, etc.

That way the browser-to-server password POST is protected by TLS.
