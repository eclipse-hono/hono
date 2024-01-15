+++
title = "Device Registry"
weight = 205
aliases = [
  "/user-guide/file-based-device-registry/",
  "/user-guide/jdbc-based-device-registry/",
  "/user-guide/mongodb-based-device-registry/"
]
+++

The Device Registry component provides implementations of Hono's [Tenant API]({{< relref "/api/tenant" >}}),
[Device Registration API]({{< relref "/api/device-registration" >}}) and [Credentials API]({{< relref "/api/credentials" >}}).
Protocol adapters use these AMQP 1.0 based APIs to determine a device's registration status during runtime. For example,
when a device tries to connect to a protocol adapter, the adapter uses information retrieved from the device registry
to check if the device is *enabled*, whether the tenant that it belongs to requires devices to authenticate before
accepting any data from it and if so, for verifying the device's credentials.

In addition to the above APIs, the Device Registry also exposes HTTP endpoints for managing the content of the registry
according to the [Device Registry Management API]({{< relref "/api/management" >}}). This API is used by administrators
to create and configure tenants and provision devices into the system. Without these steps, no devices can connect to
any of Hono's protocol adapters. The [Device Provisioning concept page]({{< relref "device-provisioning" >}}) describes
the steps necessary to manually provision a device and also provides information about how devices can be provisioned
automatically based on X.509 client certificates.

<!--more-->
Hono comes with two registry implementations out of the box:

1. The [Mongo DB based registry]({{< relref "mongodb-device-registry-config" >}}) persists data to collections of a Mongo DB.
1. The [JDBC based registry]({{< relref "jdbc-device-registry-config" >}}) persists data to tables of a relational database.

Please refer to the corresponding Admin Guides for details regarding the configuration of the registries.

{{% notice tip %}}
During runtime, the protocol adapters require access to *any* implementation of the Tenant, Device Registration
and Credentials APIs. However, they do not require these implementations to be the Mongo DB or the JDBC based
registry coming with Hono. This means that the protocol adapters can also be configured to access custom implementations
of these APIs which, for example, serve as a facade for an existing application or service that contains the relevant
device data.
{{% /notice %}}

## Device Registry Management API

The HTTP based [Device Registry Management API]({{< relref "/api/management" >}}) can be used by administrators and/or
applications to create, update and delete the entities managed by the registry.
This includes tenants, the devices belonging to the tenants and the credentials that devices use to authenticate to
protocol adapters.

### Authentication

The registry implementations can be configured to require clients of the management API to authenticate using the
standard HTTP basic authentication mechanism. For more information on how to configure authentication, please refer
to the registry's corresponding Admin Guide.

### Managing Tenants

The tenants in the registry can be managed using the Device Registry Management API's
[tenant related resources]({{< relref "/api/management#tenants" >}}).

{{% notice info %}}
The JDBC based registry implementation currently has the following limitations:

* Tenants can be retrieved using the [search tenants]({{< relref "/api/management#tenants/searchTenants" >}})
  operation defined by the Device Registry Management API, but the *filterJson* and *sortJson* query parameters are
  (currently) being ignored. The result set will always be sorted by the tenant ID in ascending order.
* The *alias* and *trust-anchor-group* properties defined on a tenant are being ignored by the registry. Consequently,
  multiple tenants can not be configured to use the same trust anchor(s).
{{% /notice %}}

#### Registering a Certificate Authority

Devices can use an X.509 client certificate for authenticating to Hono's protocol adapters. In order for this to work,
one or more root *certificate authorities* (CAs) need to be registered with the tenant that the devices belong to.

The [Management API]({{< relref "/api/management#tenants/updateTenant" >}}) can be used to register CAs based on a public
key plus some meta data. It also supports registering CAs using PEM files that contain a signed certificate.

Given a certificate in a PEM file (i.e. one that begins with `-----BEGIN CERTIFICATE-----` and ends with
`-----END CERTIFICATE-----`), the Base64 string representing the certificate's binary encoding can be extracted
from the file using the following command:

```sh
CERT=$(openssl x509 -in my-ca-cert.pem -outform PEM | sed /^---/d | sed -z 's/\n//g')
```

The CA can then be registered using the Management API:

```sh
TENANT_ID=my-tenant
REGISTRY_IP=hono.eclipseprojects.io
curl --location 'https://${REGISTRY_IP}:28443/v1/tenants/${TENANT_ID}'
--header 'content-type: application/json'
--data '{
  "trusted-ca": [
    {
      "cert": "'${CERT}'"
    }
  ],
  "ext": {
    "messaging-type": "kafka"
  }
}'
```

The `TENANT_ID` and `REGISTRY_IP` variables need to be adapted to the Hono installation and tenant being used.

#### Registration Limits

The registry implementations support the enforcement of *registration limits* defined at the tenant level.
In particular, the registries enforce

* the maximum number of devices that can be registered for each tenant and
* the maximum number of credentials that can be registered for each device of a tenant.

In order to enable enforcement of any of these limits for a tenant, the corresponding *registration-limits* property
needs to be set explicitly on the tenant like in the example below:

```json
{
  "registration-limits": {
    "max-devices": 100,
    "max-credentials-per-device": 5
  }
}
```

The registry implementations can also be configured with a global limit for the number of devices that can be registered
per tenant. The value can be set via the registry's *HONO_REGISTRY_SVC_MAXDEVICESPERTENANT* configuration variable.
The maximum number of devices allowed for a particular tenant is then determined as follows:

1. If the tenant's *registration-limits*/*max-devices* property has a value > `-1` then that value is used as the limit.
1. Otherwise, if the *HONO_REGISTRY_SVC_MAXDEVICESPERTENANT* configuration variable has a value > `-1` then that value
   is used as the limit.
1. Otherwise, the number of devices is unlimited.

### Managing Devices

The devices in the registry can be managed using the Device Registry Management API's
[device related resources]({{< relref "/api/management#devices" >}}).

{{% notice info %}}
The JDBC based registry implementation currently has the following limitations:

* Registration information can be retrieved using the
  [search devices]({{< relref "/api/management#devices/searchDevicesForTenant" >}}) operation defined by the Device
  Registry Management API. The *filterJson* query parameter currently only allows one filter expression per request,
  the *sortJson* query parameter is (currently) being ignored.
  The result set will always be sorted by the device ID in ascending order.
{{% /notice %}}

### Managing Credentials

The device's credentials can be managed using the Device Registry Management API's
[credentials related resources]({{< relref "/api/management#credentials" >}}).
