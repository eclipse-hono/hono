+++
title = "Implementing Third Party Adapters"
weight = 390
+++

Eclipse Hono&trade; has the concept of protocol adapters (check the [Component Overview]({{< relref "architecture/component-view/index.md" >}}) for details),
which provide protocol endpoints for devices.
Hono already comes with a set of protocol adapters for some of the well known IoT protocols.
However, in some cases devices do not communicate via standardized protocols but use a proprietary protocol.
To provide the service user of a Hono service the possibility to also connect devices speaking a proprietary protocol
Hono supports the concept of *Third Party Adapters*.

A *Third Party Adapter* is a separately deployed micro service, which understands proprietary protocol messages and translates
them to AMQP messages processable by Hono's AMQP protocol adapter.
In that sense a *Third Party Adapter* behaves like a gateway for the devices speaking the proprietary protocol and forwards their
messages to the AMQP Messaging Network. 

The diagram below provides an overview of the *Third Party Adapter* setup.

{{< figure src="../third_party_adapter.png" >}}  

Devices send telemetry/event messages via their own proprietary protocols to the *Third Party Adapters* gateway,
which send the messages via AMQP to the Hono AMQP adapter.

A prerequisite for using *Third Party Adapters* is that devices are registered in Hono's Device Registry
and are configured in such a way that the *Third Party Adapters* can act on their behalf i.e.
with a device registration "via" property containing the gateway id of the *Third Party Adapters*.

## Scaling *Third Party Adapters*
To support scaling *Third Party Adapters* dynamically, ie. using multiple instances of them, each 
*Third Party Adapters* instance would need to be registered as a gateway in Hono and also be configured in 
the "via" entry of the devices. 
To ease the registration of *Third Party Adapters* gateway groups grouping the gateway ids of the deployed *Third Party Adapters*
 can be used.
The devices then only need to reference the gateway group in their "via" property.

## Example: How to set up a *Third Party Adapter* to connect to the AMQP adapter

### Prerequisites

From your Hono instance get:
    - AMQP hono adapter ip, referred to as `AMQP_ADAPTER_IP`
    - AMQP hono adapter port, referred to as `AMQP_ADAPTER_PORT` (default: 5672)
    - A device `d` username, a combination of hono deviceId and tenantId, concatenated with `'@'`, , referred to as `USERNAME`
    E.g.: `7c7c9777-2acd-450e-aa61-ab73d37ad0ef@6d12841d-0458-4271-b060-44a46f3417a9`
    - A password for device `d`, refered to as `PASSWORD`

