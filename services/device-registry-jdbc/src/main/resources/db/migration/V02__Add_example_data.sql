INSERT INTO tenants (tenant_id, version, data)
VALUES ('DEFAULT_TENANT', 'initial-version', '{"enabled":true}'),
       ('HTTP_TENANT', 'initial-version', '{"enabled":true,"adapters":[{"type":"hono-http","enabled":true,"device-authentication-required":true},{"type":"hono-mqtt","enabled":false,"device-authentication-required":true},{"type":"hono-kura","enabled":false,"device-authentication-required":true},{"type":"hono-coap","enabled":false,"device-authentication-required":true}]}');

INSERT INTO tenant_trust_anchors (subject_dn, tenant_id, data)
VALUES ('CN=DEFAULT_TENANT_CA,OU=Hono,O=Eclipse IoT,L=Ottawa,C=CA', 'DEFAULT_TENANT', '{"id":"a5efdaf5-e13b-4f00-97f4-6e516148bffa","public-key":"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAElkwCSPlO563eQb6ONdULAISm2XngGGSoAAz+I1s8zkS9guPUpNKoxeczLtKlObelHqBgIZtRXdrPRgXidGOnmQ==","algorithm":"EC","not-before":"2019-09-18T08:35:40Z","not-after":"2020-09-17T08:35:40Z","auto-provisioning-enabled":false}');

INSERT INTO device_registrations (tenant_id, device_id, version, created, auto_provisioned, data)
VALUES ('DEFAULT_TENANT', '4711', 'initial-version', CURRENT_TIMESTAMP(), FALSE, '{"enabled":true,"defaults":{"content-type":"application/vnd.bumlux","importance":"high"}}'),
       ('DEFAULT_TENANT', '4712', 'initial-version', CURRENT_TIMESTAMP(), FALSE, '{"enabled":true,"via":["gw-1"]}'),
       ('DEFAULT_TENANT', 'gw-1', 'initial-version', CURRENT_TIMESTAMP(), FALSE, '{"enabled":true}');

INSERT INTO device_credentials (tenant_id, device_id, type, auth_id, data)
VALUES ('DEFAULT_TENANT', '4711', 'hashed-password', 'sensor1', '{"type":"hashed-password","auth-id":"sensor1","secrets":[{"id":"38784caf-83de-4008-a9fe-98687103ad29","not-before":"2017-05-01T13:00:00Z","not-after":"2037-06-01T13:00:00Z","comment":"pwd: hono-secret","hash-function":"bcrypt","pwd-hash":"$2a$10$N7UMjhZ2hYx.yuvW9WVXZ.4y33mr6MvnpAsZ8wgLHnkamH2tZ1jD."}],"enabled":true}'),
       ('DEFAULT_TENANT', '4711', 'psk', 'sensor1', '{"type":"psk","auth-id":"sensor1","secrets":[{"id":"ab3db625-ca16-4469-9300-75eee7a322f1","not-before":"2017-12-31T23:00:00Z","not-after":"2037-06-01T13:00:00Z","comment":"key: hono-secret","key":"aG9uby1zZWNyZXQ="}],"enabled":true}'),
       ('DEFAULT_TENANT', '4711', 'x509-cert', 'CN=Device 4711,OU=Hono,O=Eclipse IoT,L=Ottawa,C=CA', '{"type":"x509-cert","auth-id":"CN=Device 4711,OU=Hono,O=Eclipse IoT,L=Ottawa,C=CA","secrets":[{"id":"a38a313c-5dda-49eb-a009-b0f1f343f93a","comment":"The secrets array must contain an object, which can be empty."}],"enabled":true}'),
       ('DEFAULT_TENANT', 'gw-1', 'hashed-password', 'gw', '{"type":"hashed-password","auth-id":"gw","secrets":[{"id":"7d1fbda2-0f48-4c25-a0c3-1591df939aab","not-before":"2017-12-31T23:00:00Z","not-after":"2037-06-01T13:00:00Z","comment":"pwd: gw-secret","hash-function":"bcrypt","pwd-hash":"$2a$10$GMcN0iV9gJV7L1sH6J82Xebc1C7CGJ..Rbs./vcTuTuxPEgS9DOa6"}],"enabled":true}'),
       ('DEFAULT_TENANT', 'gw-1', 'psk', 'gw', '{"type":"psk","auth-id":"gw","secrets":[{"id":"b1004571-7d69-4f1f-92aa-ffdcb0d2e992","not-before":"2017-12-31T23:00:00Z","not-after":"2037-06-01T13:00:00Z","comment":"key: gw-secret","key":"Z3ctc2VjcmV0"}],"enabled":true}');
