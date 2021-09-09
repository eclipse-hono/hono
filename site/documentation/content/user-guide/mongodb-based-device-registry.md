+++
title = "MongoDB Based Device Registry"
weight = 280
+++

The MongoDB based Device Registry component provides implementations of Hono's [Tenant API]({{< relref "/api/tenant" >}}),
[Device Registration API]({{< relref "/api/device-registration" >}}) and [Credentials API]({{< relref "/api/credentials" >}}).
As such, it exposes AMQP 1.0 based endpoints for retrieving the relevant information. Protocol adapters use these APIs
to determine a device's registration status, e.g. if it is enabled and if it is registered with a particular tenant,
and to authenticate a device before accepting any data for processing from it.

In addition to the above APIs, this Device Registry also exposes HTTP endpoints for managing the contents of the Device
Registry according to the [Device Registry Management API]({{< relref "/api/management" >}}). It uses a MongoDB database
to persist tenant, device and credentials data into separate collections.

For more information on how to configure the MongoDB based device registry, see
[MongoDB based Device Registry configuration]({{< relref "mongodb-device-registry-config" >}}).

## Authentication

This Device Registry secures its HTTP Endpoints using basic authentication mechanism. Thereby the clients connecting
to the MongoDB based Device Registry are required to authenticate. For more information on how to enable the
authentication and configure it, please refer to the `hono.registry.http.authenticationRequired` property in the
[MongoDB based Device Registry configuration]({{< relref "mongodb-device-registry-config#service-configuration" >}}).

## Managing Tenants

Please refer to the [Device Registry Management API]({{< relref "/api/management#tenants" >}}) for information about managing tenants.

### Registration Limits

The registry supports the enforcement of *registration limits* defined at the tenant level. The registry enforces

* the maximum number of devices that can be registered for a tenant and
* the maximum number of credentials that can be registered for each device of a tenant.

In order to enable enforcement of any of these limits for a tenant, the corresponding *registration-limits* property needs to
be set explicitly on the tenant like in the example below:

```json
{
  "registration-limits": {
    "max-devices": 100,
    "max-credentials-per-device": 5
  }
}
```

{{% note title="Global Maximum Device per Tenant Limit" %}}
The registry can be configured with a global limit for the number of devices that can be registered per tenant.
The value can be set via the registry's *HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT* configuration variable.
The maximum number of devices allowed for a particular tenant is determined as follows:

1. If the tenant's *registration-limits*/*max-devices* property has a value > -1 then that value is used as the limit.
1. Otherwise, if the *HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT* configuration variable has a value > -1 then that value
   is used as the limit.
1. Otherwise, the number of devices is unlimited.
{{% /note %}}

## Managing Devices

Please refer to the [Device Registry Management API]({{< relref "/api/management#devices" >}}) for information about managing devices.

## Managing Credentials

Please refer to the [Device Registry Management API]({{< relref "/api/management#credentials" >}}) for information about managing credentials.
