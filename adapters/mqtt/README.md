# hono-adapter-mqtt project

This module contains Hono MQTT protocol adapter implemented using Vert.x and Quarkus.

## MQTT v5 Features

### Session Expiry Interval
The Hono MQTT adapter recognizes the `Session Expiry Interval` property sent by MQTT v5 clients in their CONNECT packets.

When a client provides a `Session Expiry Interval`, the server will include this interval in the `Session Expiry Interval` property of the CONNACK packet sent back to the client. This acknowledges the client's request. If the server is configured with a maximum session expiry interval that is lower than the client's requested interval, the server will use its configured maximum. (Currently, the implementation echoes back the client's value without enforcing a server-side maximum).

**Important:** While the MQTT adapter acknowledges the `Session Expiry Interval`, full server-side persistence of session state (including subscriptions and queued messages) for the duration of this interval when a client disconnects is currently a **TODO** and not yet fully implemented. The adapter presently operates mostly as if `Clean Start = true` regarding the persistence of session state across disconnects. Placeholder logic for scheduling session expiry upon client disconnect and cancelling it upon (hypothetical) session resumption has been added, along with corresponding logging.

### Message Expiry Interval

#### From Client to Server
If an MQTT v5 client includes the `Message Expiry Interval` property in a PUBLISH packet, the Hono MQTT adapter extracts this interval. This interval is then propagated as a message property (e.g., `x-hono-msg-expiry-interval`) in the message that is sent to downstream services (e.g., via AMQP or Kafka).

It is the responsibility of these downstream services or the ultimate consumer of the message to interpret and act upon this expiry information (e.g., by discarding the message if it has expired). Hono itself does not discard telemetry or event messages based on this interval before forwarding them.

#### To Client (Commands)
The MQTT adapter's internal mechanism for publishing messages to clients (e.g., for commands) has been updated to be capable of including the `Message Expiry Interval` property in the outgoing PUBLISH packets.

However, the ability for an application to *set* a specific message expiry interval on a command message sent *to* a device is currently a **TODO**. This functionality is dependent on future enhancements to Hono's Command API to support message expiry properties. Placeholder logic for checking a command's expiry before attempting delivery to an MQTT client is included in the adapter but is not fully active pending these API updates.

### Related Metrics
The following metrics have been added to provide insights into the usage of session and message expiry intervals:

*   `hono.mqtt.sessions.persistent.active` (Gauge): Approximate number of sessions currently being tracked for expiry. This is incremented when a client that indicated a Session Expiry Interval disconnects and decremented when a session is (theoretically) resumed or expires.
*   `hono.mqtt.sessions.expired.total` (Counter): Total number of persistent sessions that have (theoretically) expired based on their Session Expiry Interval.
*   `hono.mqtt.sessions.resumed.total` (Counter): Total number of sessions (theoretically) resumed. *Note: Full session resumption is not yet implemented.*
*   `hono.mqtt.messages.client.received.expiry.total` (Counter): Total count of PUBLISH messages received from clients that included a Message Expiry Interval property.
*   `hono.mqtt.commands.expired.total` (Counter): Total count of command messages that were (theoretically) discarded by the adapter because they expired before they could be delivered to the MQTT client. *Note: Full command expiry logic is not yet active.*
*   `hono.mqtt.commands.sent.expiry.total` (Counter): Total count of command messages sent to MQTT clients that included a (theoretical) Message Expiry Interval property. *Note: Setting expiry on commands by applications is not yet supported.*

**Note:** Some metrics related to full session persistence and command expiry reflect the current capabilities and will become more accurate as the underlying **TODO** items are fully implemented.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```
mvn quarkus:dev
```

## Packaging and running the application

The application can be packaged using `mvn package`.
It produces the `hono-adapter-mqtt-1.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.

The application is now runnable using `java -jar target/hono-adapter-mqtt-1.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: `mvn package -Pnative`.

You can then execute your native executable with: `./target/hono-adapter-mqtt-1.0-SNAPSHOT-runner`

## Creating Docker images

You can create a docker image by running: `mvn clean install -Pbuild-docker-image`

If you want to create a native image as well, activate the `native` profile as well:

```sh
mvn clean install -Pbuild-docker-image,native
```
