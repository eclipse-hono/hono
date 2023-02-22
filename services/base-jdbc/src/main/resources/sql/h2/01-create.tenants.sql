CREATE TABLE IF NOT EXISTS tenants
(
    TENANT_ID VARCHAR(256) NOT NULL,
    VERSION   CHAR(36)     NOT NULL,
    DATA      TEXT,

    PRIMARY KEY (TENANT_ID)
);

CREATE TABLE IF NOT EXISTS tenant_trust_anchors
(
    SUBJECT_DN VARCHAR(256) NOT NULL,
    TENANT_ID  VARCHAR(256) NOT NULL,
    DATA       TEXT,

    PRIMARY KEY (SUBJECT_DN),
    FOREIGN KEY (TENANT_ID) REFERENCES tenants (TENANT_ID) ON DELETE CASCADE
);

-- create indexes for non-primary key access paths

CREATE INDEX IF NOT EXISTS idx_tenant_trust_anchors_tenant ON tenant_trust_anchors (TENANT_ID);
