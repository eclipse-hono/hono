+++
title = "Authentication/Authorization"
weight = 550
+++

This page describes how authentication and authorization of devices, consumers (back end applications) and system
components works in Hono.
<!--more-->

## Requirements

1. Devices are authenticated and authorized when they connect to a protocol adapter.
1. Consumers are authenticated and authorized when they connect to a *Dispatch Router* instance.
1. System components are authenticated and authorized when they connect to each other.
1. Credentials and authorization rules can be managed centrally, i.e. credentials and rules do not need to be
   configured manually for each component.

## How it works today

The following diagram provides an overview of the components involved in use cases requiring authentication and
authorization.

{{< figure src="Hono-Auth-Overview-Today.drawio.svg" >}}

### Device Auth

Both the HTTP adapter as well as the MQTT adapter require devices to authenticate during connection establishment by
default. Both rely on the [Credentials API]({{< relref "/api/credentials" >}}) to help in verifying credentials
provided by a device. Please refer to [Device Authentication]({{< relref "/concepts/device-identity#device-authentication" >}})
for a general overview of Hono's approach to authenticating devices and to the
[protocol adapter user guides]({{< relref "/user-guide" >}}) for specifics regarding how devices can authenticate to
the corresponding protocol adapters.

### System Component Auth

Client components opening an AMQP connection to a server component are authenticated using SASL PLAIN as specified in
[RFC 4422](https://tools.ietf.org/html/rfc4422). The server component takes the authentication information provided by
the client component and opens a connection to the *Auth Server*, using the credentials provided by the client in its
SASL PLAIN exchange with the server component. On successful authentication the *Auth Server* issues a JSON Web Token
(JWT) asserting the client's identity and its granted authorities to the server component. The server component then
*attaches* this token to its AMQP connection with the client and from then on uses it to make authorization decisions
regarding the client's requests. See [Authentication API]({{< relref "/api/authentication" >}}) for details regarding
the authentication process and the format of the tokens issued by the *Auth Server*.

Based on the components shown above, the following sequence diagram shows how the *MQTT Adapter* connects to the
*Device Registry* and gets authenticated transparently using the *Auth Server*.

{{< figure src="MQTT-Adapter-authentication-today.png" width="80%" >}}

Client components are authorized whenever they open a new AMQP link on an existing connection to the server. When a
client tries to open a receiver link, the server checks if the client is authorized to *read* from the source address
the client has specified in its AMQP *attach* frame. Analogously, when a client tries to open a sender link, the server
checks if the client is authorized to *write* to the target address from the client's *attach* frame.

Service implementations may additionally authorize individual (request) messages received from the client, e.g. based
on the message's *subject* property which is used by Hono's AMQP 1.0 based APIs to indicate the operation to invoke.
In such a case the server checks if the client is authorized to *execute* the operation indicated by the message
*subject* on the link's target address.

### Application Auth

*Business Applications* connect to the Messaging Infrastructure in order to consume telemetry data and events and send
commands to devices. It is therefore the responsibility of the Messaging Infrastructure to properly authenticate and
authorize the application.

Hono supports both Apache Kafka&trade; as well as AMQP 1.0 based Messaging Infrastructure. In the latter case, the
example deployment uses Apache Qpid&trade; Dispatch Router and Apache ActiveMQ&trade; Artemis as the implementation of
the Messaging Infrastructure.

#### Kafka based Messaging Infrastructure

When using the Kafka based example deployment the Kafka broker is responsible for authenticating connections from
applications. For this purpose, the broker can be configured to authenticate clients using arbitrary SASL mechanisms.
Access to addresses for receiving messages can be restricted to certain identities.

The Kafka broker instance which is used in the example deployment is configured to use the SASL PLAIN mechanism for
authenticating clients based on a static list of user names and corresponding passwords.

Please refer to the [Kafka documentation](https://kafka.apache.org/documentation/#security) for details regarding
configuration of *Kafka broker* security.

#### AMQP 1.0 based Messaging Infrastructure

When using the AMQP 1.0 based example deployment, an Apache Qpid&trade; Dispatch Router and an Apache ActiveMQ&trade;
Artemis broker instance are used as the implementation of the Messaging Infrastructure. The Dispatch Router instance
is responsible for authenticating connections from applications. For this purpose, the router can be configured to
authenticate clients using arbitrary SASL mechanisms. Access to addresses for receiving messages can be restricted
to certain identities.

The Dispatch Router instance used in the example deployment is configured to delegate authentication of
clients to the *Auth Server* by means of its *Auth Service Plugin* mechanism. This mechanism works in a very similar
way as described above for the authentication of system components. The main difference is that the clients'
authorities are not transferred by means of a JSON Web Token but instead are carried in a property of the Auth Server's
AMQP *open* frame.

Please refer to the [Dispatch Router documentation](https://qpid.apache.org/components/dispatch-router/index.html) for
details regarding configuration of *Dispatch Router* security.

### Management of Identities and Authorities

The identities and corresponding authorities that the *Auth Server* uses for verifying credentials and issuing tokens
are defined in a configuration file read in during start-up of the *Auth Server*. These authorities are used for
authenticating and authorizing system components. They are also used for authenticating *Business Applications* when
using AMQP 1.0 based Messaging Infrastructure.

An example configuration file can be found in the [Auth Server code base](https://github.com/eclipse-hono/hono/blob/master/services/auth-base/src/test/resources/authentication-service-test-permissions.json).

## Future Approach

In the long run Hono will still use tokens for authenticating clients but will use a policy based approach for
authorizing requests, i.e. authorization decisions will be made by a central *policy enforcement* component.
Hono services will pass in the client's token, the resource being accessed and the intended action along with
potentially other attributes to the policy enforcement component which will then make the authorization decision
based on the configured rules (policy) and return the outcome to the component.
