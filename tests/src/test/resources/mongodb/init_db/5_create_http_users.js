db.registryusers.createIndex( { login: 1 }, { unique: true } );
// alllow the user defined by the maven properties in the pom.xml to access the registry's HTTP API endpoint
db.registryusers.insertOne( {
  login: "${hono.deviceregistry.http.authConfig.username}",
  spice: "${hono.deviceregistry.http.authConfig.salt}",
  pwdHash: "${hono.deviceregistry.http.authConfig.passwordHash}"
} );
