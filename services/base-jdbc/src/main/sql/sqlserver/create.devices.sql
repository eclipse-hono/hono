IF OBJECT_ID('device_registrations', 'U') IS NULL
CREATE TABLE device_registrations
(
    TENANT_ID NVARCHAR(36) NOT NULL,
    DEVICE_ID NVARCHAR(256) NOT NULL,
    VERSION   VARCHAR(36) NOT NULL,

    CREATED DATETIME2 NOT NULL,
    UPDATED_ON DATETIME2,

    AUTO_PROVISIONED BIT,
    AUTO_PROVISIONING_NOTIFICATION_SENT BIT,

    DATA      NTEXT,

    PRIMARY KEY (TENANT_ID, DEVICE_ID)
);

IF OBJECT_ID('device_credentials', 'U') IS NULL
CREATE TABLE device_credentials
(
    TENANT_ID NVARCHAR(36) NOT NULL,
    DEVICE_ID NVARCHAR(256) NOT NULL,

    TYPE      NVARCHAR(64)  NOT NULL,
    AUTH_ID   NVARCHAR(256) NOT NULL,

    DATA      NTEXT,

    PRIMARY KEY (TENANT_ID, TYPE, AUTH_ID),
    FOREIGN KEY (TENANT_ID, DEVICE_ID) REFERENCES device_registrations (TENANT_ID, DEVICE_ID) ON DELETE CASCADE
);

IF OBJECT_ID('device_groups', 'U') IS NULL
CREATE TABLE device_groups
(
    TENANT_ID NVARCHAR(36) NOT NULL,
    DEVICE_ID NVARCHAR(256) NOT NULL,
    GROUP_ID  NVARCHAR(256) NOT NULL,

    PRIMARY KEY (TENANT_ID, DEVICE_ID, GROUP_ID),
    FOREIGN KEY (TENANT_ID, DEVICE_ID) REFERENCES device_registrations (TENANT_ID, DEVICE_ID) ON DELETE CASCADE
);

IF OBJECT_ID('device_states', 'U') IS NULL
CREATE TABLE device_states
(
    TENANT_ID           NVARCHAR(36) NOT NULL,
    DEVICE_ID           NVARCHAR(256) NOT NULL,

    LAST_KNOWN_GATEWAY  NVARCHAR(256),
    ADAPTER_INSTANCE_ID NVARCHAR(256),

    PRIMARY KEY (TENANT_ID, DEVICE_ID)
);

-- create indexes for non-primary key access paths

CREATE INDEX idx_device_registrations_tenant ON device_registrations (TENANT_ID);

CREATE INDEX idx_device_credentials_tenant ON device_credentials (TENANT_ID);
CREATE INDEX idx_device_credentials_tenant_and_device ON device_credentials (TENANT_ID, DEVICE_ID);

CREATE INDEX idx_device_states_tenant ON device_states (TENANT_ID);

CREATE INDEX idx_device_member_of_tenant ON device_groups (TENANT_ID);
CREATE INDEX idx_device_member_of_tenant_and_device ON device_groups (TENANT_ID, DEVICE_ID);
