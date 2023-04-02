---
linkTitle: "Sandbox"
title: "Try Hono without installing locally"
description: "The Sandbox environment can be used to play around with Hono's APIs without the need to set up a Kubernetes cluster and install Hono manually."
menu: "main"
weight: 160
---

We are providing a publicly accessible Eclipse Hono&trade; *sandbox* environment at `hono.eclipseprojects.io`.
The main purpose of the system is to provide an environment for experimenting with devices and how to connect them
to Hono without the need for setting up a local instance.

## Accessing the Sandbox

The sandbox hosts a Hono instance consisting of the components listed in the table below.
The components can be accessed as described in the [Getting started Guide]({{% doclink "/getting-started/" %}}).

| Component | Service Port(s) |
| :-------- | :-------------- |
| Device Registry | `28443` (HTTP 1.1/TLS) |
| AMQP Protocol Adapter | `5671` (AMQP 1.0/TLS) |
| CoAP Protocol Adapter | `5684` (CoAP/DTLS) |
| HTTP Protocol Adapter | `8443` (HTTP 1.1/TLS) |
| MQTT Protocol Adapter | `8883` (MQTT 3.1.1/TLS) |
| Messaging Infrastructure (AMQP 1.0) | `15671` (AMQP 1.0/TLS) |
| Messaging Infrastructure (Kafka) | `9094` (Kafka/TLS) |

## Take note

* The sandbox is intended for **testing purposes only**. Under no circumstances should it be used for any production
  use case. It is also **not allowed** to register with nor publish any personally identifiable information to any of
  the sandbox's services.
* You can use the sandbox without revealing who you are or any information about yourself. The APIs of the Device
  Registry running on the sandbox can be used anonymously for creating tenants, register devices and add credentials.
  In order to minimize the risk of dissemination of data, all tenants, devices and credentials are **deleted
  periodically**.
* We do not collect nor share with third parties any of the data you provide when registering tenants, devices and
  credentials. We also do not inspect nor collect nor share with third parties any of the data your devices publish
  to the sandbox.
* **Play fair!** The sandbox's computing resources are (quite) limited. The number of devices that can be registered
  per tenant is therefore limited to 10.
* The sandbox will be running the latest Hono release or milestone (if available). However, we may also deploy a more
  recent nightly build without further notice.
* In order to minimize the risk of collisions of device identities and credentials and to reduce the risk of others
  *guessing* your identifiers, you are advised to use **non-trivial, hard-to-guess** tenant and device identifiers
  (e.g. a UUID).
* The Apache Artemis instance we use for brokering events is configured with a maximum queue size of 1MB, i.e. you
  can only buffer up to 1 MB of events (per tenant) without having any consumer connected that actually processes the
  events. Once that limit is reached, no more events will be accepted by the protocol adapters for the corresponding
  tenant. In addition to that, events that are not consumed will automatically be removed from the queue(s) after
  five minutes.
* The sandbox exposes Hono's API endpoints on TLS (>= 1.2) secured ports which are configured with a Let's Encrypt
  certificate so you should not need to configure a specific trust store on your client in order to interact with them.
* The command line client binary is available from the [downloads page]({{< relref "downloads#binaries" >}})
  and can be used to consume telemetry/event messages from the sandbox and send commands to devices as described in
  the [Getting Started Guide]({{% doclink "/getting-started/" %}})

{{% warning %}}
Everybody who knows your tenant identifier will be able to consume data published by your devices and everybody who
also knows the device identifier can read the registration information of that device.
{{% /warning %}}
