+++
title = "Hono Client Configuration"
weight = 340
+++

Hono comes with a set of Java packages that contain classes for interacting with Hono's service components via AMQP 1.0.
Hono's protocol adapters use these classes to query the device registry, receive commands and to forward events,
telemetry and command response messages to downstream business applications.

<!--more-->
All these classes require an instance of `org.eclipse.hono.client.amqp.connection.HonoConnection` which represents an
AMQP 1.0 connection to a peer. The properties of the connection can be configured by means of environment variables and/or
Java system properties. All these variables share a common *prefix*. This way, multiple sets of variables can be used
to configure multiple connections to different service endpoints without interfering with each other. For example,
the set of variables for configuring a protocol adapter's connection to the Device Registration service use the common
prefix `HONO_REGISTRATION` whereas the set of variables for configuring the connection to the Credentials service use
the `HONO_CREDENTIALS` prefix.

## Connection Properties

The following table provides an overview of the environment variables and corresponding system properties for
configuring an AMQP connection to a peer. Note that the variables map to the properties of class
`org.eclipse.hono.client.amqp.config.ClientConfigProperties` which can be used to configure a client programmatically.

The variable names contain `${PREFIX}` as a placeholder for the particular *common prefix* being used. The `${prefix}`
placeholder used in the Java system properties is the same as `${PREFIX}`, using all lower case characters and `.`
instead of `_` as the delimiter, e.g. the environment variable prefix `HONO_CREDENTIALS` corresponds to the Java system
property prefix `hono.credentials`.

