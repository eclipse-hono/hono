+++
title = "Hono Client Configuration"
weight = 340
+++

A Hono client is configured by the following keys that only differ by their API specific prefixes.
<!--more-->

The prefix is denoted as `${PREFIX}` and needs to be substituted by the prefix of the specific client (e.g. `HONO_MESSAGING`).

The `${prefix}` placeholder is the same as `${PREFIX}`, but in lower case and using `.`
instead of `_` (e.g. `HONO_MESSAGING` becomes `hono.messaging`).

## Client Configuration

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `${PREFIX}_FLOW_LATENCY`<br>`--${prefix}.flowLatency` | no | `20` | The maximum amount of time (milliseconds) that the adapter should wait for *credits* after a link to the component has been established. |
| `${PREFIX}_HOST`<br>`--${prefix}.host` | yes | `localhost` | The IP address or name of the host to connect to. NB: This needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the adapter container to the Docker network that the *Hono Server* container is running on. |
| `${PREFIX}_PORT`<br>`--${prefix}.port` | yes | `5671` | The port that the component is listening on. |
| `${PREFIX}_USERNAME`<br>`--${prefix}.username` | yes | - | The username to use for authenticating to the component. |
| `${PREFIX}_PASSWORD`<br>`--${prefix}.password` | yes | - | The password to use for authenticating to the component. |
| `${PREFIX}_TLS_ENABLED`<br>`--${prefix}.tlsEnabled` | no | `false` | If set to `true` the connection to the peer will be encrypted using TLS and the peer's identity will be verified using the JVM's configured standard trust store.<br>This variable only needs to be set to enable TLS explicitly if no specific trust store is configured using the `${PREFIX}_TRUST_STORE_PATH` variable. |
| `${PREFIX}_TRUST_STORE_PATH`<br>`--${prefix}.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the component. This property **must** be set if the component has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `${PREFIX}_TRUST_STORE_PASSWORD`<br>`--${prefix}.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

For the caching of responses there are additional values to configure:

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `${PREFIX}_RESPONSE_CACHE_MIN_SIZE`<br>`--${prefix}.responseCacheMinSize` | no | `20` | The minimum number of responses from the component to cache in the client.<br>
| `${PREFIX}_RESPONSE_CACHE_MAX_SIZE`<br>`--${prefix}.responseCacheMaxSize` | no | `1000` | The maximum number of responses from the component to cache in the client. Setting this variable to 0 disables caching.<br>
