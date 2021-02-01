+++
title = "JDBC Based Device Registry"
weight = 207
+++

The JDBC based Device Registry component provides implementations of Hono's [Tenant API]({{< relref "/api/tenant" >}}), [Device Registration API]({{< relref "/api/device-registration" >}}) and [Credentials API]({{< relref "/api/credentials" >}}). As such it exposes AMQP 1.0 based endpoints for retrieving the relevant information. Protocol adapters use these APIs to determine a device's registration status, e.g. if it is enabled and if it is registered with a particular tenant, and to authenticate a device before accepting any data for processing from it.

In addition to the above APIs, this Device Registry also exposes HTTP endpoints for managing the contents of the Device Registry according to the [Device Registry Management API]({{< relref "/api/management" >}}). It uses a JDBC compliant database to persist the data. By default, this registry supports H2 and PostgreSQL. For more information on how to configure the JDBC based device registry, see [JDBC based Device Registry configuration]({{< relref "/admin-guide/jdbc-device-registry-config.md" >}}).

## Managing Tenants

Please refer to the [Device Registry Management API]({{< relref "/api/management#tenants" >}}) for information about managing tenants.

## Managing Devices

Please refer to the [Device Registry Management API]({{< relref "/api/management#devices" >}}) for information about managing devices.

## Managing Credentials

Please refer to the [Device Registry Management API]({{< relref "/api/management#credentials" >}}) for information about managing credentials.
