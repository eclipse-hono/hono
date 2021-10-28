+++
title = "Sigfox Adapter"
weight = 260
+++

The Sigfox protocol adapter exposes an HTTP endpoint for connecting up with
the Sigfox backend for publishing telemetry, events and use command & control.
<!--more-->

{{% notice info %}}
This protocol adapter is not considered production ready. Its APIs might still
be subject to change without warning.
{{% /notice %}}

## Pre-requisites

This Sigfox adapter only connects to the Sigfox backend system
(`backend.sigfox.com`). It does not allow direct access to Sigfox devices.

So you need to set up your Sigfox devices on `backend.sigfox.com` and then
configure the callbacks connect to your installation of Hono.

## Devices and credentials

In a nutshell, the Sigfox adapter requires a single *device* identity, acting
as a gateway device. This identity will be used to connect to Hono. All devices
registered with Sigfox (the actual Sigfox devices), will be registered in Hono
to allow this first identity as their gateway device.

## Setup example

The following sub-sections walk you through an example setup.

### Registering devices

This example assumes that the Sigfox protocol adapter is available as
`https://iot-sigfox-adapter.my.hono`.

Create a new *gateway device* with the following registration:

~~~json
{
  "device-id": "sigfox-backend"
}
~~~

Create new credentials for the *gateway device*. For example, using
a username of `sigfox` and a password of `test12`:

~~~json
{
  "auth-id": "sigfox",
  "device-id": "sigfox-backend",
  "enabled": true,
  "secrets": [
    {
      "pwd-plain": "test12"
    }
  ],
  "type": "hashed-password"
}
~~~

Create a new *device*, referencing the previous *gateway device*. The
*device id* must be your Sigfox device ID (e.g. `1AB2C3`):

~~~json
{
  "device-id": "1AB2C3",
  "via": "sigfox-backend"
}
~~~

### Setting up callbacks

Log in to the Sigfox backend at https://backend.sigfox.com and then open up
the view `Device Type` -> `Callbacks`.

Create a new "Custom" callback, with the following settings
(replacing `<TENANT>` with the name of the tenant):

* **Type**: `DATA` – `UPLINK`
* **Channel**: `URL`
* **Url pattern**: `https://iot-sigfox-adapter.my.hono/data/telemetry/<TENANT>?device={device}&data={data}`
* **Use HTTP Method**: `GET`
* **Headers**
  * `Authorization` – `Basic …` (see note below)
* **Send SNI**: ☑ (Enabled)

{{% notice tip %}}
At the moment you need to manually put in the `Authorization` header,
you cannot put the credentials into the URL, as there is a bug in the
Sigfox backend, which cannot be fixed by Hono. The backend does not
properly escape the `@` character, and thus sends `foo%40tenant`
instead of `foo@tenant` to the Hono protocol adapter.

As a workaround, you can explicitly set the `Authorization` header to a
value of `Basic <base64 encoded credentials>`. You can encode the
credentials using:

~~~sh
echo -n "sigfox@tenant:password" | base64
~~~

To get the full value, including the `Basic` you may use:

~~~sh
echo "Basic $(echo -n "sigfox@tenant:password" | base64)"
~~~
{{% /notice %}}

### Enabling command & control

It is possible to enable command & control as well. For this you need to:

* Switch the **Type** of the `DATA` callback from `UPLINK` to `BIDIR`
* Add the `ack` query parameter to the **Url pattern**, e.g. `https://iot-sigfox-adapter.my.hono/data/telemetry/<TENANT>?device={device}&data={data}&ack={ack}`

{{% notice tip %}}
Sigfox allows only a very specific payload in command messages. You must send
exactly 8 bytes of data. It only supports *one way commands*.
{{% /notice %}}

## Events

You can send events by using the path `/data/event` on the URL.

## Consuming data

Use the standard way of consuming Hono messages.

## Known bugs and limitations

* Only the simple `URL` and only *data* (no *service* or *device events* are
  currently supported.
