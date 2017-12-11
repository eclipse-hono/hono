This folder contains scripts and configuration files for setting up a Hono *sandbox* with the following properties:

* Clients can register devices for arbitrary tenants using Device Registry's REST interface (requires registering the device itself as well as corresponding credentials).
* Devices can publish telemetry data and events for the tenant they have been registered with using the MQTT and HTTP adapters.
* Consumers can receive telemetry data and events for arbitrary tenants using the Telemetry and Event APIs.
* Consumers and devices are required to authenticate with the Dispatch Router and the adapters respectively.