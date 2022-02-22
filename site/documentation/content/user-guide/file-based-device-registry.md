+++
title = "File Based Device Registry"
hidden = true
weight = 0
+++

The Device Registry component provides exemplary implementations of Hono's [Tenant API]({{< relref "/api/tenant" >}}),
[Device Registration API]({{< relref "/api/device-registration" >}}) and [Credentials API]({{< relref "/api/credentials" >}}).

<!--more-->
As such it exposes AMQP 1.0 based endpoints for retrieving the relevant information and persists data to the local file
system.

{{% notice info %}}
The file based device registry has been removed in Hono 2.0.0.
Please use the [Mongo DB]({{< relref "mongodb-device-registry-config" >}}) or
[JDBC based registry]({{< relref "jdbc-device-registry-config" >}}) implementations instead.
{{% /notice %}}

In addition, the Device Registry also exposes HTTP resources for managing the contents of the registry according to the
[Device Registry Management API]({{< relref "/api/management" >}}).

{{% notice warning %}}
The Device Registry is not intended to be used in production environments. In particular, access to the HTTP resources
described below is not restricted to authorized clients only.
{{% /notice %}}

## Managing Tenants

Please refer to the [Device Registry Management API]({{< relref "/api/management#tenants" >}}) for information about
managing tenants.

{{% notice info %}}
The file based device registry does not support the Device Registry Management API's
[search tenants]({{< relref "/api/management#tenants/searchTenants" >}}) operation.
{{% /notice %}}

## Managing Devices

Please refer to the [Device Registry Management API]({{< relref "/api/management#devices" >}}) for information about
managing devices.

{{% notice info %}}
The file based device registry does not support the Device Registry Management API's
[search devices]({{< relref "/api/management#devices/searchDevicesForTenant" >}}) operation.
{{% /notice %}}

## Managing Credentials

Please refer to the [Device Registry Management API]({{< relref "/api/management#credentials" >}}) for information about
managing credentials.