| OS Environment Variable<br>Java System Property | Mandatory | Default Value | Description  |
| :---------------------------------------------- | :-------: | :------------ | :------------|
| `${PREFIX}_AMQPHOSTNAME`<br>`${prefix}.amqpHostname`   | no | - | The name to use as the *hostname* in the client's AMQP *open* frame during connection establishment. This variable can be used to indicate the *virtual host* to connect to on the server. |
| `${PREFIX}_CERTPATH`<br>`${prefix}.certPath`           | no | - | The absolute path to the PEM file containing the certificate that the client should use for authenticating to the server. This variable must be used in conjunction with `${PREFIX}_KEYPATH`.<br>Alternatively, the `${PREFIX}_KEYSTOREPATH` variable can be used to configure a key store containing both the key as well as the certificate. |
| `${PREFIX}_CONNECTTIMEOUT`<br>`${prefix}.connectTimeout` | no | `5000` | The maximum amount of time (milliseconds) that the client should wait for the AMQP connection to be opened. This includes the time for TCP/TLS connection establishment, SASL handshake and exchange of the AMQP <em>open</em> frame. This property can be used to tune the time period to wait according to the network latency involved with the connection between the client and the service. |
| `${PREFIX}_CREDENTIALSPATH`<br>`${prefix}.credentialsPath` | no | - | The absolute path to a properties file that contains a *username* and a *password* property to use for authenticating to the service.<br>This variable is an alternative to using `${PREFIX}_USERNAME` and `${PREFIX}_PASSWORD` which has the advantage of not needing to expose the secret (password) in the client process' environment. |
| `${PREFIX}_FLOWLATENCY`<br>`${prefix}.flowLatency` | no | `20` | The maximum amount of time (milliseconds) that the client should wait for *credits* after a link to the service has been established. |
| `${PREFIX}_HOST`<br>`${prefix}.host` | no | `localhost` | The IP address or name of the host to connect to. **NB** This needs to be set to an address that can be resolved within the network the client runs on. When running as a Docker container, use Docker's `--network` command line option to attach the local container to the Docker network that the service is running on. |
| `${PREFIX}_HOSTNAMEVERIFICATIONREQUIRED`<br>`${prefix}.hostnameVerificationRequired` | no | `true` | A flag indicating whether the value of the `${PREFIX}_HOST` variable must match the *distinguished name* or any of the *alternative names* asserted by the server's certificate when connecting using TLS. |
| `${PREFIX}_IDLETIMEOUT`<br>`${prefix}.idleTimeout` | no | `16000` | Sets the amount of time in milliseconds after which a connection will be closed when no frames have been received from the remote peer. This property is also used to configure a heartbeat mechanism, checking that the connection is still alive. The corresponding heartbeat interval will be set to *idleTimeout/2* ms. |
| `${PREFIX}_INITIALCREDITS`<br>`${prefix}.initialCredits` | no | `200` | The number of *credits* that a consuming client will initially issue to the service (sender) after link creation. This value effectively limits the number of messages that can be *in flight* unsettled. |
| `${PREFIX}_KEYPATH`<br>`${prefix}.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the client should use for authenticating to the server. Note that the private key is not protected by a password. You should therefore make sure that the key file can only be read by the user that the client process is running under. This variable must be used in conjunction with `${PREFIX}_CERTPATH`. Alternatively, the `${PREFIX}_KEYSTOREPATH` variable can be used to configure a key store containing both the key as well as the certificate. |
| `${PREFIX}_KEYSTOREPASSWORD`<br>`${prefix}.keyStorePassword` | no | - | The password required to read the contents of the key store. If the value starts with `file:` then the string after the prefix is interpreted as the path to a file to read the password from. |
| `${PREFIX}_KEYSTOREPATH`<br>`${prefix}.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the client should use for authenticating to the server. Either this variable or the `${PREFIX}_KEYPATH` and `${PREFIX}_CERTPATH` variables need to be set in order to enable *SASL External* based authentication to the server. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `${PREFIX}_LINKESTABLISMENTTIMEOUT`<br>`${prefix}.linkEstablishmentTimeout` | no | `1000` | The maximum amount of time (milliseconds) that the client should wait for the service's *attach* frame during link establishment. This property can be used to tune the time period to wait according to the network latency involved with the communication link between the client and the service. |
| `${PREFIX}_MAXFRAMESIZE`<br>`${prefix}.maxFrameSize` | no | `-1` | The maximum size (in bytes) of a single AMQP frame that the client should accept from the peer. When a peer sends a bigger frame, the connection will be closed. The default value of `-1` indicates that no limit is to be imposed. |
| `${PREFIX}_MAXMESSAGESIZE`<br>`${prefix}.maxMessageSize` | no | `-1` | The maximum size of messages (in bytes) that the client should accept from a peer. The default value of `-1` indicates that messages of any size should be accepted. |
| `${PREFIX}_MINMESSAGESIZE`<br>`${prefix}.minMessageSize` | no | `0` | The minimum *max-message-size* (in bytes) that the client requires a peer to accept. The default value of `0` indicates that no minimum size is required. Sender link establishment will fail, if the *max-message-size* conveyed by the peer in its *attach* frame is smaller than this property's value. |
| `${PREFIX}_MAXSESSIONFRAMES`<br>`${prefix}.maxSessionFrames` | no | `-1` | The maximum number of AMQP transfer frames for sessions created on this connection. This is the number of transfer frames that may simultaneously be in flight for all links in the session. The default value of `-1` indicates that no limit is to be imposed. |
| `${PREFIX}_NAME`<br>`${prefix}.name` | no | - | The name to use as the *container-id* in the client's AMQP *open* frame during connection establishment. |
| `${PREFIX}_PASSWORD`<br>`${prefix}.password` | no | - | The password to use for authenticating to the service. |
| `${PREFIX}_PORT`<br>`${prefix}.port` | no | `5671` | The port that the service is listening on. |
| `${PREFIX}_SENDMESSAGETIMEOUT`<br>`${prefix}.sendMessageTimeout` | no | `1000` | The maximum number of milliseconds to wait for a delivery update after an event or command message was sent before the send operation is failed. Setting this value to a higher value increases the chance of successful service invocation in situations where network latency is high. |
| `${PREFIX}_RECONNECTATTEMPTS`<br>`${prefix}.reconnectAttempts` | no | `-1` | The number of attempts (in addition to the original connection attempt) that the client should make in order to establish an AMQP connection with the peer before giving up. The default value of this property is -1 which means that the client will try forever. |
| `${PREFIX}_RECONNECTDELAYINCREMENT`<br>`${prefix}.reconnectDelayIncrement` | no | `100` | The factor (milliseconds) used in the exponential backoff algorithm for determining the delay before trying to re-establish an AMQP connection with the peer. The delay after an initial, failed connection attempt will be the value of the `${PREFIX}_RECONNECTMINDELAY` variable. Each subsequent connection attempt will use a random delay between the minimum delay and the value determined by exponentially increasing the delay by the `${PREFIX}_RECONNECTDELAYINCREMENT` factor. The overall limit of the delay time is defined by the `${PREFIX}_RECONNECTMAXDELAY` variable. |
| `${PREFIX}_RECONNECTMAXDELAY`<br>`${prefix}.reconnectMaxDelay` | no | `7000` | The maximum number of milliseconds to wait before trying to re-establish an AMQP connection with the peer. |
| `${PREFIX}_RECONNECTMINDELAY`<br>`${prefix}.reconnectMinDelay` | no | `0` | The minimum number of milliseconds to wait before trying to re-establish an AMQP connection with the peer. |
| `${PREFIX}_REQUESTTIMEOUT`<br>`${prefix}.requestTimeout` | no | `200` | The maximum number of milliseconds to wait for a response before a service invocation is failed. Setting this value to a higher value increases the chance of successful service invocation in situations where network latency is high. |
| `${PREFIX}_SECUREPROTOCOLS`<br>`${prefix}.secureProtocols` | no | `TLSv1.3,TLSv1.2` | A (comma separated) list of secure protocols (in order of preference) that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `${PREFIX}_SUPPORTEDCIPHERSUITES`<br>`${prefix}.supportedCipherSuites` | no | - | A (comma separated) list of names of cipher suites (in order of preference) that the client may negotiate in TLS sessions. Please refer to [JSSE Cipher Suite Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#jsse-cipher-suite-names) for a list of supported names. |
| `${PREFIX}_TLSENABLED`<br>`${prefix}.tlsEnabled` | no | `false` | If set to `true` the connection to the peer will be encrypted using TLS and the peer's identity will be verified using the JVM's configured standard trust store.<br>This variable only needs to be set to enable TLS explicitly if no specific trust store is configured using the `${PREFIX}_TRUSTSTOREPATH` variable. |
| `${PREFIX}_TRUSTSTOREPATH`<br>`${prefix}.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the client uses for authenticating to the service. This property **must** be set if the service has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `${PREFIX}_TRUSTSTOREPASSWORD`<br>`${prefix}.trustStorePassword` | no | - | The password required to read the contents of the trust store. If the value starts with `file:` then the string after the prefix is interpreted as the path to a file to read the password from. |
| `${PREFIX}_USELEGACYTRACECONTEXTFORMAT`<br>`${prefix}.useLegacyTraceContextFormat` | no | `true` | This flag determines where OpenTelemetry trace context information will be stored in an AMQP 1.0 message sent via the client. If set to `true`, the legacy format will be used, writing trace context information as `traceparent` and `tracestate` properties in an `x-opt-trace-context` map in the message-annotations of the message. <br/>When set to `false`, the tracing properties will be written to the application properties of the message instead.  The latter is a more generic approach, adhering to the [Trace Context: AMQP protocol](https://w3c.github.io/trace-context-amqp/) specification draft and being compatible with Eclipse Ditto, for example. It will be the default in Hono 3.0. |
| `${PREFIX}_USERNAME`<br>`${prefix}.username` | no | - | The username to use for authenticating to the service. This property (and the corresponding *password*) needs to be set in order to enable *SASL Plain* based authentication to the service.|

## Response Caching

The clients for interacting with the device registry services support the caching of responses received in response to
service invocations. Caching can greatly improve performance by preventing repeated (and costly) invocations of remote
service operations using the same request parameters. However, it usually only makes sense for resources that do not
change too frequently.

The clients follow the [approach to caching used in HTTP 1.1](https://tools.ietf.org/html/rfc2616#section-13.4). In
particular, they consider [*cache directives*](https://tools.ietf.org/html/rfc2616#section-14.9) that a service includes
in the response messages it sends back to the client.

Instances of `org.eclipse.hono.client.amqp.AbstractRequestResponseServiceClient` that have been created with a response cache
being passed into their constructor will cache responses to service invocations based on the following rules:

1. If the response contains a `no-cache` directive, the response is not cached at all.
2. Otherwise, if the response contains a `max-age` directive, the response is cached for the number of seconds determined
   as the minimum of the value contained in the directive and the value of the `${PREFIX}_RESPONSECACHEDEFAULTTIMEOUT` variable.
3. Otherwise, if the response message does not contain any of the above directives and the response's status code is one
   of the codes defined in [RFC 2616, Section 13.4 Response Cacheability](https://tools.ietf.org/html/rfc2616#section-13.4),
   the response is put to the cache using the default timeout defined by the `${PREFIX}_RESPONSECACHEDEFAULTTIMEOUT` variable
   as the maximum age.

The following table provides an overview of the environment variables and corresponding system properties for
configuring the clients' caching behavior. Note that the variables map to the properties of class
`org.eclipse.hono.client.amqp.config.RequestResponseClientConfigProperties` which can be used to configure a client programmatically.

| OS Environment Variable<br>Java System Property | Mandatory | Default Value | Description  |
| :---------------------------------------------- | :-------: | :------------ | :------------|
| `${PREFIX}_RESPONSECACHEMINSIZE`<br>`${prefix}.responseCacheMinSize` | no | `20` | The minimum number of responses that can be cached. |
| `${PREFIX}_RESPONSECACHEMAXSIZE`<br>`${prefix}.responseCacheMaxSize` | no | `1000` | The maximum number of responses that can be cached. It is up to the particular cache implementation, how to deal with new cache entries once this limit has been reached. |
| `${PREFIX}_RESPONSECACHEDEFAULTTIMEOUT`<br>`${prefix}.responseCacheDefaultTimeout` | no | `600` | The default number of seconds after which cached responses should be considered invalid. The value of this property serves as an upper boundary to the value conveyed in a `max-age` cache directive and is capped at `86400`, which corresponds to 24 hours. |

## Using TLS

The factory can be configured to use TLS for

* authenticating the server during connection establishment and
* (optionally) authenticating to the server using a client certificate (if the server supports this)

In order to authenticate the server by means of the server's certificate, the factory needs to be configured with a
*trust anchor* containing the *certificate authorities* that the client trusts. The trust anchor can be configured
explicitly by means of the `${PREFIX}_TRUSTSTOREPATH` and `${PREFIX}_TRUSTSTOREPASSWORD` variables. This is most useful
in cases where the server's certificate has not been signed by one of the public root CAs that are contained in the
JRE's standard trust store. However, if the server does use a certificate signed by such a public CA, then it is
sufficient to set the `${PREFIX}_TLSENABLED` variable to `true` in order for the client to support TLS when connecting
to the server.

The client can also be configured to authenticate to the server by means of an X.509 client certificate if the server
is configured to support this. The `${PREFIX}_CERTPATH` and `${PREFIX}_KEYPATH` variables can be used to set the paths to
PEM files containing the certificate and private key. Alternatively, the `${PREFIX}_KEYSTOREPATH` and
`${PREFIX}_KEYSTOREPASSWORD` variables can be used to set the path and password of a key store which contains both the
certificate as well as the private key.

The factory supports TLS 1.2 only by default for negotiating TLS sessions with servers. Additional protocols can be
enabled by setting the `${PREFIX}_SECUREPROTOCOLS` variable to a comma separated list of protocol names as defined in the
[vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl). However, great care should be taken when enabling
older protocols because most of them are vulnerable to attacks.

## Address rewriting

In some multi-tenant messaging environments external can have their addresses internally mapped to enforce consistent
name spaces. For example, the addresses can be prefixed by the virtual host the client uses to connect or some other
internal identifier. So address like `telemetry/DEFAULT_TENANT` would be internally represented as
`test-vhost/telemetry/DEFAULT_TENANT` for example.

To successfully address those external clients, infrastructure Hono components need to apply the same mapping rules.
The client factory can be configured to automatically rewrite addresses when opening links to the AMQP network.
The `${PREFIX}_ADDRESSREWRITERULE` variable contains rewrite rule for addresses based on the regular expressions.

| OS Environment Variable<br>Java System Property | Mandatory | Default Value | Description  |
| :---------------------------------------------- | :-------: | :------------ | :------------|
| `${PREFIX}_ADDRESSREWRITERULE`<br>`${prefix}.addressRewriteRule` | no | - | The address rewrite rule in the `"$PATTERN $REPLACEMENT"` format. |

The rule is defined in the `"$PATTERN $REPLACEMENT"` format, where the pattern and replacement use the standard
[Java regular expression](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html)
syntax. The pattern should match the address or otherwise the original address will be used.

For example, setting `HONO_ADDRESSREWRITERULE` to `([a-z_]+)/([\\w-]+) test-vhost/$1/$2` would result in adding the
`test-vhost/` prefix to all addresses used by the client.
