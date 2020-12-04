+++
title = "Release Notes"
+++

## 1.4.4

### Fixes & Enhancements

* The HTTP adapter did not properly forward the QoS level for events when the *qos-level* header is not set 
  or set to AT_MOST_ONCE. This has been fixed.
* An HTTP device sending a command response request with no `Content-Type` header meant that the northbound
  application received a message with the content type set to an empty string. Now, the content type property
  isn't set in this case.

## 1.4.3

### Fixes & Enhancements

* The file based as well as the Mongo DB based registry implementations now remove the shared-key from
  PSK credentials returned in the response of the Management API's *get credentials* operation.
* The Device Registry Management API erroneously declared the *plain text password* conveyed in the
  *update credentials* operation's request payload as a Base64 encoded byte array instead of a
  plain string. This has been fixed.
* The file based as well as the Mongo DB based registry implementations had failed to do both
  updating an existing secret (referred to by ID) and adding a new secret to the same credentials
  in a single request. This has been fixed.
* The property names for specifying the patterns for validating Tenant and Device IDs have been fixed
  in the admin guides for the file based and the Mongo DB based registry implementations.
* The registry implementations did not accept X.509 credentials in an update Credentials request.
  They also failed to remove existing credentials of a device if they were not included in an
  update Credentials request. This has been fixed.
* The AMQP protocol adapter did accept messages from clients (devices) exceeding the adapter's configured
  max-message-size. This has been fixed and the adapter now closes the link to the device in this case.

### API Changes

* The default pattern for valid device identifiers used for the file based and the MongoDB based registry
  implementations now also contains a colon (`:`) for compatibility with Eclipse Ditto.

## 1.4.0

### New Features

* The protocol adapters now report connection attempts made by devices
  in a new metric. In particular, the metric includes a tag reflecting the outcome
  of the attempt to establish a connection and the reason for failure.
  Please refer to the [Metrics API]({{% doclink "/api/metrics" %}}) for details.
* A new Quarkus based HTTP protocol adapter is now available. This adapter version provides better memory consumption and
  startup times comparing to the existing one.
* The LoraWAN protocol adapter has been extended with support for the *Actility Enterprise* provider.
* The LoraWAN protocol adapter has been extended with support for the *Orbiwise* provider.
* Added metrics for tracking the RTT between sending an AMQP message to receiving the disposition.
* The CoAP adapter now supports configuration of the *timeoutToAck* parameter at the tenant level
  in addition to the adapter level. If set, the tenant specific value is used for all devices of the
  particular tenant. If not set, the value defined at the adapter level is used.
* The Device Registry Management API has been extended now to support searching devices for a tenant
  with optional filters, paging and sorting options.
  Please refer to the [Device registry management API]({{% doclink "/api/management#/devices/searchDevicesForTenant" %}})
  for details. 
* The MongoDB based device registry now supports searching devices for a tenant with optional filters,
  paging and sorting options.

### Fixes & Enhancements

* The MongoDB based device registry now checks for tenant existence during device registration and credentials management operations.

### Deprecations
 
* The configuration property `singleTenant` of the protocol adapters and the device registry is now deprecated
  and planned to be removed in a future release. The use case of a system with just a single tenant should be
  realized by configuring just one tenant in the device registry.
* The `HONO_REGISTRY_REST_*` configuration properties of the file based device registry have been deprecated
  in favor of corresponding properties with the `HONO_REGISTRY_HTTP_` prefix.

## 1.3.0

### New Features

* The LoraWAN protocol adapter has been extended with support for the *ChirpStack* provider.
* Hono's integration tests can now be run with a Jaeger back end in order to collect tracing
  information.
* The AMQP 1.0 event message based Connection Event producer now sets a TTL on the event messages
  it produces. The TTL is the *max TTL* configured at the tenant level.
* A new Device Registry implementation based on MongoDB database is now available in Hono.
  Please refer to the [MongoDB Device Registry User Guide]({{% doclink "/user-guide/mongodb-based-device-registry" %}})
  for additional information.
* Hono protocol adapters can now be configured to use address rewriting when opening AMQP 1.0 links to the messaging network.
  This allows an easier deployment in multi-tenant messaging environments.
  Please refer to the [Hono Client Configuration Guide]({{% doclink "/admin-guide/hono-client-configuration/#address-rewriting" %}})
  for additional information.

### Fixes & Enhancements

* An admin guide for the CoAP adapter has been added.
* The Prometheus based resource limits checker now supports configuring a query timeout.
  Please refer to [Resource Limits Checker Configuration]({{% doclink "/admin-guide/common-config/#resource-limits-checker-configuration" %}})
  for additional information.
* When sending a command message to a device, the AMQP adapter now waits for a
  configurable period of time (default is 1 second) for the acknowledgement from the device.
  If none is received, the downstream command sender gets back a `released` outcome.
  Please refer to the `sendMessageToDeviceTimeout` property description in the
  AMQP Adapter admin guide for additional information.
* The client for storing device connection information to a data grid now supports configuring
  the name of the cache to store the data in.
* When the connection to a device is closed or lost, a protocol adapter instance will now
  stop listening for commands targeted at the device.
* The AMQP adapter before accepting any connections checks if the connection limit is exceeded 
  or not and if the adapter is enabled or not. These checks are currently done inside the
  `AmqpAdapterSaslAuthenticatorFactory`. Thereby, if any of these checks failed, the AMQP adapter
  reported authentication failure instead of the actual reason. This has been fixed now.
* The AMQP adapter did not consider the `HONO_CONNECTIONEVENTS_PRODUCER` configuration variable
  to set a Connection Event producer. This has been fixed.
* The *logging* Connection Event producer now supports configuring the level
  at which information should be logged. The producer type *none* has been added to explicitly
  turn off connection event reporting altogether.
  Please refer to the *Common Configuration* admin guide for details.
* The packages `org.eclipse.hono.service.credentials`, `org.eclipse.hono.service.management`,
  `org.eclipse.hono.service.management.credentials`, `org.eclipse.hono.service.management.device`,
  `org.eclipse.hono.service.management.tenant`, `org.eclipse.hono.service.registration` and
  `org.eclipse.hono.service.tenant` have been moved from the *service-base* to the
  *device-registry-base* module.
* The Device Connection service did return a 500 error code if no *last known gateway* could
  be found for a device ID. This has been fixed so that the service now returns a 404 in that
  case as specified by the Device Connection API.
* The cache based Device Connection service implementation now applies a lifespan of 28 days
  when setting/updating cache entries containing *last known gateway* information. This means
  no global expiration configuration is needed anymore for the cache.
* Protocol adapters failed to re-establish receiver links for commands to be routed after loss
  of connection to the AMQP Messaging Network. This has been fixed.
* The AMQP adapter reported an incorrect number of connections if resource limits had been
  defined and exceeded. This has been fixed.
* The device registry management HTTP endpoints now reject requests to register or update objects
  containing unknown properties. In the past such properties had been simply ignored.
* The Device Registry Management API has been amended with example messages and more thorough
  description of operations.