How to get these values, can be found in [Getting started guide](https://www.eclipse.org/hono/getting-started/).

Alternatively, these values can be fetched and created using the following script:

```bash
# prior: setup hono in kubernetes namespace "hono"
export REGISTRY_IP=$(kubectl -n hono get service  hono-service-device-registry-ext --output='jsonpath={.status.loadBalancer.ingress[0].ip}')
echo "MQTT_ADAPTER_IP=${MQTT_ADAPTER_IP}"
export AMQP_NETWORK_IP=$(kubectl -n hono get service hono-dispatch-router-ext --output='jsonpath={.status.loadBalancer.ingress[0].ip}')
echo "AMQP_NETWORK_IP=${AMQP_NETWORK_IP}"
export AMQP_ADAPTER_PORT=$(kubectl -n hono get service hono-adapter-amqp-vertx --output='jsonpath={.status.loadBalancer.ingress[0].port}')
echo "AMQP_ADAPTER_IP=${AMQP_ADAPTER_IP}"

# Get example tenant or
export MY_TENANT="DEFAULT_TENANT"
# register new tenant
# export MY_TENANT=$(curl -X POST http://$REGISTRY_IP:28080/v1/tenants 2>/dev/null | jq -r .id)

echo "MY_TENANT=\"${MY_TENANT}\""

# register new device
export MY_DEVICE=$(curl -X POST http://$REGISTRY_IP:28080/v1/devices/$MY_TENANT 2>/dev/null | jq -r .id)
echo "MY_DEVICE=\"${MY_DEVICE}\""

# set credential secret for device
export MY_PWD="dummyDevicePassword"
echo "MY_PWD=\"${MY_PWD}\""
curl -i -X PUT -H "content-type: application/json" --data-binary '[{
  "type": "hashed-password",
  "auth-id": "'$MY_DEVICE'",
  "secrets": [{ "pwd-plain": "'$MY_PWD'" }]
}]' http://$REGISTRY_IP:28080/v1/credentials/$MY_TENANT/$MY_DEVICE

```

### Initialize a java spring boot project

Since the Hono classes used in this example use Java Spring boot, spring boot is used as a dependency.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
      <version>2.1.9.RELEASE</version>
</dependency>

```

Import Hono Cli dependency to inherit [`org.eclipse.hono.cli.adapter.AmqpCliClient`](https://github.com/eclipse/hono/blob/master/cli/src/main/java/org/eclipse/hono/cli/adapter/AmqpCliClient.java) that provides a connection to interact with the Hono AMQP adapter.

```xml
<dependency>
    <groupId>org.eclipse.hono</groupId>
    <artifactId>hono-cli</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

Set [Vertx](https://vertx.io/) as `@Bean`, since it is used by `org.eclipse.hono.cli.adapter.AmqpCliClient`

```java
@Bean
public Vertx vertx() {
    final VertxOptions options = new VertxOptions()
            .setWarningExceptionTime(1500000000);
    return Vertx.vertx(options);
}
```

### Setup connection: ClientConfigProperties

To connect to the Hono AMQP adapter it is needed to set Connection properties and credentials. These can be set as Spring properties or programmatic using [`org.eclipse.hono.config.ClientConfigProperties`](https://github.com/eclipse/hono/tree/e511184c2dbd851c5c29ae3c2a9578b3be6748e4/core/src/main/java/org/eclipse/hono/config/ClientConfigProperties.java).

#### Set by: Spring properties

See [application.yml](https://github.com/bosch-io/hono/tree/3c654571abaa46e46642f2f4539ee1c6dbbdfa47/cli/src/main/resources/application.yml)
The properties are set in [`AmqpCliClient:49-59`](https://github.com/eclipse/hono/blob/39668a4bd2a793b833cacb2af38bfdc40e656668/cli/src/main/java/org/eclipse/hono/cli/adapter/AmqpCliClient.java#L49-L59)

#### Set by: ClientConfigProperties Api

Import `ClientConfigProperties` and inherit `AmqpCliClient`.
Before communicating with the AMQP adapter, set connection properties:

```java
ClientConfigProperties props = new ClientConfigProperties();
props.setHost(AMQP_ADAPTER_IP);
props.setPort(AMQP_ADAPTER_PORT);
props.setUsername(USERNAME);
props.setPassword(PASSWORD);
setClientConfig(props);
```

### Open AMQP adapter connection and create `ProtonSender`

In the class, containing the `ClientConfigProperties` and inheriting from `AmqpCliClient`, `Telemetry` and `Event` messages can be sent by creating `ProtonConnection` and `ProtonSender`.

- Create a `ProtonConnection` by calling [`AmqpCliClient.connectToAdapter()`](https://github.com/bosch-io/hono/blob/39668a4bd2a793b833cacb2af38bfdc40e656668/cli/src/main/java/org/eclipse/hono/cli/adapter/AmqpCliClient.java#L88-L133)
- In the success handler:
  - Assign the `ProtonConnection` to the `AmqpCliClient` property `ProtonConnection adapterConnection`
  - Create a returning `ProtonSender` by using [`AmqpCliClient.createSender()`](https://github.com/bosch-io/hono/blob/39668a4bd2a793b833cacb2af38bfdc40e656668/cli/src/main/java/org/eclipse/hono/cli/adapter/AmqpCliClient.java#L70-L80)

```java
final Future<ProtonSender> senderFuture = connectToAdapter()
    .compose(con -> {
        this.adapterConnection = con;
        return createSender();
    });
```

### Send Telemetry or Event

Depending on the desired message address (telemetry/event):

- Use `telemetry` or `t`
- Use `event` or `e`

```java
final String messageAddress = "event"; // E.g.: event

```

- Create `CompletableFuture` to notify on message delivery.
- In success handler create message using `Message io.vertx.proton.ProtonHelper.message(String address, String body)` with `messageAddress` and a payload.
- Complete `CompletableFuture` in `ProtonDelivery` handler
- On `ProtonDelivery` fail, call `messageTracker.completeExceptionally(Throwable ex)` on `CompletableFuture`
- Listen on `CompletableFuture` completion.

```java
final CompletableFuture<ProtonDelivery> messageSent = new CompletableFuture<>();

senderFuture.map(sender -> {
        final Message message = ProtonHelper.message(messageAddress, "{\"alert\": true}");
        sender.send(message, delivery -> {
            adapterConnection.close();
            messageTracker.complete(delivery);
        });
        return sender;
    })
    .otherwise(t -> {
        messageTracker.completeExceptionally(t);
        return null;
    });
```

### Send Command and Control

In the class, containing the `ClientConfigProperties` and inheriting from `AmqpCliClient`, commands can be received and answered, using `ProtonConnection`, `ProtonSender` and `io.vertx.proton.ProtonReceiver`.

> Hint: Create `ProtonConnection` and `Future<ProtonSender> senderFuture` using step [Open AMQP adapter connection and create `ProtonSender`](#open-amqp-adapter-connection-and-create-protonsender)

- In success handler of `senderFuture`:
  - Assign `ProtonSender s` to a class member
  - Create a Promise `receiverPromise` for the `ProtonReceiver receiver`
    - Set QoS
    - Pass `receiver.handler(ProtonMessageHandler handler)` a `ProtonMessageHandler messageHandler` (defined in later step)
    - Pass `receiver.openHandler(Handler<AsyncResult<ProtonReceiver>> remoteOpenHandler)` the `Promise receiverPromise`
    - Call `receiver.open()`
  - Use `receiverPromise` to get a signal when receiver ready

```java
senderFuture.map(s -> {
        this.sender = s;
        final Promise<ProtonReceiver> receiverPromise = Promise.promise();
        final ProtonReceiver receiver = adapterConnection.createReceiver(CommandConstants.COMMAND_ENDPOINT);
        receiver.setQoS(ProtonQoS.AT_LEAST_ONCE);
        receiver.handler(messageHandler);
        receiver.openHandler(receiverPromise);
        receiver.open();
        return receiverPromise.future().map(rcvr -> {
            log.info("Commands receiver ready");
            return rcvr;
        });
    });
```

#### Command and Control: Receiver and response handling

The `ProtonReceiver` gets a `ProtonMessageHandler messageHandler` passed with `handler()`. The `ProtonMessageHandler` is a callback function, that handles the `io.vertx.proton.ProtonDelivery` and the received messages (`org.apache.qpid.proton.message.Message`).

- Create `ProtonMessageHandler messageHandler` lambda function that handles the receiving commands
  - Information about the received command can be found in `Message m`: [see qpid documentation](https://qpid.apache.org/releases/qpid-proton-j-0.21.0/api/org/apache/qpid/proton/message/Message.html)
  - E.g.: Encode `m.getBody()` into a String `commandPayload`
  - If command is [One-Way-command](https://www.eclipse.org/hono/docs/api/command-and-control/#send-a-one-way-command) no response is needed
  - If command is [request/response-command](https://www.eclipse.org/hono/docs/api/command-and-control/#send-a-request-response-command), a reply address can be found `m.getReplyTo()`
    - Use `Message io.vertx.proton.ProtonHelper.message(String address, String body)` to create a response message `commandResponse` with `messageAddress` and a payload.
    - Pass`commandResponse.setCorrelationId(Object correlationId)` to set the response message id to the same one of request `m.getCorrelationId()`
    - Use `void org.eclipse.hono.util.MessageHelper.addProperty(Message msg, String key, Object value)` to add `HttpURLConnection.HTTP_OK` to the response property `MessageHelper.APP_PROPERTY_STATUS`
    - Set a content type `commandResponse.setContentType("application/json");`
    - Call `this.sender.send(Message message, Handler<ProtonDelivery> onUpdated)` of the prior assigned class member `ProtonSender` `this.sender` to send the response `commandResponse` and the 2nd parameter handles to handle the delivery result.

```java
final ProtonMessageHandler messageHandler = (d, m) -> {
    String commandPayload = null;
    if (m.getBody() instanceof Data) {
        final byte[] body = (((Data) m.getBody()).getValue()).getArray();
        commandPayload = new String(body);
    }
    System.out.println(String.format("Got command: %s", commandPayload));
    final boolean isOneWay = m.getReplyTo() == null;
    if (!isOneWay) {

        final String responsePayload = String.format("{\"data\": \"command %s received\"}", commandPayload);
        final Message commandResponse = ProtonHelper.message(m.getReplyTo(), responsePayload);
        commandResponse.setCorrelationId(m.getCorrelationId());
        MessageHelper.addProperty(commandResponse, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        commandResponse.setContentType("application/json");
        this.sender.send(commandResponse, delivery -> {
            if (delivery.remotelySettled()) {
                System.out.println(String.format("sent response to command [name: %s, outcome: %s]%n", m.getSubject(), delivery.getRemoteState().getType()));
            } else {
                System.out.println("application did not settle command response message");
            }
        });
    }
};
```
