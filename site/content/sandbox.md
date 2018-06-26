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

* The sandbox is intended for **testing purposes only**. Under no circumstances should it be used for any production use case. It is also **not allowed** to register with nor publish any personally identifiable information to any of the sandbox's services.
* You can use the sandbox without revealing who you are or any information about yourself. The APIs of the Device Registry running on the sandbox can be used anonymously for creating tenants, register devices and add credentials. However, data can only be added but **cannot be updated or removed** using the corresponding APIs. This is to prevent others from tampering with your tenants/devices/credentials. In order to minimize the risk of dissemination of data, all tenants, devices and credentials are **deleted periodically**.
* We do not collect nor share with third parties any of the data you provide when registering tenants, devices and credentials. We also do not inspect nor collect nor share with third parties any of the data your devices publish to the sandbox.
* **Play fair!** The sandbox's computing resources are (quite) limited. The number of devices that can be registered per tenant is therefore limited to 100.
* The sandbox will be running the latest Hono release or milestone (if available). However, we may also deploy a more recent nightly build without further notice.
* The sandbox exposes its API endpoints on both a TLS secured as well as an unsecured port. The secure ports use a Let's Encrypt certificate so you should not need to configure a specific trust store on your client in order to interact with them. Please note that when using the unsecured ports, the information you exchange with the sandbox might be exposed to eavesdroppers. We therefore **strongly suggest** that you use the secure ports only, if possible!
  When using the [Hono client]({{< relref "/admin-guide/hono-client-configuration.md" >}}) to access the sandbox' Telemetry and/or Event APIs, make sure to not set a trust store explicitly but instead set the *tlsEnabled* property to `true`. The example below can be used to consume telemetry messages from the sandbox:

    ```
    $> java -jar target/hono-example-0.6-exec.jar --hono.client.host=hono.eclipse.org --hono.client.port=15671 --hono.client.tlsEnabled=true --hono.client.username=consumer@HONO --hono.client.password=verysecret --spring.profiles.active=receiver
    ```
  Note that  only the *receiver* profile is activated but not the *ssl* profile.
* In order to minimize the risk of collisions of device identities and credentials and to reduce the risk of others *guessing* your identifiers, you are advised to use **non-trivial, hard-to-guess** tenant and device identifiers (e.g. a UUID).
* The Apache Artemis instance we use for brokering events is configured with a maximum queue size of 1MB, i.e. you can only buffer up to 1 MB of events (per tenant) without having any consumer connected that actually processes the events. Once that limit is reached, no more events will be accepted by the protocol adapters for the corresponding tenant. In addition to that, events that are not consumed will automatically be removed from the queue(s) after five minutes.
* The Grafana dashboard is not publicly available.

{{% warning %}}
Everybody who knows your tenant identifier will be able to consume data published by your devices and everybody who also knows the device identifier can read the registration information of that device.
{{% /warning %}}