* The CoAP adapter has been enhanced to asynchronously look up a device's PSK during the
  DTLS handshake. This will allow the adapter to handle a lot more handshakes concurrently.
  The adapter also employs a more efficient message de-duplicator which reduces the adapter's
  memory footprint.

### API Changes

* The `getRemoteContainer` method in `org.eclipse.hono.client.HonoConnection` has been
  renamed to `getRemoteContainerId`.
* The `getName` method in `org.eclipse.hono.connection.ConnectionFactory` has been removed
  and an additional `connect` method has been added.
* The *set command-handling protocol adapter instance* operation of the *Device Connection* API
  has been extended to support an additional parameter which can be used to indicate the
  maximum amount of time that the given information is to be considered valid.
* The `hono-core` module no longer embeds any external classes by default.
  This includes the *OpenTracing API* and *Spring Crypto* classes. You can still
  embed the classes, as in Hono versions 1.2.x and before, by enabling the
  Maven profile `embed-dependencies` (e.g using the command line switch
  `-Pembed-dependencies`). By default this profile is not active.
* The `org.eclipse.hono.client.DeviceRegistration` interface's *get* methods have been removed
  because the Device Registration API does not define a corresponding operation.
  Consequently, the C&C functionality of the Kerlink Lora provider which relied on the *get*
  method has been removed.
* Protocol adapters implementing the `org.eclipse.hono.service.AbstractAdapterConfig` class
  now need to implement the `getAdapterName` method. The `customizeDownstreamSenderFactoryConfig`
  method has been renamed to `getDownstreamSenderFactoryConfigDefaults`, while now returning
  properties instead of working on given ones. The new method name now more accurately conveys
  what the method is used for. The same change has been applied to the other `customize[*]Config`
  methods in the `AbstractAdapterConfig` class.
* The file based as well as the MongoDB based device registry implementations now disallow certain
  special characters in device and tenant identifiers. The configuration properties
  `HONO_REGISTRY_HTTP_TENANT_ID_PATTERN` and `HONO_REGISTRY_HTTP_DEVICE_ID_PATTERN` (and corresponding ones with
  prefix `HONO_REGISTRY_REST` for the file based registry) can be used to override the patterns for matching
  valid identifiers. Please refer to the 
  [file based registry]({{% doclink "/admin-guide/file-based-device-registry-config/#service-configuration" %}}) or
  [MongoDB based registry]({{% doclink "/admin-guide/mongodb-device-registry-config/#service-configuration" %}})
  configuration guide for details.
 
### Deprecations
 
 * The configuration property `HONO_MQTT_COMMAND_ACK_TIMEOUT` of the MQTT adapter is now deprecated
   and planned to be removed in a future release. Use `HONO_MQTT_SEND_MESSAGE_TO_DEVICE_TIMEOUT` instead.

## 1.2.4

### Fixes & Enhancements

* The `HonoConnection` implementation didn't use a delay before a reconnect attempt after
  a certain number of reconnnect attempts (58 with the default configuration) had already
  failed. This has been fixed.
* An error when freeing Command & Control related resources of an idle tenant has been fixed.
* The Hotrod based DeviceConnectionClientFactory has been improved to prevent locking of
  objects in a clustered cache.
* The AMQP adapter reported an incorrect number of connections if resource limits had been
  defined and exceeded. This has been fixed.

## 1.2.3

### Fixes & Enhancements

* The Device Connection service did return a 500 error code if no *last known gateway* could
  be found for a device ID. This has been fixed so that the service now returns a 404
  in that case as specified by the Device Connection API.
* The cache based Device Connection service implementation now applies a lifespan of 28 days
  when setting/updating cache entries containing *last known gateway* information. This means
  no global expiration configuration is needed anymore for the cache.
* Protocol adapters failed to re-establish receiver links for commands to be routed after loss
  of connection to the AMQP Messaging Network. This has been fixed.

### API Changes

* The `org.eclipse.hono.client.DeviceRegistration` interface's *get* methods have been removed
  because the Device Registration API does not define a corresponding operation.
  Consequently, the C&C functionality of the Kerlink Lora provider which relied on the *get*
  method has been removed.

## 1.2.2

### Fixes & Enhancements

* Commands might not have been routed to the target device or gateway if multiple
  Verticle instances were deployed in a protocol adapter instance. This has been fixed.
* The example registry's admin guide has been amended with some missing configuration
  variables.
* The documentation of the underlying concepts of Hono's Command & Control functionality
  has been updated to reflect recent changes.
* Some dependencies have been updated to more recent versions fixing potential
  vulnerabilities.

## 1.2.1

### Fixes & Enhancements

* The AMQP adapter didn't report any metrics anymore. This has been fixed.
* The CoAP and HTTP adapters' user guide now correctly documents the 413 status code returned
  in response to a request that contains a payload exceeding the configured maximum size.

## 1.2.0

### New Features

* The protocol adapters can now be configured with a direct connection to a data grid
  for storing device connection information.
  This has the advantage of saving the network hop to the Device Connection service.
  Please refer to the protocol adapter Admin Guides for details.
* The Prometheus based resource limits check can now be configured with a trust store, key material
  and/or a username and password in order to be able to connect to a Prometheus server
  that requires TLS and/or client authentication.
* A Java client for the communication with the AMQP protocol adapter has been added.
  It can be used to implement devices, (protocol) gateways and for testing purposes.
  For more information refer to [AMQP Adapter Client for Java]({{% doclink "/dev-guide/amqp_adapter_client/" %}}).
* Devices can now be configured with *groups* of gateways that are allowed to act on behalf of the
  device. This makes it easier to support scenarios in which a device may *roam* between multiple
  gateways. The HTTP based management API has been adapted accordingly.
* The CoAP adapter now supports forwarding commands to devices in the response body of requests for
  uploading telemetry data and/or events, analogous to the HTTP adapter.
  Please note that the CoAP adapter is still considered *experimental* and its device facing API
  is therefore still subject to change.

### Fixes & Enhancements

* When a message arrived, the *message limit* checks failed to calculate the payload size 
  of the incoming message based on the configured *minimum message size*. Instead, it used the
  actual payload size to verify if the *message limit* has been exceeded or not. This has been
  fixed now.
* The Command & Control implementation in the protocol adapters has been optimized to use
  far fewer consumer links to the AMQP Messaging Network, saving up on resources.
  Before this change, the protocol adapters created a separate receiver link for each device that wanted
  to receive commands. Now each protocol adapter instance creates only a single receiver link over
  which all commands for all devices connected to the adapter instance are transmitted.
* The base classes for implementing a device registry have been moved into their own `device-registry-based`
  module.
* The example device registry's AMQP and HTTP endpoints do no longer exchange incoming request messages and outgoing
  response messages with the service implementation verticles over the Vert.x EventBus. This reduces complexity
  and should improve throughput as messages no longer need to be serialized anymore.
* A first version of the CoAP User Guide has been added.
* A concept page explaining the different ways devices can be connected to Hono's protocol adapters has been
  added. There is also example code illustrating how a protocol gateway which connects to the AMQP adapter can
  be implemented.

### API Changes

