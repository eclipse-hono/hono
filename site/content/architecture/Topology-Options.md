+++
title = "Topology Options"
weight = 510
+++

This page describes the system topology of Eclipse Hono&trade; and some of the considered alternatives.
<!--more-->

The diagram below illustrates the system topology approach we are taking for Hono.

![Hybrid connection model](../Hybrid_Connection_Model.png)

Protocol adapters connect to a telemetry endpoint node in order to upload telemetry data while consumers of the data (e.g. Business Applications) connect to a Dispatch Router node for retrieving the data.
We have deliberately chosen an asymmetric approach for several reasons:
* We want to take advantage of Apache Qpid's Dispatch Router's flexible message routing capabilities. This allows a user to route telemetry messages directly to consumers as well as using a brokered approach where all telemetry messages are forwarded to a message broker instead, e.g. in order to provide for store and forward semantics.
* Using a dedicated endpoint component allows for implementing flexible authorization policies independent from a particular message broker.

# Other topologies under consideration

We also take the following topology alternatives into considerations. The following sections contain options for Hono's overall system topologies. The options mainly differ in the approach taken for connecting clients to Hono and how existing MOM infrastructure is used.

## Direct Connection

In this option clients (*Protocol Adapters* and *Solutions*) connect directly to Hono (or any of its sub-components) using AMQP 1.0. Hono is responsible for managing the connections and implementing Hono's API. Hono employs dedicated messaging infrastructure for *buffering* the northbound telemetry data to be delivered to *Solutions* and for delivering southbound commands to devices connected via *Protocol Adapters*. It is important to note that one reason for using different messaging infrastructure for northbound and southbound traffic is that in IoT scenarios the telemetry data will most probably be orders of magnitude larger in volume and frequency than the command and control messages.

![Direct Connection](../Connect_directly.png)

Main advantages of this approach:
* Full (programmatic) control over connection establishment (and in particular authorization). Hono can dynamically create AMQP sessions and links based on the client's identity and authorizations.
* No additional (remote) communication between Hono and an AMQP protocol router necessary.

Main drawbacks include:
* Requires implementation of AMQP connection management. While we can use existing frameworks for this (e.g. Apache Qpid Proton), there will probably some aspects not covered by the framework used and implementing a spec correctly (even if AMQP 1.0 seems to be written well) is not that easy.

### Connection via embedded Router

This is just a variant of the previous option. In this case an *embedded router component* runs in the same process as Hono and is used for managing the AMQP 1.0 connections with clients.

![Connect via embedded Router](../Connect_via_embedded_Router.png)

Main advantages include:
* No need to implement connection management, depending on the router component's capabilities we might be able to leverage the router's Java API to control its behavior at runtime.

Main drawbacks include:
* Approach is limited to using a router component that can be run embedded in a Java process. Potential candidates include ActiveMQ, ActiveMQ Artemis, Qpid Java.

## Connection via dedicated Router

In this option clients connect to a dedicated AMQP 1.0 router component which is responsible for managing the connections. Hono also connects to this router component as an AMQP client. The router component forwards API messages from clients to Hono which provides the implementation of the API. Hono leverages existing MOM infrastructure for implementing the northbound telemetry data channel and the southbound command & control endpoints representing the devices.

![Connect via dedicated Router](../Connect_via_dedicated_Router.png)

Main drawbacks include:
* Less control over connection establishment and authorization. This may require the usage of a separate AMQP session for exchanging *flow control* messages in order to explicitly trigger the dynamic creation of client specific resources.

## Connection via Message Broker

This option is similar to the *Connection via dedicated Router* option with the main difference being that the routing of incoming messages from clients is not done by a dedicated (separate) router component but instead by the same (AMQP 1.0 based) MOM infrastructure that is used for implementing the northbound telemetry data channel and the southbound command & control endpoints representing the devices. Hono's main responsibility in this case is implementing the API.

![Connect via Message Broker](../Connect_via_MOM.png)

Main drawbacks include:
* Requires usage of a MOM that supports AMQP 1.0 natively.
