---
title: "Device Provisioning"
weight: 250
resources:
  - src: auto-provisioning.svg
---

This page describes how devices are provisioned in Hono, i.e. how their digital representation is generated.
For each device, registration information is stored that defines a device identity. 
Each device belongs to exactly one tenant. Each device must have at least one set of credentials that are used to authenticate to Hono.

To get an understanding of what is meant by the terms *tenant*, *device registration* and *credentials*, 
it is recommended to read the [Device Identity]({{< ref "/concepts/device-identity" >}} page first.

So in order to use a device with Hono, it has to be provisioned. 
This means that registration information and at least one credential record must be stored in the device registry.

There are different ways to perform device provisioning.

## Manual Device Provisioning

Devices can be provisioned using Hono's [Management API]({{< ref "/api/management" >}}) via REST.

If the desired *tenant* does not yet exist, it must be created first. 
How to do this is described in the [User Guide]({{< ref "/user-guide/device-registry#add-a-tenant" >}}) of the Device Registry.

The actual Device Provisioning is then performed as described under [Register Device]({{< ref "/user-guide/device-registry#register-device" >}}). 
This creates both a device registration and an (empty) credentials record.
The last step is to add real credentials, as described in [Update Credentials for a Device]({{< ref "/user-guide/device-registry#update-credentials-for-a-device" >}}).


## Automatic Device Provisioning

The term *Auto-Provisioning* denotes a feature of Hono where the Device Registry automatically generates 
the credentials and registration information for a device the first time it connects.
Auto-Provisioning is supported by Hono's protocol adapters for devices that authenticate with client certificates.
The feature can be enabled per certificate authority (CA) at the tenant.


### Prerequisites

Hono does not require a specific Device Registry implementation, but only specifies a set of APIs that must be provided by a compatible implementation.
Since the main part of the *Auto-Provisioning* has to be done by the Device Registry, the used implementation must explicitly support this feature.


### Sequence of steps in Auto-Provisioning

*Auto-Provisioning* consists of two steps. 

#### Step 1: Configure the Tenant

The CA to be used by the devices needs to be configured. The following tasks must be performed:

1. Create tenant
2. Configure the trusted CA for the tenant
3. Enable the feature for the CA

If the Device Registry implementation provides the [Management API]({{< ref "/api/management" >}}), this could be done in a single step. 
For details refer to the [Tenant API specification]({{< ref "/api/tenant#trusted-ca-format" >}}).

#### Step 2: Connect an unregistered Device to Hono

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
4. Optional: Provision device in external systems

The newly created credentials are returned to the protocol adapter in the response as if they had been present before.

The following query of the Device Registration API returns the previously generated registration data.

The provisioning is, of course, a one-time action, on subsequent connections the APIs simply return the stored records.
