# Docker frontend build fix

The frontend Docker build context is `frontend/`, so the root `.dockerignore` is not used by Docker for that build.

This zip adds `frontend/.dockerignore` so local `node_modules` and `dist` are not copied into the Docker image.

Without this, a Windows/Git Bash `frontend/node_modules` folder can overwrite the Linux container's freshly installed `node_modules` during:

```dockerfile
COPY . .
```

That can cause errors like:

```text
Error [ERR_MODULE_NOT_FOUND]: Cannot find module '/app/node_modules/dist/node/cli.js' imported from /app/node_modules/.bin/vite
```

Run cleanly:

```bash
docker compose down --volumes --remove-orphans
docker builder prune -f
docker compose build --no-cache frontend
docker compose up --build --force-recreate --scale backend=2 --scale frontend=2
```
