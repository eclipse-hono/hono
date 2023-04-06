---
title: "Device Provisioning"
weight: 188
resources:
  - src: auto-provisioning.svg
---

This page describes how devices are provisioned in Hono, i.e. how their digital representation is generated.
For each device, registration information is stored that defines a device identity. 
Each device belongs to exactly one tenant. Each device must have at least one set of credentials that are used to authenticate to Hono.

To get an understanding of what is meant by the terms *tenant*, *device registration* and *credentials*, 
it is recommended to read the [Device Identity]({{< relref "/concepts/device-identity" >}}) page first.

So in order to use a device with Hono, it has to be provisioned. 
This means that registration information and at least one credential record must be stored in the device registry.

There are different ways to perform device provisioning.

## Manual Device Provisioning

Devices can be provisioned using Hono's [Device Registry Management API]({{< relref "/api/management" >}}) via HTTP.

If the desired *tenant* does not yet exist, it must be created first. 
How to do this is described in the tenants section of the [Device Registry Management API]({{< relref "/api/management" >}}).

The actual Device Provisioning is then performed by adding devices as described under devices section of the 
[Device Registry Management API]({{< relref "/api/management" >}}).
This creates both a device identity and an (empty) credentials record. The last step is to add real credentials
as described in the credentials section of the [Device Registry Management API]({{< relref "/api/management" >}}).


## Automatic Device Provisioning

The term *Auto-Provisioning* denotes a feature of Hono where the Device Registry automatically generates 
the credentials and registration information for a device the first time it connects.
Auto-Provisioning is supported by Hono's protocol adapters for devices that authenticate with client certificates or for
devices that are connected via a gateway.

### Prerequisites

Hono does not require a specific Device Registry implementation, but only specifies a set of APIs that must be provided by a compatible implementation.
Since the main part of the *Auto-Provisioning* has to be done by the Device Registry, the used implementation must explicitly support this feature.

#### Client certificate based Auto-Provisioning

This feature can be enabled by supplying a certificate authority (CA) for a certain tenant:

##### Step 1: Configure the Tenant

The CA to be used by the devices needs to be configured. The following tasks must be performed:

1. Create tenant
2. Configure the trusted CA for the tenant
3. Enable the feature for the CA

If the Device Registry implementation provides the [Management API]({{< ref "/api/management" >}}), this could be done in a single step. 
For details refer to the [Tenant API specification]({{< ref "/api/tenant#trusted-ca-format" >}}).

The Device Registry generates a unique device identifier during auto-provisioning. If *auto-provisioning-device-id-template*
is configured in the corresponding tenant's CA entry, then the device registry generates the device identifier based on
the configured template. If not configured, then a random unique device identifier is generated. Refer to the 
[Device Registry Management API]({{< ref "/api/management#tenants/createTenant" >}}) for more details on how to 
configure a tenant's trusted CA authority for that.

The Device Registry creates device credentials during auto-provisioning. If *auth-id-template* is configured in the
corresponding tenant’s CA entry, then the device registry generates the credentials authentication identity based on the
configured template. If not configured, then subject DN of the device certificate is used as authentication identity.
Refer to the [Device Registry Management API]({{< ref "/api/management#tenants/createTenant" >}}) for more details on 
how to configure a tenant's trusted CA authority for that.

##### Step 2: Connect an unregistered Device to Hono

{{< figure src="auto-provisioning.svg" alt="A unregistered device connects to a protocol adapter which uses the Credentials API to provision it" title="Automatic Provisioning of a Device" >}}

Hono's protocol adapters query the APIs of the Device Registry during [Device Authentication]({{< ref "/concepts/device-identity" >}}).
First, the Tenant API is queried. If the Tenant configuration returned contains the CA used for authentication and the 
feature is switched on for this CA, the protocol adapter assumes that automatic provisioning must be performed.
It puts the device's certificate into the query to enable the device registry to do the provisioning.

If the Device Registry does not find any credentials for the device, it takes the information from the client 
certificate to create both credentials and device registration data for it.

The Device Registry is expected to perform the following steps: 

1. Generate a unique device-id
2. Create device
3. Create credentials
4. Send a [Device Provisioning Notification]({{< ref "/api/event#device-provisioning-notification" >}})
5. Optional: Provision device in external systems

After successfully sending a device provisioning notification, the corresponding device registration is updated with
*auto-provisioning-notification-sent* as `true`. Whenever a protocol adapter sends a request to the Device Registry to
fetch credentials, this property is verified provided that the device or gateway is auto-provisioned. If it is `false`
a device provisioning notification is sent followed by setting *auto-provisioning-notification-sent* to `true`. This 
is to ensure that the notification is sent at least once.

The newly created credentials are returned to the protocol adapter in the response as if they had been present before.

The following query of the Device Registration API returns the previously generated registration data.

The provisioning is, of course, a one-time action, on subsequent connections the APIs simply return the stored records.

## Automatic Gateway Provisioning

Auto-provisioning of gateways involves the same steps as 
[Automatic Device Provisioning]({{< ref "/concepts/device-provisioning#automatic-device-provisioning" >}}).
An extra attribute namely *auto-provision-as-gateway* is needed in the tenant's trusted CA configuration to enable
auto-provisioning of gateways. If this attribute is set to `true` then the device registry provisions any unregistered 
devices that authenticate with a client certificate issued by this tenant's trusted CA as gateways, provided that the 
attribute *auto-provisioning-enabled* is also set to `true`. Refer to the 
[Device Registry Management API]({{< ref "/api/management#tenants/createTenant" >}})
for more details on the tenant's trusted CA configuration.

## Gateway based Auto-Provisioning

It refers to auto-provisioning of edge devices that are connected via gateways.
Enabling gateway based auto-provisioning requires configuration of the gateway device:

##### Step 1: Configure the Gateway Device

The gateway device must have the corresponding authority (`auto-provisioning-enabled`) set.
Refer to the [Management API]({{< ref "/api/management" >}}) for creating or updating a 
corresponding device.

##### Step 2: Connect an unregistered Device via Gateway to Hono

{{< figure src="auto-provisioning-gateway.svg" alt="A unregistered device connects to a gateway which uses the Device Registration API to provision it" 
title="Automatic Provisioning of a Device via gateway" >}}

The yet unregistered edge device sends telemetry data via a gateway (1) to a protocol adapter which then checks if the 
edge device is already registered by calling the *assert* operation of Device Registration API (2). If the device 
registration service finds that the device isn't registered yet and the gateway has the `auto-provisioning-enabled` 
authority set, it creates the edge device (3). 

Subsequently, after it made sure that it hasn't already done so, it sends an 
[Device Provisioning Notification]({{< ref "/api/event#device-provisioning-notification" >}}) with the  `hono_registration_status` application 
property being set to `NEW` to the AMQP network (4). Once the event has been accepted by the peer (5), the registration 
service marks the event as delivered (6). The persistent flag guarantees that the Device Provisioning Notification is sent 
AT_LEAST_ONCE. 
(*NB*: applications may receive duplicates of the Device Provisioning Notification!).

Finally, the device registration service returns the registration information to the protocol adapter (7) which then 
forwards the telemetry data to the AMQP network (8).