* The device registry credentials endpoint will no longer hand out sensitive details for hashed-password secrets.
  `pwd-hash`, `salt` and `hash-function` are stored by the device registry but not returned to the user.
  Each secret is given an ID which is now returned, along with the other metadata (time validity and optional fields).
* The `tenant` and `devices` endpoints of the management HTTP API now accept creation requests without a body.
  As there is no mandatory field, having a mandatory body was confusing. 
* The methods of the service base classes `CredentialsManagementService`, `CredentialsService`,
  `DeviceConnectionService`, `DeviceManagementService`, `RegistrationService`, `TenantManagementService` and 
  `TenantService` have been refactored to now return a Vert.x Future instead of taking a Handler as an argument.
* The `CommandConsumerFactory` interface has been renamed to `ProtocolAdapterCommandConsumerFactory` and method
  signatures have been changed slightly. Also, a new `initialize` method has been added to be called on protocol
  adapter startup.
* The AMQP Messaging Network now has to be configured in such a way that protocol adapters can send and
  receive messages on the `command_internal/*` instead of the `control/*` address pattern.
* The Device Registry Management API has been extended to support the definition of *Gateway Groups* which
  can be referenced in a device's *viaGroups* property in order to authorize all gateways that are a member
  of any of the groups to act on behalf of the device.

## 1.1.2

### API Changes

* The `org.eclipse.hono.client.DeviceRegistration` interface's *get* methods have been removed
  because the Device Registration API does not define a corresponding operation.
  Consequently, the C&C functionality of the Kerlink Lora provider which relied on the *get*
  method has been removed.

## 1.1.1

### Fixes & Enhancements

* The *FireFly* LoRa adapter now supports mapping of the *mic* property.
* A bug preventing the lora and SigFox adapters to start up correctly has been fixed.
* The MQTT adapter failed to accept command response messages from authenticated gateways that had
  been published to the `command//${device-id}/res/${req-id}/${status}` topic. This has been fixed
  and the adapter now correctly uses the gateway's tenant in this case.
* When a north bound application sent a command to a device that is connected via a gateway, the
  AMQP adapter set the gateway's ID instead of the device's ID in the command response forwarded
  to the application. This has been fixed.

## 1.1.0

### New Features

* The Sigfox and LoRaWAN adapters now report metrics analogously to the other protocol
  adapters.
