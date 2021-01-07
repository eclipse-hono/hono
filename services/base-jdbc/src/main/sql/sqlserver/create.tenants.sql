IF OBJECT_ID('tenants', 'U') IS NULL
CREATE TABLE tenants
(
    TENANT_ID NVARCHAR(36) NOT NULL,
    VERSION   VARCHAR(36) NOT NULL,
    DATA      NTEXT,

    PRIMARY KEY (TENANT_ID)
);

IF OBJECT_ID('tenant_trust_anchors', 'U') IS NULL
CREATE TABLE tenant_trust_anchors
(
    SUBJECT_DN NVARCHAR(256) NOT NULL,
    TENANT_ID  NVARCHAR(36)  NOT NULL,
    DATA       NTEXT,

    PRIMARY KEY (SUBJECT_DN),
    FOREIGN KEY (TENANT_ID) REFERENCES tenants (TENANT_ID) ON DELETE CASCADE
);

-- create indexes for non-primary key access paths

CREATE INDEX idx_tenant_trust_anchors_tenant ON tenant_trust_anchors (TENANT_ID);
