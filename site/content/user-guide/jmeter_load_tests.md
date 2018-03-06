+++
title = "Load Tests with JMeter"
weight = 270
+++

Eclipse Hono&trade; comes with an [Apache JMeter](https://jmeter.apache.org) plugin which provides *samplers* that can be used to send and receive telemetry/event data from JMeter test plans.
<!--more-->

The plugin provides a *Hono Sender Sampler* and a *Hono Receiver Sampler* which can be used to play the roles of a protocol adapter (sender) and an application/solution (consumer). The diagram below illustrates how the components are related to each other.

{{< figure src="../jmeter-overview.png" title="JMeter with Hono" >}}

## Installation
 
1. Install [JMeter](https://jmeter.apache.org/download_jmeter.cgi)
2. Copy the plugin jar file `<hono-installation>/jmeter/target/plugin/hono-jmeter-<version>-jar-with-dependencies.jar` to the  `<jmeter-installation>/lib/ext` folder.
3. Start JMeter

## Example Test Plans

The `<hono-installation>/jmeter/src/jmeter` folder contains several JMeter test plans which you can use as a basis for your own tests. All test plans can be run against any standard Hono installation, e.g. as deployed in the [Getting started Guide]({{< relref "getting-started.md" >}}).

1.  `amqp_messaging_throughput_test.jmx` runs a set of AMQP 1.0 senders and consumers for a given period of time. The senders publish data to Hono Messaging directly while the consumers receive messages from the AMQP 1.0 Messaging Network (in case of the example installation this is the Qpid Dispatch Router). The test plan measures the number of messages that are sent/received during the test execution.<br>
The sender can be configured to wait for *n active receivers* (from this test plan and JMeter instance) which can be used to make sure, that the receivers consuming from the corresponding address (e.g. `telemetry/DEFAULT_TENANT`) are up and running before the senders begin to publish messages.
2.  `http_messaging_throughput_test.jmx` runs a set of HTTP clients and AMQP 1.0 consumers for a given period of time. The senders publish data to Hono' HTTP adapter while the consumers receive messages from the AMQP 1.0 Messaging Network (in case of the example installation this is the Qpid Dispatch Router). The test plan measures the number of messages that are sent/received during the test execution.
3.  `mqtt_messaging_throughput_test.jmx` runs a set of MQTT clients and AMQP 1.0 consumers for a given period of time. The clients publish data to Hono' MQTT adapter while the consumers receive messages from the AMQP 1.0 Messaging Network (in case of the example installation this is the Qpid Dispatch Router). The test plan measures the number of messages that are sent/received during the test execution. To use this plan you also need to add the [JMeter plugin for MQTT](https://github.com/emqtt/mqtt-jmeter) to your JMeter plugin path.

It is recommended to run the test plans in *non-gui mode* as illustrated by the example shell scripts that are contained in the `<hono-installation>/jmeter/src/jmeter` folder. You may need to adapt some of the properties to reflect your concrete setup, e.g. the path to the trust store, host names, ports etc.
