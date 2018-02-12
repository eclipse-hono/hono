+++
title = "Sandbox"
menu = "main"
weight = 160
+++

We are providing a publicly accessible Eclipse Hono&trade; *sandbox* environment at `hono.eclipse.org`.
The main purpose of the system is to provide an environment for experimenting with devices and how to connect them to Hono without the need for setting up a local instance.

The sandbox hosts a Hono instance consisting of the same components as described in the [Getting started Guide]({{< relref "getting-started.md" >}}).
All services are exposed via the same ports as used in the guide.

## Take note

* The *sandbox* is intended for **testing purposes only**. Under no circumstances it should be used for any real life use cases.
* **Play fair!** The sandbox's computing resources are (quite) limited. The number of devices that can be registered per tenant is therefore limited to 100.
* We will update the sandbox to a recent Hono nightly build frequently and without further notice. All device registration data and credentials will be scrubbed as part of the update process.
* The certificates used for securing the TLS connections are signed with Hono's demo CA key. When connecting to any of the sandbox services using TLS you therefore need to add the [CA certificate](https://raw.githubusercontent.com/eclipse/hono/master/demo-certs/certs/trusted-certs.pem) to your trust anchor.
* Devices and credentials can be added but they **cannot be updated or removed**. This is to prevent others from tampering with your devices/credentials.
* The Grafana dashboard is not publicly available.
* In order to minimize the risk of collisions of device identities and credentials, you should use a **non-trivial, hard-to-guess tenant name** (e.g. a UUID).
* The Apache Artemis instance we use for brokering events is configured with a maximum queue size of 1MB, i.e. you can only buffer up to 1 MB of events without having any consumer connected that actually processes the events. Once that limit is reached, no more events will be accepted by the protocol adapters for the corresponding tenant.

