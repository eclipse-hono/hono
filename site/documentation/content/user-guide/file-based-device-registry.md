+++
title = "File Based Device Registry"
weight = 206
+++

The Device Registry component provides exemplary implementations of Hono's [Tenant API]({{< relref "/api/tenant" >}}), [Device Registration API]({{< relref "/api/device-registration" >}}) and [Credentials API]({{< relref "/api/credentials" >}}).

<!--more-->
As such it exposes AMQP 1.0 based endpoints for retrieving the relevant information and persists data in the local filesystem. 

In addition, the Device Registry also exposes HTTP resources for managing the contents of the registry according to the [Device Registry HTTP API]({{< relref "/api/management" >}}).

{{% warning %}}
The Device Registry is not intended to be used in production environments. In particular, access to the HTTP resources described below is not restricted to authorized clients only.

The resources have been designed to provide convenient access to the registry's content using command line tools like *curl* or *HTTPie*.
{{% /warning %}}

## Managing Tenants

Please refer to the [Device Registry Management API]({{< relref "/api/management#tenants" >}}) for information about managing tenants.

## Managing Devices

Please refer to the [Device Registry Management API]({{< relref "/api/management#devices" >}}) for information about managing devices.

## Managing Credentials

Please refer to the [Device Registry Management API]({{< relref "/api/management#credentials" >}}) for information about managing credentials.
