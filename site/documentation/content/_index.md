---
title: "Documentation"
weight: 120
pre: '&nbsp;<i class="fas fa-book"></i>&nbsp;'
resources:
- src: overview_hono.svg
---

# Documentation 

{{< figure src="overview_hono.svg?width=70pc" title="Data flowing from Devices via Hono to Business Applications and back" >}}

The place of Eclipse Hono&trade; is within an IoT cloud. The goal is simplified device connectivity for _Business Applications_
(or _Solutions_).
It abstracts away the multiple specifics of different device-side connection protocols from a backend application
perspective. Hono defines a small set of APIs to communicate in a unified way with very different devices, whether they
use, for example, connection-oriented protocols such as MQTT and AMQP 1.0 or stateless protocols such as HTTP. As a
result, Hono frees developers of IoT backend applications from the need to know device protocols in detail and allows
them to add or change connection protocols without having to modify their applications.

For each supported connection protocol, Hono contains a microservice called _Protocol Adapter_, which maps the connection
protocol of the device to Hono's APIs. Those APIs are offered via a central messaging system. Each of the messaging APIs has a
so-called _northbound_ API for the Business Applications. The _southbound_ APIs are used by protocol adapters to forward
messages from devices to the applications and vice versa. Messages flowing from devices to Business Applications are
called _downstream_ messages since this is the primary communication direction in practice. The other communication
direction is called _upstream_. These are messages sent from a Business Application to a device via the _Command &
Control_ API.

The APIs defined by Hono for messaging are _Telemetry_, _Event_, and _Command & Control_. The Telemetry API is intended
to send arbitrary data, e.g. from sensors, downstream. Events are downstream messages as well, intended less frequent
but more important messages. Depending on the provided messaging network, events can be persisted and delivered later on
if an application faces a limited downtime. The Command & Control API allows to send messages, like commands, from
northbound applications upstream and optionally return a response from a device.

The [Getting Started]({{< relref "/getting-started" >}}) guide helps to familiarize oneself with Hono by practical example.
If interested in a specific protocol, the corresponding [User Guide]({{< relref "/user-guide" >}}) explains the usage
comprehensively.

To learn the concepts more in-depth, start reading about the [architecture]({{< relref "/architecture" >}})
and Hono's concept of a [Device Identity]({{< relref "/concepts/device-identity" >}}). 
The concept page [Multi-tenancy]({{< relref "/concepts/tenancy" >}}) explains how Hono allows isolating multiple sets 
of devices and data. 
[Connecting Devices]({{< relref "/concepts/connecting-devices" >}}) shows different ways to connect a device 
(directly or via a gateway), and [Device Provisioning]({{< relref "/concepts/device-provisioning" >}}) explains some 
options to register devices in Hono.

Topics related to the operation of Hono are explained in the [Admin Guide]({{< relref "/admin-guide/" >}})
and the [Deployment]({{< relref "/deployment" >}}) documentation.

