+++
title = "Frequently Asked Questions"
menu = "main"
menuTitle = "FAQs"
weight = 550
pre = '<i class="far fa-question-circle"></i> '
+++


## Technical Questions


#### Why do I get `HTTP/1.1 503 Service Unavailable` when sending messages to the HTTP protocol adapter?

Please check if you have a [consumer connected]({{< relref "getting-started.md#starting-a-consumer" >}}) 
and that you do not filter out the message type.  


#### Why do I get the exception `io.vertx.core.VertxException: OpenSSL is not available`?

Please check if you have set the property `nativeTlsRequired` in the protocol adapter's configuration. The default Hono
containers do not contain `netty-tcnative`. To enable this option, please follow the explanation in the 
[Admin Guide]({{< relref "admin-guide/secure_communication.md#using-openssl" >}}) or build your own images.


#### Why do I see `ConnectionLimitManager - Connection limit (<VALUE>) exceeded` in the logs of a protocol adapter? 

The configured maximum number of concurrent connections is exceeded and the protocol adapter refuses to accept more 
connections to prevent running out of resources. This limit is either configured on the protocol adapter
([MQTT]({{< relref "admin-guide/mqtt-adapter-config.md#service-configuration" >}}),
[AMQP]({{< relref "admin-guide/amqp-adapter-config.md#service-configuration" >}})) or if not set, 
the protocol adapter determines a reasonable value based on the available resources like memory and CPU.


#### Why do I see `MemoryBasedConnectionLimitStrategy - Not enough memory` in the logs of a protocol adapter? 

The protocol adapter can not allocate enough memory. Please provide more memory. To try it anyways, configure the 
maximum number of concurrent connections, as documented in the Admin Guides of the protocol adapter
([MQTT]({{< relref "admin-guide/mqtt-adapter-config.md#service-configuration" >}}),
[AMQP]({{< relref "admin-guide/amqp-adapter-config.md#service-configuration" >}})).



#### How do I use client certificates for authentication?

Make sure that you are able to connect to the respective protocol adapter with TLS 
(see the [Admin Guide]({{< relref "admin-guide/secure_communication.md#using-openssl" >}}) for configuration).
[Here](https://blog.bosch-si.com/developer/x-509-based-device-authentication-in-eclipse-hono/) is an article, that 
provides a complete walk-through guide for all required steps. 
Additionally you can use and adapt the script for the creation of demo certificates in the Hono repository.
More information can be found in the User Guide of the protocol adapter 
([MQTT]({{< relref "user-guide/mqtt-adapter.md#authentication" >}}),
[HTTP]({{< relref "user-guide/http-adapter.md#device-authentication" >}})).



## Organizational Questions

#### Will you add feature _x_ to Hono?

To find out about the future development you can have a look at the [Roadmap]({{< relref "community/road-map.md" >}}) or
[get in touch]({{< relref "community/get-in-touch.md" >}}) with the Hono developers.
