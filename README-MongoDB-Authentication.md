# MongoDB Atlas setup

The application uses MongoDB Atlas only. No MongoDB container or database port
is included in either Compose deployment.

1. Create an Atlas cluster and an application database user with read/write
   access to the `example_security` database.
2. Add the public outbound IP of each machine that runs the backend to the Atlas
   project IP access list. Do not use `0.0.0.0/0` for production.
3. Copy the Atlas Drivers connection string into `MONGODB_URI`, include the
   `/example_security` database path, and URL-encode reserved characters in the
   password.
4. Leave `REQUIRE_EXTERNAL_SERVICES=true` enabled.

```properties
MONGODB_URI=mongodb+srv://USERNAME:URL_ENCODED_PASSWORD@YOUR_CLUSTER.mongodb.net/example_security?retryWrites=true&w=majority
```

Atlas manages the replica set. Configure backups and periodically test a
restore in Atlas; the Krystal host no longer owns a MongoDB volume.
