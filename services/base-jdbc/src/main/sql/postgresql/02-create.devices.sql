CREATE TABLE IF NOT EXISTS device_registrations
(
    TENANT_ID VARCHAR(36) NOT NULL,
    DEVICE_ID VARCHAR(256) NOT NULL,
    VERSION   CHAR(36)     NOT NULL,

    CREATED TIMESTAMP NOT NULL,
    UPDATED_ON TIMESTAMP,

    AUTO_PROVISIONED BOOLEAN,
    AUTO_PROVISIONING_NOTIFICATION_SENT BOOLEAN,

    DATA      JSONB,

    PRIMARY KEY (TENANT_ID, DEVICE_ID)
);

CREATE TABLE IF NOT EXISTS device_credentials
(
    TENANT_ID VARCHAR(36) NOT NULL,
    DEVICE_ID VARCHAR(256) NOT NULL,

    TYPE      VARCHAR(64)  NOT NULL,
    AUTH_ID   VARCHAR(256) NOT NULL,

    DATA      JSONB,

    PRIMARY KEY (TENANT_ID, TYPE, AUTH_ID),
    FOREIGN KEY (TENANT_ID, DEVICE_ID) REFERENCES device_registrations (TENANT_ID, DEVICE_ID) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_groups
(
    TENANT_ID VARCHAR(36) NOT NULL,
    DEVICE_ID VARCHAR(256) NOT NULL,
    GROUP_ID  VARCHAR(256) NOT NULL,

    PRIMARY KEY (TENANT_ID, DEVICE_ID, GROUP_ID),
    FOREIGN KEY (TENANT_ID, DEVICE_ID) REFERENCES device_registrations (TENANT_ID, DEVICE_ID) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_states
(
    TENANT_ID           VARCHAR(36) NOT NULL,
    DEVICE_ID           VARCHAR(256) NOT NULL,

    LAST_KNOWN_GATEWAY  VARCHAR(256),
    ADAPTER_INSTANCE_ID VARCHAR(256),

    PRIMARY KEY (TENANT_ID, DEVICE_ID)
);

-- create indexes for non-primary key access paths

CREATE INDEX idx_device_registrations_tenant ON device_registrations (TENANT_ID);

CREATE INDEX idx_device_credentials_tenant ON device_credentials (TENANT_ID);
CREATE INDEX idx_device_credentials_tenant_and_device ON device_credentials (TENANT_ID, DEVICE_ID);

CREATE INDEX idx_device_states_tenant ON device_states (TENANT_ID);

CREATE INDEX idx_device_member_of_tenant ON device_groups (TENANT_ID);
CREATE INDEX idx_device_member_of_tenant_and_device ON device_groups (TENANT_ID, DEVICE_ID);
