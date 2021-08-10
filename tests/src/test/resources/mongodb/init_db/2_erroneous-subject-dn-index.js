db.tenants.createIndex(
  {
    "tenant.trusted-ca.subject-dn": 1
  },
  {
    unique: true,
    partialFilterExpression: {
      "tenant.trusted-ca": { $exists: true }
    }
  }
);
