+++
title = "Load Tests with JMeter"
weight = 270
+++

Eclipse Hono&trade; comes with an [Apache JMeter](https://jmeter.apache.org) plugin which can be used to send and receive telemetry/event data as part of JMeter test scripts.
<!--more-->

The plugin provides two JMeter `Sampler`s which can be used to play the roles of a protocol adapter (sender) and an application/solution (consumer).

## Installation
 
1. Install [JMeter](https://jmeter.apache.org/download_jmeter.cgi)
2. Copy the `<hono-installation>/jmeter/target/hono-jmeter-<version>-jar-with-dependencies.jar` into the  `<jmeter-installation>/lib/ext` directory.
3. Start JMeter

## Provided test plans

To start you can load the test plan `<hono-installation>/jmeter/src/jmeter/hono_jmeter_runtime.jmx` in JMeter. 
It provides a test plan, which runs AMQP 1.0 receiver and sender sampler for a given time with a given amount of 
threads (devices/solution-receivers). It assumes the typical Hono "get started" docker installation. 

You may need to change some properties of the Hono Sender/Hono Receiver - e.g. the trust store path, ports etc.

There is also the test plan `<hono-installation>/jmeter/src/jmeter/hono_jmeter_runtime_adapters.jmx`, which provides a receiver (AMQP 1.0) and senders for the REST/MQTT/Kura adapters. To use the MQTT/Kura senders you need the JMeter plugin for MQTT in your JMeter installation.

## Important

* The sender can be configured to wait for *n active receivers* (from this test plan and JMeter instance) which can be used to make sure, that the receivers consuming from the corresponding address (e.g. `telemetry/DEFAULT_TENANT`) are up and running before the senders begin to publish messages.

## Overview

{{< figure src="../Hono_jmeter.svg" title="JMeter with Hono" width="70%">}}