+++
title = "Authentication/Authorization"
weight = 550
+++

This page describes how authentication and authorization of devices, consumers (back end applications) and system components works in Hono.
<!--more-->

## Requirements

1. Devices are authenticated and authorized when they connect to a protocol adapter.
1. Consumers are authenticated and authorized when they connect to a *Dispatch Router* instance.
1. System components are authenticated and authorized when they connect to each other.
1. Credentials and authorization rules can be managed centrally, i.e. credentials and rules do not need to be configured manually on each component.

## How it works today

The following diagram provides an overview of the components involved in use cases requiring authentication and authorization.

{{< figure src="../Hono-Auth-Overview-Today.jpg" >}}

### Device Auth

Both the HTTP adapter as well as the MQTT adapter require devices to authenticate during connection establishment by default. Both rely on the [Credentials API]({{< relref "api/Credentials-API.md" >}}) to help in verifying credentials provided by a device. See [Device Authentication]({{< relref "concepts/device-identity.md" >}}) for a general overview of Hono's approach to authenticating devices and [HTTP adapter]({{< relref "user-guide/http-adapter.md" >}}) and [MQTT adapter]({{< relref "user-guide/mqtt-adapter.md" >}}) for specifics regarding how devices using HTTP and MQTT can authenticate to the corresponding protocol adapters.

### System Component Auth

Client components opening an AMQP connection to a server component are authenticated using SASL PLAIN as specified in [RFC 4422](https://tools.ietf.org/html/rfc4422). The server component takes the authentication information provided by the client component and opens a connection to the *Auth Server*, using the credentials provided by the client in its SASL PLAIN exchange with the *Auth Server*. On successful authentication the *Auth Server* issues a JSON Web Token (JWT) asserting the client's identity and its granted authorities to the server component. The server component *attaches* this token to the AMQP connection with the client and from then on uses it to make authorization decisions regarding the client's requests. See [Authentication API]({{< relref "api/Authentication-API.md" >}}) for details regarding the authentication process and the format of the tokens issued by the *Auth Server*.

Based on the components shown above, the following sequence diagram shows how the *MQTT Adapter* connects to the *Device Registry* and gets authenticated transparently using the *Auth Server*.

{{< figure src="../MQTT-Adapter-authentication-today.png" width="80%" >}}

Client components are authorized whenever they open a new AMQP link on an existing connection to the server. When a client tries to open a receiver link, the server checks if teh client is authorized to *read* from the source address the client has specified in its AMQP *attach* frame. Analogously, when a client tries to open a sender link, the server checks if the client is authorized to *write* to the target address from the client's *attach* frame.

Service implementations may additionally authorize individual (request) messages received from the client, e.g. based on the message's *subject* property which is used by Hono's AMQP 1.0 based APIs to indicate the operation to invoke. In such a case the server checks if the client is authorized to *execute* the operation indicated by the message *subject* on the link's target address.

### Consumer Auth

*Business Applications* connect to the AMQP 1.0 Messaging Network (represented by the *Dispatch Router* in the diagram above) in order to consume telemetry data and events. Authentication of consumers is done as part of establishing the AMQP connection using SASL PLAIN as specified in [RFC 4422](https://tools.ietf.org/html/rfc4422).

Consumers are authorized whenever they open a new AMQP link to the *Dispatch Router* for reading from a source address or writing to a target address.

### Management of Identities and Authorities

The identities and corresponding authorities that the *Auth Server* uses for verifying credentials and issuing tokens are defined in a configuration file (`services/auth/src/main/resources/permissions.json`) read in during start-up of the *Auth Server*.

The identities and authorities allowed to access the *Dispatch Router* component are set in a configuration file (`dispatchrouter/qpid/qdrouter-with-broker.json`) read in during startup of the component. The default configuration file authorizes the `consumer@HONO` user to consume both telemetry data as well as events of any tenant. The default password for this user is `verysecret`. 

Please refer to the [Dispatch Router configuration guide](http://qpid.apache.org/releases/qpid-dispatch-0.8.0/man/qdrouterd.conf.html) and the [Policy documentation](https://github.com/apache/qpid-dispatch/blob/0.8.x/doc/book/policy.adoc) for details regarding configuration of *Dispatch Router* security.

In a next step we will integrate the *Dispatch Router* with the token based authentication scheme used by the other Hono components in order to provide for seamless identity and authority management across the whole system.

## Future Approach

In the long run Hono will still use tokens for authenticating clients but will use a policy based approach for authorizing requests, i.e. authorization decisions will be made by a central *policy enforcement* component. Hono services will pass in the client's token, the resource being accessed and the intended action along with potentially other attributes to the policy enforcement component which will then make the authorization decision based on the configured rules (policy) and return the outcome to the component.
