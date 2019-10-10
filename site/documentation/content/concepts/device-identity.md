+++
title = "Device Identity"
weight = 182
+++

This page describes how devices are represented and identified throughout Hono and its APIs.
<!--more-->

The main purpose of Hono is to provide a uniform API for applications to interact with devices, regardless of the particular communication protocol the devices natively use. In order to do so, Hono uses a unique *logical* identifier to refer to each device individually.

Hono does not make any assumptions about the format of a device identifier (or *device-id* for short). It basically is a string which is defined at the time a device is registered. Once registered, the device can be referred to by this identifier when using Hono's APIs until the device is unregistered. 

## Tenant

Hono supports the logical partitioning of devices into groups called [*tenants*]({{< relref "/concepts/tenancy" >}}). Each tenant has a unique identifier, a string called the *tenant-id*, and can be used to provide a logical grouping of devices belonging e.g. to the same application scope or organizational unit. Each device can thus be uniquely identified by the tuple (*tenant-id*, *device-id*). This tuple is broadly used throughout Hono's APIs when addressing a particular device.

## Device Registration

Hono components use the [Device Registration API]({{< relref "/api/device-registration" >}}) to access device registration information. The API defines the *assert Registration* operation for verifying a device's registration status.
In many real world scenarios there will already be a component in place which keeps track of devices and which supports the particular *provisioning process* being used to bring devices into life. In such cases it makes sense to simply implement the Device Registration API as a *facade* on top of the existing component.

In addition to that, Hono defines a [Device Registry Management API]({{< relref "/api/management" >}}), which can be implemented to take advantage of standardized  operations for managing devices and credentials. This API is optional because Hono components do not require it during runtime. 

For demonstration purposes, Hono comes with a [simple default implementation]({{< relref "/admin-guide/device-registry-config.md" >}}) of both APIs which keeps all data in memory only. Therefore it is not supposed to be used in production scenarios. For the future, it is planned that a production-ready Device Registry will be released as part of Hono that implements both APIs.

## Device Authentication

Devices connect to protocol adapters in order to publish telemetry data or events. Downstream applications consuming this data often take particular actions based on the content of the messages. Such actions may include simply updating some statistics, e.g. tracking the average room temperature, but may also trigger more serious activities like shutting down a power plant. It is therefore important that applications can rely on the fact that the messages they process have in fact been produced by the device indicated by a message's source address.

Hono relies on protocol adapters to establish a device's identity before it is allowed to publish downstream data or receive commands. Conceptually, Hono distinguishes between two identities

1. an identity associated with the authentication credentials (termed the *authentication identity* or *auth-id*), and
1. an identity to act as (the *device identity* or *device-id*).

A device therefore presents an *auth-id* as part of its credentials during the authentication process which is then resolved to a *device identity* by the protocol adapter on successful verification of the credentials.

In order to support the protocol adapters in the process of verifying credentials presented by a device, the [Credentials API]({{< relref "/api/credentials" >}}) provides means to look up *secrets* on record for the device and use this information to verify the credentials.

The Credentials API supports registration of multiple sets of credentials for each device. A set of credentials consists of an *auth-id* and some sort of *secret* information. The particular *type* of secret determines the kind of information kept. Please refer to the [Standard Credential Types]({{< relref "/api/credentials#standard-credential-types" >}}) defined in the Credentials API for details. Based on this approach, a device may be authenticated using different types of secrets, e.g. a *hashed password* or a *pre-shared key*, depending on the capabilities of the device and/or protocol adapter.

Once the protocol adapter has resolved the *device-id* for a device, it uses this identity when referring to the device in all subsequent API invocations, e.g. when forwarding telemetry messages downstream to the AMQP Messaging Network.
