+++
title = "MongoDB Based Device Registry"
weight = 205
+++

The MongoDB based Device Registry component provides implementations of Hono's [Tenant API]({{< relref "/api/tenant" >}}), [Device Registration API]({{< relref "/api/device-registration" >}}) and [Credentials API]({{< relref "/api/credentials" >}}). As such it exposes AMQP 1.0 based endpoints for retrieving the relevant information. Protocol adapters use these APIs to determine a device's registration status, e.g. if it is enabled and if it is registered with a particular tenant, and to authenticate a device before accepting any data for processing from it.

In addition to the above APIs, this Device Registry also exposes HTTP endpoints for managing the contents of the Device Registry according to the [Device Registry Management API]({{< relref "/api/management" >}}). It uses a MongoDB database to persist the data. The credentials, device and tenant information are stored in separate collections in a MongoDB database. For more information on how to configure the MongoDB based device registry, see [MongoDB based Device Registry configuration]({{< relref "/admin-guide/mongodb-device-registry-config.md" >}}).

## Authentication

This Device Registry secures its HTTP Endpoints using basic authentication mechanism. Thereby the clients connecting to the MongoDB based Device Registry are required to authenticate. For more information on how to enable the authentication and configure it, please refer to the `hono.registry.http.authenticationRequired` property in the [MongoDB based Device Registry configuration]({{< relref "/admin-guide/mongodb-device-registry-config.md#service-configuration" >}}).

## Managing Tenants

Please refer to the [Device Registry Management API]({{< relref "/api/management#tenants" >}}) for information about managing tenants.

## Managing Devices

Please refer to the [Device Registry Management API]({{< relref "/api/management#devices" >}}) for information about managing devices.

## Managing Credentials

Please refer to the [Device Registry Management API]({{< relref "/api/management#credentials" >}}) for information about managing credentials.
