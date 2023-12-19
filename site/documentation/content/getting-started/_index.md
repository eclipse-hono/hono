+++
title = "Getting started with Eclipse Hono"
linkTitle = "Getting started"
description = "Take a quick tour and learn how Hono can help you connect devices via HTTP and/or MQTT and how downstream applications can consume the data published by devices."
weight = 170
pre = '<i class="fas fa-plane-departure"></i> '
+++

This guide will walk you through an interactive example usage scenario of Eclipse Hono. You will learn how devices can
use Hono's protocol adapters to publish telemetry data and events using both HTTP and/or MQTT. You will also see how
downstream applications can consume this data using Hono's north bound API regardless of the communication protocols
used by the devices.

## Prerequisites for the Getting started Guide

This guide requires several tools to be installed on your computer.
During the course of this guide, the devices publishing data will be represented by means of running some command line
tools for posting HTTP requests and for publishing MQTT packets.

#### Curl HTTP Command Line Client

The `curl` command is used in this guide for registering devices with Hono and for simulating a device publishing data
using HTTP. On most *nix like systems `curl` will probably already be installed. Otherwise, please refer to the
[Curl project page](https://curl.haxx.se/) for installation instructions.

#### Mosquitto MQTT Command Line Client

The `mosquitto_pub` command is used in this guide for simulating a device publishing data using the MQTT protocol.
Please refer to the [Mosquitto project page](https://mosquitto.org/) for installation instructions, if you do not
have it installed already.

{{% notice info %}}
The installation of the Mosquitto command line client is optional. If you do not install it then you will not be
able to simulate an MQTT based device but otherwise will be able to get the same results described in this guide.
{{% /notice %}}

#### Hono Command Line Client

The Hono command line client is used in this guide for simulating an application that consumes telemetry data and events
published by devices. The client is available from [Hono's download page]({{% homelink "downloads" %}}).

{{% notice tip %}}
The command line client is available in two variants:

1. A Java Archive that requires a Java Runtime Environment to be installed locally and
1. a x86_64 Linux executable that can be run from a shell directly.

The former variant works on all platforms where Java is available and will be used during the remainder of this guide.
The latter variant should work on modern Linux distributions and can be used by replacing `java -jar hono-cli-*-exec.jar`
with just the name of the executable file.

Note that when using the native executable, the root CA certificates included in the executable will be used. To use the
(possibly more up to date) local CA certificates instead, add the `--ca-file` CLI parameter with the path of the
certificates (e.g. `--ca-file /etc/ssl/certs/ca-certificates.crt`).
{{% /notice %}}

#### Hono Instance

The most important prerequisite is, of course, a Hono instance that you can work with.

The most straightforward option to use for this guide is the [Hono Sandbox](https://www.eclipse.org/hono/sandbox) which
is running on infrastructure provided by the Eclipse Foundation and which is publicly accessible from the internet.

Using the Sandbox, there is no need to set up your own Hono instance locally. However, it requires several non-standard
ports being accessible from your computer which may not be the case, e.g. if you are behind a firewall restricting
internet access to a few standard ports only.

You can verify if you can access one of the non-standard ports of the Sandbox by running the following command and
comparing the output:

~~~sh
curl -sIX GET https://hono.eclipseprojects.io:28443/v1/tenants/DEFAULT_TENANT
~~~

If you get output like this

~~~
HTTP/1.1 200 OK
etag: 89d40d26-5956-4cc6-b978-b15fda5d1823
content-type: application/json; charset=utf-8
content-length: 260
~~~

you should be able to use the Sandbox. However, if the `curl` command failed to execute, you will need to set up a local
Hono instance. You can choose between using Apache Kafka&trade; as the messaging infrastructure or AMQP 1.0
based infrastructure using Apache Qpid&trade; Dispatch Router and Apache ActiveMQ&trade; Artemis. Please select the
tab below that matches your chosen type of messaging infrastructure and follow the instructions given there.

{{< tabs groupId="hono-instance" >}}
{{% tab name="Sandbox" %}}

Hono consists of a set of microservices which are deployed as Docker containers. The diagram below provides
an overview of the containers that are part of the Hono Sandbox deployment.

{{< figure src="Hono_instance_kafka.svg" title="Components of the Hono Sandbox deployment"
alt="The Docker containers representing the services of the example Hono Sandbox deployment using Kafka based messaging infrastructure" >}}

* Hono Instance
  * An *HTTP Adapter* instance that exposes Hono's Telemetry and Event APIs as URI resources.
  * An *MQTT Adapter* instance that exposes Hono's Telemetry and Event APIs as a generic MQTT topic hierarchy.
  * An *AMQP Adapter* instance that exposes Hono's Telemetry and Event APIs as a set of AMQP 1.0 addresses.
  * A *Command Router* instance that receives Command & Control messages and forwards them to protocol adapters.
  * A *Device Registry* instance that manages registration information and issues device registration assertions to
    protocol adapters.
  * An *Auth Server* instance that authenticates Hono components and issues tokens asserting identity and authorities.
* Kafka Cluster
  * An *Apache Kafka* broker instance that downstream applications connect to in order to consume telemetry data and
    events from devices and to send Command & Control messages to devices.
  * An *Apache Zookeeper* instance that is required by the Kafka cluster.
* Monitoring Infrastructure
  * A *Prometheus* instance for storing metrics data from services and protocol adapters.
  * A *Grafana* instance providing a dashboard visualizing the collected metrics data.

In the example scenario used in the remainder of this guide, the devices will connect to the HTTP and MQTT adapters in
order to publish telemetry data and events. The devices will be authenticated using information stored in the Device
Registry. The data is then forwarded downstream to the example application via the Kafka broker.

Run the following commands to create the `hono.env` file which will be used during the remainder of this guide to set
and refresh some environment variables:

~~~sh
cat <<EOS > hono.env
export REGISTRY_IP=hono.eclipseprojects.io
export HTTP_ADAPTER_IP=hono.eclipseprojects.io
export MQTT_ADAPTER_IP=hono.eclipseprojects.io
export KAFKA_IP=hono.eclipseprojects.io
export APP_OPTIONS="--sandbox"
export CURL_OPTIONS=
export MOSQUITTO_OPTIONS='--cafile /etc/ssl/certs/ca-certificates.crt'
EOS
~~~

Verify that the `hono.env` file has been created in your current working directory:

~~~sh
cat hono.env
~~~
~~~
export REGISTRY_IP=hono.eclipseprojects.io
export HTTP_ADAPTER_IP=hono.eclipseprojects.io
export MQTT_ADAPTER_IP=hono.eclipseprojects.io
export KAFKA_IP=hono.eclipseprojects.io
export APP_OPTIONS="--sandbox"
export CURL_OPTIONS=
export MOSQUITTO_OPTIONS='--cafile /etc/ssl/certs/ca-certificates.crt'
~~~

{{% /tab %}}

{{% tab name="Local Instance using Kafka" %}}

In order to set up an instance of Hono running on your local computer using Apache Kafka as the messaging infrastructure,
follow these steps:

1. Please refer to the [Kubernetes installation instructions]({{< relref "/deployment/create-kubernetes-cluster" >}})
   for setting up a local single-node Minikube cluster.
1. Make sure to run `minikube tunnel` in order to support creating Kubernetes services of type *LoadBalancer*.
1. Follow the instructions given in the [README](https://github.com/eclipse/packages/blob/master/charts/hono/README.md)
   of Hono's Helm chart in order to install Hono with a single-node Kafka instance to your local Minikube cluster.

Hono consists of a set of microservices which are deployed as Docker containers. The diagram below provides an overview
of the containers that are part of the example deployment of Hono on the local Minikube cluster.

{{< figure src="Hono_instance_kafka.svg" title="Components of the example Hono deployment using Kafka"
alt="The Docker containers representing the services of the example Hono deployment using Kafka based messaging infrastructure" >}}

* Hono Instance
  * An *HTTP Adapter* instance that exposes Hono's Telemetry and Event APIs as URI resources.
  * An *MQTT Adapter* instance that exposes Hono's Telemetry and Event APIs as a generic MQTT topic hierarchy.
  * An *AMQP Adapter* instance that exposes Hono's Telemetry and Event APIs as a set of AMQP 1.0 addresses.
  * A *Command Router* instance that receives Command & Control messages and forwards them to protocol adapters.
  * A *Device Registry* instance that manages registration information and issues device registration assertions to
    protocol adapters.
  * An *Auth Server* instance that authenticates Hono components and issues tokens asserting identity and authorities.
* Kafka Cluster
  * An *Apache Kafka* broker instance that downstream applications connect to in order to consume telemetry data and
    events from devices and to send Command & Control messages to devices.
  * An *Apache Zookeeper* instance that is required by the Kafka cluster.
* Monitoring Infrastructure
  * A *Prometheus* instance for storing metrics data from services and protocol adapters.
  * A *Grafana* instance providing a dashboard visualizing the collected metrics data.

In the example scenario used in the remainder of this guide, the devices will connect to the HTTP and MQTT adapters in
order to publish telemetry data and events. The devices will be authenticated using information stored in the Device
Registry. The data is then forwarded downstream to the example application via the Kafka broker.

Once Hono has been deployed to your local cluster, run the following commands to create the `hono.env` file which will
be used during the remainder of this guide to set and refresh some environment variables:

~~~sh
echo "export REGISTRY_IP=$(kubectl get service eclipse-hono-service-device-registry-ext --output="jsonpath={.status.loadBalancer.ingress[0]['hostname','ip']}" -n hono)" > hono.env
echo "export HTTP_ADAPTER_IP=$(kubectl get service eclipse-hono-adapter-http --output="jsonpath={.status.loadBalancer.ingress[0]['hostname','ip']}" -n hono)" >> hono.env
echo "export MQTT_ADAPTER_IP=$(kubectl get service eclipse-hono-adapter-mqtt --output="jsonpath={.status.loadBalancer.ingress[0]['hostname','ip']}" -n hono)" >> hono.env
KAFKA_IP=$(kubectl get service eclipse-hono-kafka-0-external --output="jsonpath={.status.loadBalancer.ingress[0]['hostname','ip']}" -n hono)
TRUSTSTORE_PATH=/tmp/truststore.pem
kubectl get configmaps eclipse-hono-example-trust-store --template="{{index .data \"ca.crt\"}}" -n hono > ${TRUSTSTORE_PATH}
echo "export APP_OPTIONS='-H ${KAFKA_IP} -P 9094 -u hono -p hono-secret --ca-file ${TRUSTSTORE_PATH} --disable-hostname-verification'" >> hono.env
echo "export CURL_OPTIONS='--insecure'" >> hono.env
echo "export MOSQUITTO_OPTIONS='--cafile ${TRUSTSTORE_PATH} --insecure'" >> hono.env
~~~

Verify that the `hono.env` file has been created in your current working directory:

~~~sh
cat hono.env
~~~
~~~
export REGISTRY_IP=(host name/IP address)
export HTTP_ADAPTER_IP=(host name/IP address)
export MQTT_ADAPTER_IP=(host name/IP address)
export APP_OPTIONS='-H (host name/IP address) -P 9094 -u hono -p hono-secret --ca-file /tmp/truststore.pem --disable-hostname-verification'
export CURL_OPTIONS='--insecure'
export MOSQUITTO_OPTIONS='--cafile /tmp/truststore.pem --insecure'
~~~

{{% /tab %}}

{{% tab name="Local Instance using AMQP 1.0" %}}
In order to set up an instance of Hono running on your local computer using Apache Qpid as the messaging infrastructure,
follow these steps:

1. Please refer to the [Kubernetes installation instructions]({{< relref "/deployment/create-kubernetes-cluster" >}})
   for setting up a local single-node Minikube cluster.
1. Make sure to run `minikube tunnel` in order to support creating Kubernetes services of type *LoadBalancer*.
1. Follow the instructions given in the
   [README](https://github.com/eclipse/packages/blob/master/charts/hono/README.md#using-amqp-10-based-messaging-infrastructure)
   of Hono's Helm chart in order to install Hono with a single-node Qpid Dispatch Router instance to your local Minikube
   cluster.

Hono consists of a set of microservices which are deployed as Docker containers. The diagram below provides an overview
of the containers that are part of the example deployment of Hono on the local Minikube cluster.

{{< figure src="Hono_instance_amqp.svg" title="Components of the example Hono deployment using AMQP 1.0"
alt="The Docker containers representing the services of the example Hono deployment using AMQP 1.0 based messaging infrastructure" >}}

* Hono Instance
  * An *HTTP Adapter* instance that exposes Hono's Telemetry and Event APIs as URI resources.
  * An *MQTT Adapter* instance that exposes Hono's Telemetry and Event APIs as a generic MQTT topic hierarchy.
  * An *AMQP Adapter* instance that exposes Hono's Telemetry and Event APIs as a set of AMQP 1.0 addresses.
  * A *Command Router* instance that receives Command & Control messages and forwards them to protocol adapters.
  * A *Device Registry* instance that manages registration information and issues device registration assertions to
    protocol adapters.
  * An *Auth Server* instance that authenticates Hono components and issues tokens asserting identity and authorities.
* AMQP Network
  * An *Apache Qpid Dispatch Router* instance that downstream applications connect to in order to consume telemetry
    data and events from devices and to send Command & Control messages to devices.
  * An *Apache ActiveMQ Artemis* instance serving as the persistence store for events.
* Monitoring Infrastructure
  * A *Prometheus* instance for storing metrics data from services and protocol adapters.
  * A *Grafana* instance providing a dashboard visualizing the collected metrics data.

In the example scenario used in the remainder of this guide, the devices will connect to the HTTP and MQTT adapters in
order to publish telemetry data and events. The devices will be authenticated using information stored in the Device
Registry. The data is then forwarded downstream to the example application via the AMQP Messaging Network.

Once Hono has been deployed to your local cluster, run the following commands to create the `hono.env` file which will
be used during the remainder of this guide to set and refresh some environment variables:

~~~sh
echo "export REGISTRY_IP=$(kubectl get service eclipse-hono-service-device-registry-ext --output="jsonpath={.status.loadBalancer.ingress[0]['hostname','ip']}" -n hono)" > hono.env
echo "export HTTP_ADAPTER_IP=$(kubectl get service eclipse-hono-adapter-http --output="jsonpath={.status.loadBalancer.ingress[0]['hostname','ip']}" -n hono)" >> hono.env
echo "export MQTT_ADAPTER_IP=$(kubectl get service eclipse-hono-adapter-mqtt --output="jsonpath={.status.loadBalancer.ingress[0]['hostname','ip']}" -n hono)" >> hono.env
TRUSTSTORE_PATH=/tmp/truststore.pem
kubectl get configmaps eclipse-hono-example-trust-store --template="{{index .data \"ca.crt\"}}" -n hono > ${TRUSTSTORE_PATH}
AMQP_NETWORK_IP=$(kubectl get service eclipse-hono-dispatch-router-ext --output="jsonpath={.status.loadBalancer.ingress[0]['hostname','ip']}" -n hono)
echo "export APP_OPTIONS='--amqp -H ${AMQP_NETWORK_IP} -P 15671 -u consumer@HONO -p verysecret --ca-file ${TRUSTSTORE_PATH} --disable-hostname-verification'" >> hono.env
echo "export CURL_OPTIONS='--insecure'" >> hono.env
echo "export MOSQUITTO_OPTIONS='--cafile ${TRUSTSTORE_PATH} --insecure'" >> hono.env
~~~

Verify that the `hono.env` file has been created in your current working directory:

~~~sh
cat hono.env
~~~
~~~
export REGISTRY_IP=(host name/IP address)
export HTTP_ADAPTER_IP=(host name/IP address)
export MQTT_ADAPTER_IP=(host name/IP address)
export APP_OPTIONS='--amqp -H (host name/IP address) -P 15671 -u consumer@HONO -p verysecret --ca-file /tmp/truststore.pem --disable-hostname-verification'
export CURL_OPTIONS='--insecure'
export MOSQUITTO_OPTIONS='--cafile /tmp/truststore.pem --insecure'
~~~

{{% /tab %}}
{{< /tabs >}}

{{% notice info %}}
We will use the `hono.env` file during the rest of this guide to set and refresh environment variables
that contain the Hono API endpoint addresses and the example device credentials.
{{% /notice %}}


## Registering Devices

When a device tries to connect to one of Hono's protocol adapters, the protocol adapter first tries to authenticate
the device using information kept in the Device Registry. The information maintained in the registry includes the
*tenant* (a logical scope) that the device belongs to, the device's unique *identity* within the tenant and the
*credentials* used by the device for authentication.

Before a device can connect to Hono and publish any data, the corresponding information needs to be added to the
Device Registry.

### Creating a new Tenant

Register a tenant that is configured for Kafka based messaging using Hono's Device Registry's management HTTP API:

~~~sh
# in the folder that contains the hono.env file
source hono.env
curl -i -X POST ${CURL_OPTIONS} -H "content-type: application/json" --data-binary '{
  "ext": {
    "messaging-type": "kafka"
  }
}' https://${REGISTRY_IP}:28443/v1/tenants
~~~
~~~
HTTP/1.1 201 Created
etag: becc93d7-ab0f-48ec-ad26-debdf339cbf4
location: /v1/tenants/85f63e23-1b78-4156-8500-debcbd1a8d35
content-type: application/json; charset=utf-8
content-length: 45

{"id":"85f63e23-1b78-4156-8500-debcbd1a8d35"}
~~~

The response will contain a randomly generated tenant identifier. It will probably be different from the value shown
in the example above. In any case, save it in an environment variable by adding a line like this to the `hono.env`
file created earlier:

~~~sh
echo "export MY_TENANT=85f63e23-1b78-4156-8500-debcbd1a8d35" >> hono.env
~~~

Make sure to actually use the tenant identifier returned by Hono's device registry.

### Adding a Device to the Tenant

Register a device using Hono's Device Registry's management HTTP API:

~~~sh
# in the folder that contains the hono.env file
source hono.env
curl -i -X POST ${CURL_OPTIONS} https://${REGISTRY_IP}:28443/v1/devices/${MY_TENANT}
~~~
~~~
HTTP/1.1 201 Created
etag: 68eab243-3df9-457d-a0ab-b702e57c0c17
location: /v1/devices/85f63e23-1b78-4156-8500-debcbd1a8d35/4412abe2-f219-4099-ae14-b446604ae9c6
content-type: application/json; charset=utf-8
content-length: 45

{"id":"4412abe2-f219-4099-ae14-b446604ae9c6"}
~~~

The response will contain a randomly generated device identifier. It will probably be different from the value shown
in the example above. In any case, save it in an environment variable by adding a line like this to the `hono.env`
file created earlier:

~~~sh
echo "export MY_DEVICE=4412abe2-f219-4099-ae14-b446604ae9c6" >> hono.env
~~~

Make sure to actually use the device identifier returned by Hono's device registry.

### Setting a Password for the Device

Choose a (random) password and register it using Hono's Device Registry's management HTTP API (replace
`this-is-my-password` with your password):

~~~sh
# in directory that contains the hono.env file
echo "export MY_PWD=this-is-my-password" >> hono.env
~~~

~~~sh
source hono.env
curl -i -X PUT ${CURL_OPTIONS} -H "content-type: application/json" --data-binary '[{
  "type": "hashed-password",
  "auth-id": "'${MY_DEVICE}'",
  "secrets": [{
      "pwd-plain": "'${MY_PWD}'"
  }]
}]' https://${REGISTRY_IP}:28443/v1/credentials/${MY_TENANT}/${MY_DEVICE}
~~~
~~~
HTTP/1.1 204 No Content
etag: cf91fd4d-7111-4e8a-af68-c703993a8be1
Content-Length: 0
~~~

<a name="starting-a-consumer"></a>
## Starting the example Application

The telemetry data produced by devices is usually consumed by downstream applications that use it to implement their
corresponding business functionality.
In this guide we will use the Hono command line client to simulate such an application.
The client will connect to Hono's north bound [Telemetry]({{< relref "/api/telemetry-kafka" >}}) and
[Event API]({{< relref "/api/event-kafka" >}})s, subscribe to all telemetry and event messages and log the messages
to the console.

Open a **new** terminal window to run the client from the command line.

~~~sh
# in directory that contains the hono.env file
source hono.env
~~~

~~~sh
# in directory where the hono-cli-*-exec.jar file has been downloaded to
java -jar hono-cli-*-exec.jar app ${APP_OPTIONS} consume --tenant ${MY_TENANT}
~~~

## Publishing Telemetry Data to the HTTP Adapter

Now that the downstream application is running, devices can start publishing telemetry data and events using Hono's
protocol adapters. First, you will simulate a device publishing data to Hono using the HTTP protocol.

Go back to the original terminal and run:

~~~sh
curl -i -u ${MY_DEVICE}@${MY_TENANT}:${MY_PWD} ${CURL_OPTIONS} -H 'Content-Type: application/json' --data-binary '{"temp": 5}' https://${HTTP_ADAPTER_IP}:8443/telemetry
~~~
~~~
HTTP/1.1 202 Accepted
Content-Length: 0
~~~

If you have started the downstream application as described above, you should now see the telemetry message being output
to the application's console in the other terminal. The output should look something like this:

~~~
t 4412abe2-f219-4099-ae14-b446604ae9c6 application/json {"temp": 5} {orig_adapter=hono-http, device_id=my-device, orig_address=/telemetry}
~~~

You can publish more data simply by re-running the `curl` command above with arbitrary payload.

The HTTP Adapter also supports publishing telemetry messages using *at least once* delivery semantics. For information
on how that works and additional examples for interacting with Hono via HTTP, please refer to the
[HTTP Adapter's User Guide]({{< relref "/user-guide/http-adapter" >}}).

## Publishing Events to the HTTP Adapter

In a similar way you can upload events:

~~~sh
curl -i -u ${MY_DEVICE}@${MY_TENANT}:${MY_PWD} ${CURL_OPTIONS} -H 'Content-Type: application/json' --data-binary '{"alarm": "fire"}' https://${HTTP_ADAPTER_IP}:8443/event
~~~
~~~
HTTP/1.1 202 Accepted
Content-Length: 0
~~~

Again, you should see the event being logged to the console of the downstream application.

## Publishing Telemetry Data to the MQTT Adapter

Devices can also publish data to Hono using the MQTT protocol. If you have installed the `mosquitto_pub` command line
client, you can run the following command to publish arbitrary telemetry data to Hono's MQTT adapter using QoS 0:

~~~sh
mosquitto_pub -h ${MQTT_ADAPTER_IP} -p 8883 -u ${MY_DEVICE}@${MY_TENANT} -P ${MY_PWD} ${MOSQUITTO_OPTIONS} -t telemetry -m '{"temp": 5}'
~~~

Again, you should now see the telemetry message being logged to console of the downstream application.

The MQTT Adapter also supports publishing telemetry messages using QoS 1. For information on how that works
and additional examples for interacting with Hono via MQTT, please refer to the
[MQTT Adapter's User Guide]({{< relref "/user-guide/mqtt-adapter" >}}).

## Publishing Events to the MQTT Adapter

In a similar way you can upload events:

~~~sh
mosquitto_pub -h ${MQTT_ADAPTER_IP} -p 8883 -u ${MY_DEVICE}@${MY_TENANT} -P ${MY_PWD} ${MOSQUITTO_OPTIONS} -t event -q 1 -m '{"alarm": "fire"}'
~~~

Again, you should now see the event being logged to console of the downstream application.

{{% notice tip "Congratulations" %}}
You have successfully connected a device to Hono and published sensor data for consumption by an application. The
application consumed messages via Hono's north bound API regardless of the transport protocol used by the device to
publish the data.

**What to try next?**

* Continue with the next sections to learn how applications can send commands to devices by means of the
  [Command & Control API for Kafka]({{< relref "/api/command-and-control-kafka" >}}) and/or
  [Command & Control API for AMQP 1.0]({{< relref "/api/command-and-control" >}}).
* Check out the [User Guides]({{< relref "/user-guide" >}}) to explore more options for devices to connect to Hono
  using different transport protocols.
* Learn more about the managing tenants, devices and credentials using the
  [Device Registry's HTTP API]({{< relref "/user-guide/device-registry" >}}).
{{% /notice %}}

## Advanced: Sending Commands to a Device

The following example will guide you through an advanced feature of Hono. You will see how an application can send a
command to a device and receive a response with the result of processing the command on the device. The communication
direction here is exactly the other way round than with telemetry and events.

The following assumes that the steps in the
[Prerequisites for the Getting started Guide]({{< relref "#prerequisites-for-the-getting-started-guide" >}}) and
[Registering Devices]({{< relref "#registering-devices" >}}) sections above have been completed. To simulate the device,
you can use the Mosquitto tools again while the Hono Command Line Client simulates the application as before.

### Receiving a Command

With the `mosquitto_sub` command you simulate an MQTT device that receives a command.
Create a subscription to the command topic in the terminal for the simulated device:
 
 ~~~sh
# in directory that contains the hono.env file
source hono.env
mosquitto_sub -v -h ${MQTT_ADAPTER_IP} -p 8883 -u ${MY_DEVICE}@${MY_TENANT} -P ${MY_PWD} ${MOSQUITTO_OPTIONS} -t command///req/#
 ~~~

Now that the device is waiting to receive commands, the application can start sending them.

In a **new** terminal window, start the command line client:

~~~sh
# in directory that contains the hono.env file
source hono.env
~~~
~~~sh
# in directory where the hono-cli-*-exec.jar file has been downloaded to
java -jar hono-cli-*-exec.jar app ${APP_OPTIONS} command
~~~
~~~
hono-cli/app/command>
~~~

At the prompt, enter the following to send a one-way command with a JSON payload to the device:

~~~sh
ow --tenant ${MY_TENANT} --device ${MY_DEVICE} -n setVolume --payload '{"level": 50}'
~~~

In the terminal for the simulated device you should see the received command as follows

~~~
command///req//setVolume {"level": 50}
~~~

### Sending a Response to a Command

Now that you have sent a one-way command to the device,  you may get to know *request-response* commands where the
device sends a response to the application. A request-response command received from an application contains an
identifier that is unique to each new command. The device must include this identifier in its response so that the
application can correctly correlate the response with the request.

If you send a request-response command like this

~~~sh
req --tenant ${MY_TENANT} --device ${MY_DEVICE} -n setBrightness --payload '{"level": 87}'
~~~

the application will wait up to 60 seconds for the device's response.

In the terminal for the simulated device you should see the received command that looks like this

~~~
command///req/10117f669c12-09ef-416d-88c1-1787f894856d/setBrightness {"level": 87}
~~~

The topic segment between `req` and `setBrightness` is the request identifier that must be included in the response.

You can cancel the command `mosquitto_sub` in the terminal of the device (press the key combination `Ctrl + C`) to reuse the 
configuration with the  environment variables for sending the response.
The following example shows how a response can be sent via MQTT. Note that the actual identifier from the received
command must be used.

~~~sh
export REQ_ID=10117f669c12-09ef-416d-88c1-1787f894856d
mosquitto_pub -h ${MQTT_ADAPTER_IP} -p 8883 -u ${MY_DEVICE}@${MY_TENANT} -P ${MY_PWD} ${MOSQUITTO_OPTIONS} -t command///res/${REQ_ID}/200 -m '{"current-level": 87}'
~~~

The `200` at the end of the topic is an HTTP status code that reports the result of processing the command to the
application.

If the Command Line Client has successfully received the response in time, it will print a line to the console's
*standard output*:

~~~
res 4412abe2-f219-4099-ae14-b446604ae9c6 200 application/octet-stream {"current-level": 87}
~~~

The command line client will print an error message to *System.err* if the device does not respond to the command
in time. By default, the client will wait for 60 seconds for the response, a longer timeout can be set using the `-r`
option:

~~~sh
req --tenant ${MY_TENANT} --device ${MY_DEVICE} -n setBrightness --payload '{"level": 87}' -r 120
~~~

{{% notice tip "Congratulations" %}}
You have successfully sent commands to a device and responded to them. For more information on Command & Control refer to
[Commands using HTTP]({{< relref "/user-guide/http-adapter#command--control" >}}) 
and [Commands using MQTT]({{< relref "/user-guide/mqtt-adapter#command--control" >}}).
The [Command and Control Concepts]({{< relref "/concepts/command-and-control" >}}) page contains sequence diagrams that
explain this in more detail.
{{% /notice %}}
