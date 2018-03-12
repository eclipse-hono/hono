+++
title = "Hono Client Configuration"
weight = 340
+++

The Hono Client factory can be used to create specific clients for accessing services implementing one or more of Hono's APIs.
<!--more-->

The factory uses environment variables and/or command line options to configure the connection to the service and the caching of responses to service invocations. All variables used for configuring a particular Hono Client factory instance share a common *prefix*. This way, multiple sets of variables can be used to configure multiple factories for connecting to different service endpoints without interfering with each other. For example, the set of variables for configuring the client factory for the Device Registration service may use the common prefix `HONO_REGISTRATION` whereas the set for configuring the factory for the Credentials service may use `HONO_CREDENTIALS`.

## Connection Properties

The following table provides an overview of the configuration variables and corresponding command line options for configuring the AMQP connection to the service.

The variable names contain `${PREFIX}` as a placeholder for the particular *common prefix* being used. The `${prefix}` placeholder used in the command line option name is the same as `${PREFIX}`, using all lower case characters and `.` instead of `_` as the delimiter,  e.g. the variable prefix `HONO_CREDENTIALS` corresponds to the command line option prefix `hono.credentials`).

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `${PREFIX}_AMQP_HOSTNAME`<br>`--${prefix}.amqpHostname` | no | - | The name to use as the <em>hostname</em> in the client's AMQP <em>Open</em> frame during connection establishment. This variable can be used to indicate the *virtual host* to connect to on the server. |
| `${PREFIX}_FLOW_LATENCY`<br>`--${prefix}.flowLatency` | no | `20` | The maximum amount of time (milliseconds) that the adapter should wait for *credits* after a link to the service has been established. |
| `${PREFIX}_HOST`<br>`--${prefix}.host` | no | `localhost` | The IP address or name of the host to connect to. NB: This needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the local container to the Docker network that the service is running on. |
| `${PREFIX}_HOSTNAME_VERIFICATION_REQUIRED`<br>`--${prefix}.hostnameVerificationRequired` | no | `true` | A flag indicating whether the value of the `${PREFIX}_HOST` variable must match the *distinguished name* or any of the *alternative names* asserted by the server's certificate when connecting using TLS. |
| `${PREFIX}_INITIAL_CREDITS`<br>`--${prefix}.initialCredits` | no | `200` | The number of *credits* that a consuming client will initially issue to the service (sender) after link creation. This value effectively limits the number of messages that can be *in flight* unsettled. |
| `${PREFIX}_NAME`<br>`--${prefix}.name` | no | - | The name to use as the <em>container-id</em> in the client's AMQP <em>Open</em> frame during connection establishment. |
| `${PREFIX}_PORT`<br>`--${prefix}.port` | no | `5671` | The port that the service is listening on. |
| `${PREFIX}_USERNAME`<br>`--${prefix}.username` | no | - | The username to use for authenticating to the service. |
| `${PREFIX}_PASSWORD`<br>`--${prefix}.password` | no | - | The password to use for authenticating to the service. |
| `${PREFIX}_REQUEST_TIMEOUT`<br>`--${prefix}.requestTimeout` | no | `200` | The maximum number of milliseconds to wait for a response before a service invocation is failed. Setting this value to a higher value increases the chance of successful service invocation in situations where network latency is high. |
| `${PREFIX}_TLS_ENABLED`<br>`--${prefix}.tlsEnabled` | no | `false` | If set to `true` the connection to the peer will be encrypted using TLS and the peer's identity will be verified using the JVM's configured standard trust store.<br>This variable only needs to be set to enable TLS explicitly if no specific trust store is configured using the `${PREFIX}_TRUST_STORE_PATH` variable. |
| `${PREFIX}_TRUST_STORE_PATH`<br>`--${prefix}.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the service. This property **must** be set if the service has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `${PREFIX}_TRUST_STORE_PASSWORD`<br>`--${prefix}.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

## Response Caching

The clients created by a Hono client factory support the caching of responses received in response to service invocations. Caching can greatly improve performance by preventing costly invocations of remote service operations. However, it usually only makes sense for resources that do not change too frequently. The Hono client follows the [approach to caching used in HTTP 1.1](https://tools.ietf.org/html/rfc2616#section-13.4). In particular, it supports [*cache directives*](https://tools.ietf.org/html/rfc2616#section-14.9) that a service includes in the response messages it sends back to the Hono client.

In order to enable caching, the `org.eclipse.hono.client.impl.HonoClientImpl` factory class needs to be configured with a cache manager using the *setCacheManager* method. Any specific client created by the factory will then cache responses to service invocations based on the following rules:

1. If the response contains a `no-cache` directive, the response is not cached at all.
2. Otherwise, if the response contains a `max-age` directive, the response is cached for the number of seconds specified by the directive.
3. Otherwise, if the response message does not contain any of the above directives and the response's status code is one of the codes defined in [RFC 2616, Section 13.4 Response Cacheability](https://tools.ietf.org/html/rfc2616#section-13.4), the response is put to the cache using the default timeout defined by the `${PREFIX}_RESPONSE_CACHE_DEFAULT_TIMEOUT` variable as the maximum age.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the Hono client's caching behavior.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `${PREFIX}_RESPONSE_CACHE_MIN_SIZE`<br>`--${prefix}.responseCacheMinSize` | no | `20` | The minimum number of responses that can be cached. |
| `${PREFIX}_RESPONSE_CACHE_MAX_SIZE`<br>`--${prefix}.responseCacheMaxSize` | no | `1000` | The maximum number of responses that can be cached. It is up to the particular cache implementation, how to deal with new cache entries once this limit has been reached. |
| `${PREFIX}_RESPONSE_CACHE_DEFAULT_TIMEOUT`<br>`--${prefix}.responseCacheDefaultTimeout` | no | `600` | The default number of seconds after which cached responses should be considered invalid. |
