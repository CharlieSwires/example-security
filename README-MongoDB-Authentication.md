# MongoDB authentication and network isolation

The default Compose deployment no longer publishes port 27017 to the host.
MongoDB is reachable only by containers on the private Compose network and now
requires authentication.

Before starting a new deployment, copy `env.list.example` to `env.list` and
replace both example passwords with different long, random URL-safe values. The
application connects with the least-privileged `MONGO_APP_USERNAME`; the root
account is reserved for database administration.

The initialization script runs only for an empty MongoDB data volume. If this is
an existing deployment, do not simply enable authentication and restart: first
take and verify a backup, then create the root and application users while the
old database is still reachable. Alternatively, restore the verified dump into
a newly initialized authenticated volume.

For backups, run `mongodump` inside the MongoDB container or connect through an
SSH tunnel. Do not publish 27017 on the public VPS. If local Compass access is
temporarily required, use a development-only override binding it to loopback:

```yaml
services:
  mongo:
    ports:
      - "127.0.0.1:27017:27017"
```

Remove that override when it is no longer needed.
