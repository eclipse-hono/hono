+++
title = "Load Tests with JMeter"
weight = 230
+++

The Hono project includes a JMeter plugin, which could be used to send and receive telemetry/event data. It uses the 
Hono client and acts as an adapter on the sender side and as an application/solution on the receiver side.
<!--more-->

## Installation
 
 - Run `mvn clean install assembly:single` in the JMeter module to build the needed jar with dependencies for JMeter
 - Install [JMeter](http://jmeter.apache.org/download_jmeter.cgi)
 - Copy the `<hono-installation>/jmeter/target/hono-jmeter-<version>-jar-with-dependencies.jar` into the  `<jmeter-installation>/lib/ext` directory.
 - Start JMeter

## Provided test plans

To start you can load the test plan `<hono-installation>/jmeter/src/jmeter/hono_jmeter_runtime.jmx` in JMeter. 
It provides a test plan, which runs AMQP 1.0 receiver and sender sampler for a given time with a given amount of 
threads (devices/solution-receivers). It assumes the typical Hono "get started" docker installation. 

You may need to change some properties of the Hono Sender/Hono Receiver - e.g. the truststore path, ports etc.

There is also the test plan `<hono-installation>/jmeter/src/jmeter/hono_jmeter_runtime_adapters.jmx`, which provides a receiver (AMQP 1.0) and senders for the REST/MQTT/Kura adapters. To use the MQTT/Kura senders you need the JMeter plugin for MQTT in your JMeter installation.

## Important

 - If you want to use more than one thread for the receivers, you should configure the qpid router to `balanced` 
 (vs. `multicast`).
 - Sender could be configured to wait for n active receivers (from this test plan and JMeter instance) - this could be 
 used to make sure, that the receivers on the address (like `telemetry/DEFAULT_TENANT`) are started before sending begins.

## Overview

{{< figure src="../Hono_jmeter.svg" title="JMeter with Hono" width="70%">}}