* With the release of [Eclipse Californium](https://www.eclipse.org/californium) 2.0.0,
  the CoAP adapter became an official part of the Hono release. The adapter also supports
  tracking of request processing using OpenTracing but is still considered *experimental*.
* A lorawan provider has been added for the *loriot* network provider.
* Hono's protocol adapters now support Server Name Indication in order
  to allow devices to establish a TLS connection with adapters using a tenant specific
  server certificate and host name. Please refer to the *Secure Communication* admin
  guide for details.
* Hono now supports *auto-provisioning* of devices that authenticate with X.509 client certificates. 
  For more information please refer to the [Device Provisioning]({{% doclink "/concepts/provisioning/" %}})
  concept and for details to the [Tenant API]({{% doclink "/api/tenant/#trusted-ca-format" %}})
  and the [Credentials API]({{% doclink "/api/credentials/#get-credentials" %}}).
* The Hono Auth Server and Device Registry components now support configuring the SASL
  mechanisms advertised to a client connecting to these components. This can be used to
  restrict the support to only one of the SASL PLAIN and EXTERNAL mechanisms instead of both. 
* A new metric namely *hono.connections.authenticated.duration* has been introduced to track the 
  connection duration of the authenticated devices. Please refer to the 
  [Metrics API]({{% doclink "/api/metrics/" %}}) for more details.
* The protocol adapters that maintain *connection state* can now be configured to verify the *connection 
  duration limit* for each tenant before accepting any new connection request from the devices. Please 
  refer to the [resource-limits]({{% doclink "/concepts/resource-limits/#connection-duration-limit" %}}) 
  section for more details.
* Hono's example device registry now supports configuring a time out for processing requests from clients.
  This is configured using the property `sendTimeOutInMs` in `org.eclipse.hono.config.ServiceConfigProperties`.
* Hono's Helm chart is now available from the
  [Eclipse IoT Packages chart repository](https://www.eclipse.org/packages/repository/).
  All future development of the chart will be done in the IoT Packages project only.
  The deployment guide has been adapted accordingly.

### Fixes & Enhancements

* Hono's Helm chart now supports configuring resource requests and limits of the container images.
* The MQTT adapter now includes a device's MQTT client ID in its request to retrieve the device's
  credentials. This additional information can be used by device registry implementations when determining
  the device identity.
* The domain name of the [Hono Sandbox]({{< relref "/sandbox.md" >}}) has been changed to
  `hono.eclipseprojects.io`.
  It will still be available at the old domain name for some time as well, though.
* Hono's OpenTracing instrumentation has been upgraded to Opentracing 0.33.0.
  The example deployment now uses the Jaeger Java client in version 0.35.2 and the
  Jaeger 1.16 agent and back end components.
* Some of the environment variable names documented in the user guides which can be used for configuring
  Hono components had not been recognized during start up of the components.
  In particular, the names for configuring the connection to the Device Connection service
  and for configuring a heath check server have been fixed.
* The AMQP adapter now correctly accepts command response messages from devices that do not
  contain any payload. Such responses are useful to only convey a status code in reply to
  a command.

### API Changes

* The already deprecated endpoints with the `control` prefix have been removed. The northbound and southbound
  Command & Control endpoints can now only be used with the `command` and `command_response` prefixes
  (or the respective shorthand version). Note that the AMQP Messaging Network still needs to be
  configured in such a way that protocol adapters can send and receive messages on the `control/*`
  address pattern. This is now used for internal communication between protocol adapters only.
* The `create` method in `org.eclipse.hono.client.HonoConnection` now requires its Vertx parameter to be not null.

## 1.0.4

### API Changes

* The `org.eclipse.hono.client.DeviceRegistration` interface's *get* methods have been removed
  because the Device Registration API does not define a corresponding operation.
  Consequently, the C&C functionality of the Kerlink Lora provider which relied on the *get*
  method has been removed.

## 1.0.3

### Fixes & Enhancements

* Hono's Helm Chart now correctly configures Prometheus based resource limit checks if
  a Prometheus server is being used for collecting metrics.
* Some OpenTracing spans used for tracking processing of messages haven't properly been finished.
  This has been fixed.
* Protocol adapters are now able to process messages while still trying to re-establish connections
  to other services after a connection loss. This might result in fewer messages being rejected
  in such situations.

## 1.0.2

### Fixes & Enhancements

* The example deployment now also works with Kubernetes 1.16 using Helm 2.15 and later.
* The Hono chart can now also be deployed using the recently released Helm 3. In fact,
  Helm 3 is now the recommended way for deploying Hono as it doesn't require installation
  of any Helm specific components to the Kubernetes cluster.
* The example data grid which can be deployed using the Hono Helm chart can now be scaled
  out to more than one node.
* Under rare circumstances an HTTP adapter instance might have gotten into a state where
  a device's requests to receive commands could no longer be processed successfully
  anymore until the adapter instance had been restarted. This has been fixed.
* A potential issue has been identified where some command messages might not get sent to
  the corresponding gateway. The scenario here involves the gateway sending event/telemetry
  messages via HTTP with a `hono-ttd` header in order to receive commands, and doing so
  with multiple concurrent requests for *different* devices. To resolve this issue, the 
  corresponding tenant can be configured with a `support-concurrent-gateway-device-command-requests`
  option set to `true` in the `ext` field of an `adapters` entry of type `hono-http`.
  Note that with this option it is not supported for the authenticated gateway to send
  _one_ message with a `hono-ttd` header and no device id to receive commands for *any*
  device that has last sent a telemetry or event message via this gateway.

## 1.0.1

### Fixes & Enhancements

* The AMQP protocol adapter now requests devices to send traffic periodically in order
  to prevent a time out of the connection. This way the adapter is able to detect and
  close stale connections which is important to reliably close the device's corresponding
  command consumer. The time period after which the adapter should consider a connection stale
  can be configured using an environment variable. Please refer to the AMQP adapter's
  admin guide for details.
* The example deployment using the Helm chart correctly creates the `DEFAULT_TENANT` again.

## 1.0.0

### New Features

* A tenant can now be configured with a *max-ttl* which is used as a upper boundary for default
  TTL values configured for devices/tenants. Please refer to the [Tenant API]
  ({{% doclink "/api/tenant#resource-limits-configuration-format" %}}) for details.
  The AMQP, HTTP, MQTT and Kura protocol adapters consider this property when setting a TTL on
  downstream event messages.
* A protocol adapter can now be configured with a timeout for idle tenants. When there has been no 
  communication between a protocol adapter instance and the devices of a tenant, the former one releases 
  allocated resources of the tenant. Currently this means that it closes AMQP links and stops reporting 
  metrics for this tenant. The timeout is configured with the property `tenantIdleTimeout` for a protocol 
  adapter. Please refer to the protocol adapter [configuration guides]({{% doclink "/admin-guide/" %}})
  for details.
* The accounting period for the *message limit* checks can now be configured as `monthly`.
  In this case the data usage for a tenant is calculated from the beginning till the end of the 
  (Gregorian) calendar month. Refer [resource limits] ({{% doclink "/concepts/resource-limits/" %}})
  for more information.
* The devices can now indicate a *time-to-live* duration for event messages published using 
  the HTTP and MQTT adapters by setting the *hono-ttl* property in requests explicitly. Please refer to the
  [HTTP Adapter]({{% doclink "/user-guide/http-adapter/#publish-an-event-authenticated-device" %}})
  and [MQTT Adapter] ({{% doclink "/user-guide/mqtt-adapter/#publishing-events" %}}) for details.
* The device registry HTTP management API now properly implements *cross-origin resource sharing (CORS)* support,
  by allowing the service to be exposed to configured domains (by default, it's exposed to all domains).
* The `org.eclipse.hono.util.MessageHelper` now provides convenience factory methods for creating
  new downstream messages from basic properties.
  `org.eclipse.hono.service.AbstractProtocolAdapterBase` has been adapted to delegate to these
  new factory methods.
* Hono's protocol adapters can now use multiple trusted certificate authorities per tenant to authenticate
  devices based on client certificates. The list of trusted certificate authorities can be managed at the
  tenant level using the Device Registry Management API.
* Authenticated gateway devices can now subscribe to commands for specific devices. Before, gateways
  could only subscribe to commands directed at any of the devices that the gateway has acted on behalf of.
  With the new feature of also being able to subscribe to commands for specific devices, northbound
  applications will get notified of such a subscription along with the specific device id.
* Now a *max-ttd* value, which is used as an upper boundary for the *hono-ttd* value specified by the devices,
  can be set as an *extension* property in the adapters section of the tenant configuration.  

### API Changes

* The already deprecated *legacy metrics* support has been removed.
* The already deprecated *legacy device registry* and the corresponding base classes, which had been deprecated 
 as well, have been removed.
* The topic filters used by MQTT devices to subscribe to commands has been changed slightly
  to better fit the addressing scheme used by the other protocol adapters.
  The existing topic filters have been deprecated but are still supported.
  Please refer to the [MQTT adapter user guide]({{% doclink "/user-guide/mqtt-adapter/#command-control" %}})
  for details.
* The interface `ResourceLimitChecks` and its implementation classes have been moved to
  package `org.eclipse.hono.service.resourcelimits` from `org.eclipse.hono.service.plan`.
  Also the configuration parameters for the resource limits were renamed from `hono.plan`
  to `hono.resourceLimits`. Please refer to the protocol adapter [configuration guides]
  ({{% doclink "/admin-guide/" %}}) for more information.
* The response payload of the *get Tenant* operation of the Tenant API has been changed to contain
  a list of trusted certificate authorities instead of just a single one. This way, protocol
  adapters can now authenticate devices based on client certificates signed by one of multiple
  different trusted root authorities defined for a tenant.
  All standard protocol adapters have been adapted to this change.
  The *Tenant* JSON schema object used in the tenant related resources of the Device Registry Management API
  has also been adapted to contain a list of trusted authorities instead of a single one.

### Deprecations

* The OpenShift specific source-to-image deployment model has been removed in
  favor of the Helm charts and the Eclipse IoT Packages project. You can still
  deploy Hono on OpenShift using the Helm charts.
* Defining password secrets with user provided password hash, function and salt is deprecated in the device registry management
  API and will be removed in the upcoming versions. You should use `pwd-plain` property only going forward.

## 1.0-M7

### New Features

* The Hono Helm chart now supports to choose if the example Device Registry, example AMQP Messaging Network
  and/or the example Jaeger back end should be deployed and used with the Hono components or not.
  In the latter case, the chart now also supports setting configuration properties for using an already
  existing AMQP Messaging Network, Device Registry and/or Jaeger back end.
* A data grid based implementation of the Device Connection API has been added to Hono. This implementation
  can be used in production environments using a highly scalable data grid for storing device connection
  information. The service can be used instead of the simple implementation provided by the example Device
  Registry by means of setting a configuration property when [deploying using the Helm chart]
  ({{% doclink "/deployment/helm-based-deployment/#using-the-device-connection-service" %}}).
* A tenant can now be configured so that *all* OpenTracing spans created when processing messages for that
  specific tenant will be recorded in the tracing backend (overriding the default sampling strategy that
  might only record a certain percentage of traces). See 
  [Monitoring & Tracing]({{% doclink "/admin-guide/monitoring-tracing-config/#enforcing-the-recording-of-traces-for-a-tenant" %}})
  for more details.

### API Changes

* The obsolete method variants of `reportTelemetry` in `org.eclipse.hono.service.metric.Metrics` 
  have been removed. The new variants of this method accept an additional parameter of type `TenantObject`.
* The already deprecated `org.eclipse.hono.service.AbstractProtocolAdapterBase.getRegistrationAssertion`
  method has been removed. The alternate variant of the `getRegistrationAssertion` method which accepts an 
  additional OpenTracing span parameter should be used.
* The already deprecated `getRegistrationAssertion`, `getTenantConfiguration`, `sendConnectedTtdEvent`,
  `sendDisconnectedTtdEvent` and `sendTtdEvent` methods in `org.eclipse.hono.service.AbstractProtocolAdapterBase` 
  have been removed. The alternate variant of these methods which accepts an additional OpenTracing span parameter 
  should be used.  

### Deprecations

* The deprecated Kura adapter is no longer deployed by default by the Helm chart.
  However, it can still be deployed by means of [setting a configuration property]
  ({{% doclink "/deployment/helm-based-deployment/#deploying-optional-adapters" %}}).

## 1.0-M6

### New Features

* Implementation of the new HTTP management API for tenants, devices and
  credentials.
* The health check endpoints of services can now be called securely via TLS. 
  Please refer to the protocol adapter [configuration guides]({{% doclink "/admin-guide/" %}}) 
  for the new parameters available.
* The [Tenant API]({{% doclink "/api/tenant/#resource-limits-configuration-format" %}}) now optionally allows 
  specifying *minimum message size*. If it is specified, then the payload size of the incoming telemetry, event 
  and command messages are calculated in accordance with the *minimum message size* by the AMQP, HTTP and MQTT 
  protocol adapters and then recorded in the metrics system.
  See [Metrics]({{% doclink "/api/metrics/#minimum-message-size" %}}) for more details.

### Fixes & Enhancements

* The automatic reconnect handling of the `HonoConnection` implementation has been
  improved and now applies an exponential back-off algorithm. The behavior can be
  configured using the `${PREFIX}_RECONNECT_*` configuration variables. Please
  refer to the [Hono Client Configuration guide]({{% doclink "/admin-guide/hono-client-configuration/" %}})
  for details regarding these new variables.
* The *message limit* checks is now extended to include command and control messages.
  Please refer to the [resource limits] ({{% doclink "/concepts/resource-limits/" %}}) for details.
* The health check server endpoint will now bind to a default port value of 8088 if no values
  are set explicitly in the configuration. It is also possible to start both a secure and an insecure
  server (using different ports)
  Refer to the [Monitoring configuration guide]({{% doclink "/admin-guide/monitoring-tracing-config" %}})
  for details.

### API Changes

* With the implementation of the new HTTP management API for the device registry,
  the class hierarchy for implementing device registry was significantly
  refactored. This also includes the deprecation of a list of classes and tests.
  Also see [Device registry changes](#device-registry-changes) for more information.
* The *complete* interfaces, and the *base* and *complete* implementation
  classes for services got deprecated, and are planned to be removed in a future
  release. Also see [Device registry changes](#device-registry-changes) for
  more information.
* The example Device Registry that comes with Hono now implements the new
  HTTP management API. Consequently, the URI endpoints for managing the content
  of the registry have changed accordingly.
* The configuration parameters for the health check endpoint were moved from 
  `hono.app` to `hono.healthCheck` and renamed. `hono.app.healthCheckPort` is now
  `hono.healthCheck.insecurePort` and `hono.app.healthCheckBindAddress` is now 
  `hono.healthCheck.insecurePortBindAdress`. Please refer to the protocol adapter
  [configuration guides]({{% doclink "/admin-guide/" %}}) for additional
  information on the new naming.

### Device registry changes

The section summarizes changes made for 1.0-M5 in the device registry.

During the development of Hono 1.0, we defined a new HTTP API for managing
information stored in the device registry. This API replaces the current,
provisional API, which was originally intended for tests to manipulate the file
based device registry during system tests. The new HTTP based API is intended
to replace the existing HTTP API, as well as the management part of the AMQP
based API.

The first major change is, that all the *complete* classes got deprecated. As
they are based on the services for the protocol adapters. And those services
are no longer considered to be used for managing information. The replacement
for those classes are the new management APIs.

Each of the three APIs got a companion API for *management*, which are located
in the `org.eclipse.hono.service.management` base package.

The class hierarchy was decoupled in order to make it easier to implement those
services. The new design only requires to implement a service, which is not
based on any other interface. And while your concrete implement still can
implement the `Verticle` interface, this is no longer a requirement.

Also the *base* classes got deprecated. Instead of inheriting the common
functionality, to bind to the *event bus*, that functionality got moved into new
*event bus adapter* classes, which take the reference of a service, and bind
this service to the *event bus* instead of using inheritance. Those change
make it possible to  re-use much of the functionality, but do not impose the
requirement to inherit from either the *complete* or *base* classes. And
this finally allows your service implementation to be extend your specific
base class, and re-use common Hono functionality at the same time. This allows
service implementations to implement both the standard API, as well as the
management API in the same class. As Java does not allow inheritance
from multiple classes, this was not possible before.

The new default file based device registry, was modified to use the new class
hierarchy and support the new management model.

The device registry, which was provided in 1.0-M4 and before, is still part of
the source tree, but was moved to `services/device-registry-legacy`. It is
there to show the compatibility with the older class hierarchy, using the,
now deprecated, *base* and *complete* classes. Newer implementations should not
be build on this model.

Clients of the *base* variants of the services, like the protocol adapters,
do not need to make any changes.

Implementations of the device registry services, using the existing *base* and
*complete* can re-use that model, but are encouraged to migrate to the new model
as soon as possible, as the legacy model is planned to be removed. The only
impacting change is that the service interfaces no longer extend from
`Verticle` directly, but that has been moved to the *base* implementation.

Implementations of the services for protocol adapters (the non-management API),
can switch to the new class hierarchy by dropping inheritance to the *base*
class, and starting up a new instance of the corresponding *event bus adapter*,
providing a reference to the service. For the `RegistrationService` it is also
possible to inherit common functionality from `AbstractRegistrationService`.

Implementations of the services for management need to simply inherit from the
new management interfaces, and set up the *event bus adapters* the same way.

The module `device-registry-legacy` as well as all classes and interfaces,
which got deprecated, are planned to be dropped in 1.1.

## 1.0-M5

### New Features

* Hono's protocol adapters and the other components now support using ECC based server certificates.
  The protocol adapters also support authenticating devices which present an ECC based client certificate.
  The example configuration now uses ECC based certificates by default.
* Hono now specifies a [Device Connection API]({{% doclink "/api/device-connection/" %}}) and
  contains an exemplary implementation of this API included in the device registry component. The purpose of the API is
  to be able to set and retrieve information about the connections from devices or gateways to the protocol adapters.
* This version implements the new HTTP management API for tenants, devices, and credentials.

### Fixes & Enhancements

* The Hono Sandbox's protocol adapters support using the gateway mode again.
* The [Getting Started]({{< relref "getting-started" >}}) guide has been rewritten
  to use the [Hono Sandbox]({{< relref "sandbox" >}}) or a local Minikube cluster
  instead of Docker Swarm.
* The MQTT adapter now closes the network connection to device on publish failures.

### API Changes

* The optional methods of the [Tenant API]({{% doclink "/api/tenant/" %}}) have been
  removed. Implementations of the Tenant API are encouraged to expose the *tenants* endpoint defined by
  [Hono's HTTP based management API]({{% doclink "/api/management" %}}) instead.
  Several of the formerly mandatory to include properties of the request and response messages have
  been made optional or removed altogether. Existing clients should not be affected by these changes, though.
* The optional methods of the 
  [Device Registration API]({{% doclink "/api/device-registration/" %}}) have been 
  removed. Implementations of the Device Registration API are encouraged to expose the *devices* endpoint defined by
  [Hono's HTTP based management API]({{% doclink "/api/management" %}}) instead.
  Several of the formerly mandatory to include properties of the request and response messages have
  been made optional or removed altogether. Existing clients should not be affected by these changes, though.
* The response message format of the *assert Device Registration* operation of the Device Registration API
  has been changed, replacing the optional `gw-supported` boolean field with an optional `via` field.
  The value of this field contains the list of gateway that may act on behalf of the device on which
  the operation is invoked.
* The methods for invoking the optional operations of the Device Registration API have been removed
  from `org.eclipse.hono.client.RegistrationClient` and `org.eclipse.hono.client.impl.RegistrationClientImpl`.
* The optional methods of the [Credentials API]({{% doclink "/api/credentials/" %}}) 
  have been removed. Implementations of the Credentials API are encouraged to expose the *credentials* endpoint defined
  by [Hono's HTTP based management API]({{% doclink "/api/management" %}}) instead.
  Several of the formerly mandatory to include properties of the request and response messages have
  been made optional or removed altogether. Existing clients should not be affected by these changes, though.
* The `control` prefix in the northbound and southbound Command & Control endpoints has been renamed to `command`. 
  The endpoint names with the `control` prefix are still supported but deprecated. The northbound endpoint for
  *business applications* to receive command responses has the `command_response` prefix now. The old `control` prefix
  for the receiver address is also still supported but deprecated.
* The `deviceId` parameter of the `getOrCreateCommandClient` and `getOrCreateAsyncCommandClient` methods of the 
  `org.eclipse.hono.client.ApplicationClientFactory` interface has been removed.
  This means that a `CommandClient` or `AsyncCommandClient` instance can be used to send commands to arbitrary
  devices of a tenant now. Accordingly, the `CommandClient.sendCommand` and `AsyncCommandClient.sendAsyncCommand`
  methods now require an additional `deviceId` parameter.
* The deprecated methods of `org.eclipse.hono.client.HonoConnection` have been removed.

## 1.0-M4

### New Features

* Default properties can now also be set at the tenant level, affecting all devices
  belonging to the tenant. Please refer to the 
  [protocol adapter user guides]({{% doclink "/user-guide/" %}}) for details.
* `CredentialsClientImpl` now supports caching of response data received from a Credentials service based on 
  *cache directives*. The protocol adapters are now equipped to cache the response from the Credentials Service.
  The protocol adapters support configuration variables to set the default cache timeout, the minimum 
  and maximum cache sizes for this service.
* The example device registry's Credentials service implementation now includes a *cache directive*
  in its response to the *get Credentials* operation which allows clients to cache credentials of
  type *hashed-password* and *x509-cert* for a configurable amount of time. Please refer to the
  [Device Registry Admin Guide]({{% doclink "/admin-guide/device-registry-config/" %}}) 
  for details regarding the configuration properties to use.
* There is now an official specification of an HTTP API for managing the content of a device registry.
  The [HTTP Management API]({{% doclink "/api/management" %}}) is defined using by 
  means of OpenAPI v3. Note, that the API is not yet implemented by the example device registry that comes with Hono.
* The Command & Control feature now supports gateway agnostic addressing of devices. This means that applications are
  able to send commands to devices without knowing the particular gateway they may be connected to.
* The concept and implementation of *message limit* have been added. The protocol adapters can be now
  enabled to verify this *message limit* for each tenant before accepting any telemetry/event messages.
  Please refer to the [resource limits]({{% doclink "/concepts/resource-limits/" %}}) for details.
* A basic Sigfox protocol adapter, for use with the Sigfox backend. Please read
  the [Sigfox protocol adapter]({{% doclink "/user-guide/sigfox-adapter/" %}})
  documentation to learn more about pre-requisites and limitations.

### Fixes & Enhancements

* vert.x has been updated to version 3.7.0.

### API Changes

* The `org.eclipse.hono.util.RegistrationConstants.FIELD_DEFAULTS` constant
  has been renamed to `org.eclipse.hono.util.RegistrationConstants.FIELD_PAYLOAD_DEFAULTS`.
* The `org.eclipse.hono.service.AbstractProtocolAdapterBase.newMessage` and
  `org.eclipse.hono.service.AbstractProtocolAdapterBase.addProperties` methods have
  been changed to accept an additional parameter of type `TenantObject` which may contain default
  properties defined for the tenant to be included in downstream messages.
* The *get Registration Information* operation of the Device Registration API is not optional anymore, 
  it is now mandatory to implement. For device registry implementations based on the
  `CompleteRegistrationService` interface, there is no change needed as the operation is already
  defined there.
* The response message of the *assert Device Registration* operation does not contain an assertion
  token anymore. The `org.eclipse.hono.service.registration.BaseRegistrationService` class
  has been adapted accordingly.
* The already deprecated `org.eclipse.hono.client.CommandConsumerFactory.closeCommandConsumer`
  method has been removed.
* The response message format of the *assert Device Registration* operation of the Device Registration API
  has been changed to include an optional `gw-supported` boolean field. The value of this field refers to 
  whether the device on which the operation is invoked allows one or more gateways to act on its behalf.
* The AMQP sender link address to be used by *business applications* to send commands to devices has been 
  changed from `control/${tenant_id}/${device_id}` to `control/${tenant_id}` with command messages requiring 
  the `to` property to be set to `control/${tenant_id}/${device_id}`. Using `control/${tenant_id}/${device_id}`
  as sender link address is still possible but gateway agnostic addressing of devices is not supported for 
  such command messages.

### Deprecations

* Instructions for script based deployment to Kubernetes have been removed from the deployment guide.
  Using Helm is now the only supported way of deploying Hono to Kubernetes.

## 1.0-M3

### Fixes & Enhancements

* The protocol adapters add tracing information about invocations of the Device Registry
  services again.
* The example deployment now uses Qpid Dispatch Router 1.6.0.

## 1.0-M2

### New Features

* A new *experimental* LoRa protocol adapter has been added which (so far) supports the reception
  of telemetry data and events from devices connected to LoRa network providers and/or
  LoRa gateways. Note that this adapter is *not* considered production ready yet.
  Any help in improving and enhancing the adapter is more than welcome.
* The concept and implementation of *resource limits* have been added. Now a connection limit to define 
  the maximum number of device connections to be allowed per tenant can be configured. The MQTT and AMQP 
  adapters can be enabled to verify this connection limit before accepting any new connections. Please 
  refer to the [resource limits]({{% doclink "/concepts/resource-limits/" %}}) for details.
 
### Fixes & Enhancements

* The base classes for implementing the AMQP and HTTP endpoints for the Credentials, Tenant
  and Device Registration APIs now create an OpenTracing Span for tracking the
  processing of requests at a high level.
* The `hono-client` and `hono-core` artifacts use Java 8 level again so that they
  can be used in applications using Java 8.
* The protocol adapters now always specify values for the *ttd* and *qos* tags when
  reporting telemetry messages using meter name *hono.messages.received*. This fixes
  an issue when using the Prometheus back end where the HTTP adapter failed to report
  messages that contained a TTD value and others that didn't.
* The Helm based deployment of the device registry has been fixed by adding the secret
  and deployment entries for the `device-identities.json`.
* Before uploading command responses, the MQTT and AMQP adapters now check whether the device is 
  registered and also the adapter is enabled for the tenant.

### API Changes

* The `hono-client` module has undergone several major and incompatible changes. The most
  important change affects the `HonoClient` interface which no longer serves as a factory
  for the arbitrary clients to the Hono service endpoints.
  It has been renamed to `HonoConnection` and now only represents the underlying
  AMQP connection to a peer and provides methods for managing the connection state
  and registering listeners for arbitrary life-cycle events of the connection.
  In addition to this, several factory interfaces have been added which can be used
  to create specific clients to Hono's arbitrary services. All of the former `HonoClient`
  interface's factory methods have been distributed accordingly to:
  * `org.eclipse.hono.client.ApplicationClientFactory` for creating clients to
    Hono's north bound Telemetry, Event and Command &amp; Control API.
  * `org.eclipse.hono.client.DownstreamSenderFactory` for creating clients to
    Hono's south bound Telemetry and Event APIs.
  * `org.eclipse.hono.client.CommandConsumerFactory` for creating clients to
    Hono's south bound Command &amp; Control API.
  * `org.eclipse.hono.client.TenantClientFactory` for creating clients to
    Hono's Tenant API.
  * `org.eclipse.hono.client.RegistrationClientFactory` for creating clients to
    Hono's Device Registration API.
  * `org.eclipse.hono.client.CredentialsClientFactory` for creating clients to
    Hono's Credentials API.
* In this context the `org.eclipse.hono.client.MessageSender` interface has been changed as follows:
  * The *send* methods have been changed to no longer accept a *registration assertion token*
    which became obsolete with the removal of the *Hono Messaging* component.
  * The *isRegistrationAssertionRequired* method has been removed from the interface.
  * All *send* method variants which accept specific message parameters have been moved into
    the new `org.eclipse.hono.client.DownstreamSender` interface which extends the existing
    `MessageSender`.
* Several changes have been made to the `org.eclipse.hono.service.AbstractProtocolAdapterBase`
  class:
  * The *newMessage* and *addProperties* methods no longer require a boolean parameter indicating
    whether to include the assertion token in the message being created/amended.
    Custom protocol adapters should simply omit the corresponding parameter.
  * The base class now uses `org.eclipse.hono.client.CommandConsumerFactory` instead of
    `org.eclipse.hono.client.CommandConnection` for creating
    `org.eclipse.hono.client.CommandConsumer` instances.
    The *setCommandConnection* and *getCommandConnection* methods have been
    renamed to *setCommandConsumerFactory* and *getCommandConsumerFactory*
    correspondingly.
  * The base class now uses `org.eclipse.hono.client.TenantClientFactory` instead of
    `org.eclipse.hono.client.HonoClient` for creating `org.eclipse.hono.client.TenantClient`
    instances.
    The *setTenantServiceClient* and *getTenantServiceClient* methods have been
    renamed to *setTenantClientFactory* and *getTenantClientFactory* correspondingly.
  * The base class now uses `org.eclipse.hono.client.RegistrationClientFactory` instead of
    `org.eclipse.hono.client.HonoClient` for creating
    `org.eclipse.hono.client.RegistrationClient` instances.
    The *setRegistrationServiceClient* and *getRegistrationServiceClient* methods have been
    renamed to *setRegistrationClientFactory* and *getRegistrationClientFactory* correspondingly.
  * The base class now uses `org.eclipse.hono.client.CredentialsClientFactory` instead of
    `org.eclipse.hono.client.HonoClient` for creating
    `org.eclipse.hono.client.CredentialsClient` instances.
    The *setCredentialsServiceClient* and *getCredentialsServiceClient* methods have been
    renamed to *setCredentialsClientFactory* and *getCredentialsClientFactory* correspondingly.
  * The base class now uses `org.eclipse.hono.client.DownstreamSendertFactory` instead of
    `org.eclipse.hono.client.HonoClient` for creating
    `org.eclipse.hono.client.DownstreamSender` instances.
    The *setHonoMessagingClient* and *getHonoMessagingClient* methods have been
    renamed to *setDownstreamSenderFactory* and *getDownstreamSenderFactory* correspondingly.
* The `org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider` and the
  `org.eclipse.hono.service.auth.device.X509AuthProvider` now accept a
  `org.eclipse.hono.client.CredentialsClientFactory` instead of a
  `org.eclipse.hono.client.HonoClient` in their constructors.
* The `org.eclipse.hono.adapter.http.HonoBasicAuthHandler` and
  `org.eclipse.hono.adapter.http.X509AuthHandler` classes have been moved to
  package `org.eclipse.hono.service.http` in the *service-base* module for
  consistency reasons as all other reusable classes for implementing HTTP services/
  adapters are located in that package already.
* The `org.eclipse.hono.client.HonoClient` class has been renamed to
  `org.eclipse.hono.client.HonoConnection` to better reflect its sole responsibility
  for establishing (and maintaining) the connection to a Hono service endpoint.

### Deprecations

* The optional operations defined by the Tenant, Device Registration and Credentials API
  have been deprecated. They will be removed from Hono 1.0 altogether.
  A new HTTP based API will be defined instead which can then be used to *manage* the content
  of a device registry.
* `org.eclipse.hono.client.HonoConnection`'s *connect* method variants accepting
  a disconnect handler have been deprecated and will be removed in Hono 1.0.
  Client code should use one of the other *connect* methods instead and register a
  `org.eclipse.hono.client.DisconnectListener` and/or a
  `org.eclipse.hono.client.ReconnectListener` to get notified about life-cycle
  events of the underlying AMQP connection.

## 1.0-M1

### New Features

* The AMQP adapter now supports limiting the number of concurrent connections in order
  to prevent Out of Memory errors. Please refer to
  [AMQP Adapter Configuration]({{% doclink "/admin-guide/amqp-adapter-config/" %}}) for details.
* The `org.eclipse.hono.client.AsyncCommandClient` has been added to support the sending of
  commands to devices and the receiving of responses in an asynchronous way. This can be used
  to decouple the sender and receiver from each other. 
* The `org.eclipse.hono.service.tenant.CompleteBaseTenantService` class now rejects malformed
  encodings of public keys/certificates included in a request to add a trust anchor to a tenant.

### Deprecations

* The `HonoClient.closeCommandConsumer()` method will be removed in Hono 1.0.
  The `CommandConsumer.close()` method should be used instead.

## 0.9

### New Features

* The MQTT adapter now supports commands to be published using QoS 1. Please refer to
  [MQTT adapter User Guide]({{% doclink "/user-guide/mqtt-adapter/" %}}) for details.
* The MQTT adapter now supports limiting the number of concurrent connections in order
  to prevent running out of resources. Please refer to
  [MQTT Adapter Configuration]({{% doclink "/admin-guide/mqtt-adapter-config/" %}}) for details.
* The new *Helm deployment* for Kubernetes has been added. Please refer to
  [Helm based deployment guide]({{% doclink "/deployment/helm-based-deployment/" %}}) for details.

### Fixes & Enhancements

* `org.eclipse.hono.util.RequestResponseResult` now provides access to AMQP application-properties conveyed in the
  response message.
* The `org.eclipse.hono.service.registration.BaseRegistrationService` class now supports authorization of gateways
  (acting on behalf of a device) against a list of gateway identifiers instead of a single identifier only. For that purpose
  the `via` property of the device's registration information may contain either a single string or a JSON array containing
  multiple strings. Based on this, a device can now be configured to connect via arbitrary gateways instead of just a single
  one.

### API Changes

* The layout and structure of the metrics reported by Hono have been changed substantially. Many of the existing meters and tags
  have been changed or replaced in order to provide a more consistent set of metrics and increase the value of the information
  being reported. The legacy metrics still remain unchanged, though.
  Please refer to the [Metrics definition]({{% doclink "/api/metrics/" %}}) for details.
* In case of a failed connection attempt, `HonoClientImpl` will now determine based on the error whether it will re-try
  to connect to the peer. Before, reconnect attempts were done unconditionally, by default infinitely or up to the
  number of times defined in the *reconnectAttempts* property in the `ClientConfigProperties`. Now, when the outcome
  of the SASL handshake received from the peer during connection establishment indicates that invalid credentials were
  provided or that the server encountered a permanent error when handling the request, no further reconnect attempts
  will be done.
* The deprecated methods have been removed from `org.eclipse.hono.client.MessageSender` and
  its implementations.
* The Command &amp; Control API's *send a request/response command* operation has been changed. The response
  message to a command now must include the device and tenant identifiers of the device. Including
  these two properties should make it much easier to implement competing command response consumers
  in business applications.
  As a consequence, the `org.eclipse.hono.client.CommandResponse`'s factory methods have been changed to
  accept the tenant and device IDs as parameters.
* The *connectTimeout* configuration variable for the `HonoClient` now defines the time to wait not only for the TCP/TLS
  connection establishment but also for the SASL handshake and the exchange of the AMQP *open* frame.
* The (already deprecated) Hono Messaging component has been removed from Hono.

### Deprecations

* The script based deployment to Kubernetes has been deprecated and will be removed in the next version.
  The Helm based deployment should be used instead.
* The *sendCommandResponse(String, String, Buffer, Map, int, SpanContext)* of the
  `org.eclipse.hono.client.CommandResponseSender` interface has been deprecated and
  will be removed in the next version. Custom protocol adapters should use
  *sendCommandResponse(CommandResponse, SpanContext)* instead.

## 0.9-M2

### New Features

* The MQTT protocol adapter now supports authentication of devices using X.509 client certificates. Please refer to
  the [MQTT adapter user guide]({{% doclink "/user-guide/mqtt-adapter/" %}}) for details regarding configuration.

### Fixes & Enhancements

* The OpenShift *source-to-image* (S2I) deployment is now the default
  OpenShift / OKD deployment. The plain OpenShift deployment, which had been deprecated
  in Hono 0.8, has been removed.
* The protocol adapters can now be configured with a custom *DNS timeout* value, limiting the time that the adapter
  will wait for the response to a DNS query. By default, a DNS query will time out after 5 seconds.
  Please refer to the protocol adapter admin guides for details regarding the new configuration variable.
* The following configuration variables have been added to `HonoClient`:

  * *connectTimeout*: Sets a limit on the time that the client will wait for a TCP/TLS connection with the peer
    to be established. By default, a connection attempt will time out after 5 seconds.
  * *idleTimeout*: The idle timeout defines the amount of time after which a connection will be closed when no frames
    have been received from the remote peer. The default value is 16 seconds.
  * *sendMessageTimeout*: Limits the time to wait for a downstream consumer's acknowledgement of
    an event or command response message received from a device. The default value is 1 second.

    Please refer to the [Hono Client Configuration guide]({{% doclink "/admin-guide/hono-client-configuration/" %}})
    for details regarding the new configuration variables.

### API Changes

* Some of the *tags* used by Hono's components when reporting metrics have been changed. The common tag *component*
  has been renamed to *component-type*. The *protocol* tag formerly used by adapters to indicate the transport protocol
  that a message has been received over, has been replaced by the generic *component-name* tag which indicates the name
  of the component that a metric has been reported by. Please refer to the [Metrics API]({{% doclink "/api/metrics/" %}})
  for details. Note that these changes do not affect the legacy Graphite based metrics back end.

### Deprecations

* The Hono Messaging component is now  deprecated and will be removed from Hono in version 0.9 altogether.
  The example deployment has not been using Hono Messaging since 0.6 and there is no practical reason for
  using it anymore.

## 0.9-M1

### New Features

* The default Micrometer back end is now Prometheus, the Grafana dash boards have been updated
  to retrieve data from Prometheus instead of the old InfluxDB.
  The Graphite based legacy metrics format can still be used but requires building Hono from source and activating
  the `metrics-graphite` Maven build profile.
  Please refer to the [Monitoring admin guide]({{% doclink "/admin-guide/monitoring-tracing-config/" %}}) for details.
* The `org.eclipse.hono.service.credentials.CompleteBaseCredentialsService` class now supports the transparent
  *on-the-fly* hashing of clear text passwords contained in *hashed-password* credentials. Please refer to the
  [Device Registry user guide]({{% doclink "/user-guide/device-registry/#managing-credentials" %}}) for details.

### Fixes & Enhancements

* The base classes for implementing the Device Registration and Tenant APIs have been instrumented
  with OpenTracing. New variants of the `RegistrationService.assertRegistration`, `TenantService.get` and `CredentialsService.get`
  methods have been added which also accept an OpenTracing span as a parameter.
  The default implementations of these methods still default to the previously existing methods.
  In `RegistrationService` implementations based on `BaseRegistrationService` an OpenTracing span will be created,
  passed on to the `assertRegistration` method and finished eventually. The same applies to `TenantService` implementations
  based on `BaseTenantService` concerning the `get` method and to `CredentialsService` implementations based on
  `BaseCredentialsService` concerning the `get` method.

### API Changes

* The `org.eclipse.hono.service.credentials.CompleteBaseCredentialsService` class now requires an
  `org.eclipse.hono.auth.HonoPasswordEncoder` to be passed into its constructor.
  The `org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder` has been added as a default implementation for
  this purpose.
* The [Tenant API]({{% doclink "/api/tenant/#trusted-ca-format" %}}) now optionally allows specifying an
  X.509 certificate instead of a public key when defining a trusted CA.

