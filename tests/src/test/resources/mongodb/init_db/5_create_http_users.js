db.registryusers.createIndex( { login: 1 }, { unique: true } );
// alllow user "test-client" with password "secret" to access the registry's HTTP API endpoint
db.registryusers.insertOne( {
  login: "${hono.deviceregistry.http.authConfig.username}",
  spice: "${hono.deviceregistry.http.authConfig.salt}",
  pwdHash: "${hono.deviceregistry.http.authConfig.passwordHash}"
} );
