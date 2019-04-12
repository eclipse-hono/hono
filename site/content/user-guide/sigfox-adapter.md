+++
title = "Sigfox Adapter"
weight = 260
+++

The Sigfox protocol adapter exposes an HTTP endpoint for connecting up with
the Sigfox backend for publishing telemetry, events and use command & control.
<!--more-->

{{% note title="Tech preview" %}}
This protocol adapter is not considered production ready. Its APIs might still
be subject to change without warning.
{{% /note %}}

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
      "hash-function": "sha-512",
      "pwd-hash": "yHRiqBGqGlGBctRvzX98JpeTTk0ao9BfzR714G7737y8oOuRsFzJYGC0846lFy96FWev8sSb7MGeSqoJi3I6mw==",
      "salt": "j9PyA6Y9obw="
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

Create a new "Custom" callback, with the following settings:

* **Type**: `DATA` - `UPLINK`
* **Channel**: `URL`
* **Url pattern**: `https://sigfox%40DEFAULT_TENANT:test12@iot-sigfox-adapter.my.hono/data/telemetry?device={device}&data={data}`
* **Use HTTP Method**: `GET`
* **Send SNI**: ☑ (Enabled)

## Consuming data

Use the standard way of consuming Hono messages.

## Known bugs and limitations

* Command and control is currently not supported
* Events are currently not supported
* Only the simple `URL` and only *data* (no *service* or *device events* are
  currently supported.
