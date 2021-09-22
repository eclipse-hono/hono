+++
title = "File Based Device Registry"
hidden = true
weight = 210
+++

The Device Registry component provides exemplary implementations of Hono's [Tenant API]({{< relref "/api/tenant" >}}),
[Device Registration API]({{< relref "/api/device-registration" >}}) and [Credentials API]({{< relref "/api/credentials" >}}).

<!--more-->
As such it exposes AMQP 1.0 based endpoints for retrieving the relevant information and persists data to the local file
system.

{{% note title="Deprecation" %}}
The file based device registry has been deprecated and will be removed in Hono 2.0.0.
Please use the [Mongo DB]({{< relref "mongodb-device-registry-config" >}}) or
[JDBC based registry]({{< relref "jdbc-device-registry-config" >}}) implementations instead.
{{% /note %}}

In addition, the Device Registry also exposes HTTP resources for managing the contents of the registry according to the
[Device Registry Management API]({{< relref "/api/management" >}}).

{{% warning %}}
The Device Registry is not intended to be used in production environments. In particular, access to the HTTP resources
described below is not restricted to authorized clients only.
{{% /warning %}}

## Managing Tenants

Please refer to the [Device Registry Management API]({{< relref "/api/management#tenants" >}}) for information about
managing tenants.

{{% note %}}
The file based device registry does not support the Device Registry Management API's
[search tenants]({{< relref "/api/management#tenants/searchTenants" >}}) operation.
{{% /note %}}

## Managing Devices

Please refer to the [Device Registry Management API]({{< relref "/api/management#devices" >}}) for information about
managing devices.

{{% note %}}
The file based device registry does not support the Device Registry Management API's
[search devices]({{< relref "/api/management#devices/searchDevicesForTenant" >}}) operation.
{{% /note %}}

## Managing Credentials

Please refer to the [Device Registry Management API]({{< relref "/api/management#credentials" >}}) for information about
managing credentials.
