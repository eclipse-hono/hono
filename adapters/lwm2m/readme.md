This module contains a protocol adapter for the Open Mobile Alliance's Lightweight M2M device management protocol.
This module is still experimental and currently only supports forwarding of notifications for observed resources on LWM2M clients to Hono's telemetry API.

It is built on top of [Eclipse leshan](https://www.eclipse.org/leshan) and uses [Eclipse Californium](https://www.eclipse.org/californium) for communicating with clients via CoAP.

# Running the Adapter

The adapter is implemented as a Spring Boot application and as such can be run from the project folder
by simply invoking

    $ mvn spring-boot:run

Being a Spring Boot application, the adapter's jar file is an *executable* jar that contains all necessary dependencies and can be run from the command line as well:

    $ java -jar target/hono-adapter-lwm2m-0.5-SNAPSHOT.jar

Any of these commands will start up the leshan server with a default *standalone* configuration. In the *standalone* configuration the adapter does **not** try to connect to a Hono server. The standalone adapter by default binds to all network interfaces and listens on ports 5683 and 5684 for UDP and DTLS traffic respectively. The adapter also exposes leshan's *Demo Web UI* via HTTP on port 8090.

Simply open a web browser and point it to `http://localhost:8090` in order to open the Web UI.

# Configuring the Adapter

The adapter's configuration can be set by means of command line arguments and/or environment variables. Please consult the [Spring Boot documentation](http://docs.spring.io/spring-boot/docs/1.4.1.RELEASE/reference/htmlsingle/#boot-features-external-config) for a thorough discussion of all supported mechanisms.

The following table provides an overview of the configuration options that are supported:

| Environment Variable | Command Line Parameter | Default Value | Description |
| :------------------- | :--------------------- | :------------ | :---------- |
| `LESHAN_SERVER_COAPBINDADDRESS` | `--leshan.server.coapBindAddress` | `0.0.0.0` | The network interface the adapter should bind its non-secure (plain UDP) endpoint to. |
| `LESHAN_SERVER_COAPPORT` | `--leshan.server.coapPort` | `5683` | The port that the adapter should listen on for UDP based LWM2M clients. |
| `LESHAN_SERVER_COAPSBINDADDRESS` | `--leshan.server.coapsBindAddress` | `0.0.0.0` | The network interface the adapter should bind its secure (DTLS) endpoint to. |
| `LESHAN_SERVER_COAPSPORT` | `--leshan.server.coapsPort` | `5684` | The port that the adapter should listen on for DTLS based LWM2M clients. |
| `LESHAN_SERVER_HTTPBINDADDRESS` | `--leshan.server.httpBindAddress` | `0.0.0.0` | The network interface the adapter should bind its web UI to. |
| `LESHAN_SERVER_HTTPPORT` | `--leshan.server.httpPort` | `8090` | The port that the adapter should listen on for DTLS based LWM2M clients. |
| `LESHAN_SERVER_OBJECTDIR` | `--leshan.server.objectDir` | - | The absolute path to a directory containing LWM2M object definition xml files that should be supported in addition to the LWM2M standard objects defined by the LWM2M spec. |
| `HONO_CLIENT_HOST` | `--hono.client.host` | `localhost` | The IP address or host name of the Hono server the adapter should connect to. |
| `HONO_CLIENT_PORT` | `--hono.client.port` | `5672` | The port of the Hono server the adapter should connect to. |
| `HONO_CLIENT_USERNAME` | `--hono.client.username` | - | The user name to use for authenticating with the Hono server. |
| `HONO_CLIENT_PASSWORD` | `--hono.client.password` | - | The password to use for authenticating with the Hono server. |
| `HONO_LWM2M_ADAPTER_TENANT` | `--hono.lwm2m.adapter.tenant` | - | The ID of the tenant that the devices managed by the adapter belong to. |

All of these properties can also be configured using a YAML or properties file. In fact, the default configuration is defined in the `src/main/resources/application.yml` file. 

The configuration file that comes with the adapter defines two Spring Boot *profiles*. The first is the *standalone* profile which we have already discussed above. The second one is the *hono* profile which shares the leshan configuration with the *standalone* profile but in addition configures the adapter to connect to a Hono server running on `localhost:5672` using username `hono-client` and password `secret`.

In order to start the adapter with the *hono* profile, you can run the following command from the project folder:

    $ java -jar target/hono-adapter-lwm2m-0.5-SNAPSHOT.jar --spring.profiles.active=hono

Please refer to the [Spring Boot documentation](http://docs.spring.io/spring-boot/docs/1.4.1.RELEASE/reference/htmlsingle/#boot-features-external-config-application-property-files) for details regarding how to create and use such a property file.

# Customizing the Adapter

tbd
