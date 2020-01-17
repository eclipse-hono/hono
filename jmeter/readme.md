# Hono Load Tests

This module contains load tests for Hono. The load tests can be executed against any Hono installation.

The load tests can be executed as:

* local bash script with local installation of Apache JMeter 5.1.1 with MQTT XMeter 1.0.1 and Hono JMeter Plugin [Load Tests with JMeter on the project web site](https://www.eclipse.org/hono/docs/user-guide/jmeter_load_tests/)
* within a docker container created with maven build flag `-Pbuild-jmeter-image`. See the [Getting Started guide on the project web site](https://www.eclipse.org/hono/getting-started/) for instructions on building the Docker images.

## Load test scenarios

The following load test scenarios are currently supported:

* `load-test-http-router.sh` -> Sending Telemetry Messages against HTTP Adapter and receiving them from the dispatch router
* `load-test-mqtt-router.sh` -> Sending Telemetry Messages against MQTT Adapter and receiving them from the dispatch router
* `load-test-messaging-router.sh` -> Sending Telemetry Messages against dispatch router and receiving them from the dispatch router

## Local bash script execution

In order to run the load tests you will need the following:

* a working *Apache JMeter 5.1.1* installation
* added the plugin *MQTT XMeter 1.0.1* to JMeter /lib/ext folder
* a successful maven build of Eclipse/Hono

Execute load test:

    # in directory: hono/jmeter/src/jmeter

    ./load-test-http-router.sh <apache jmeter install dir> <dns/ip of Hono Device Registry> <dns/ip of Hono Dispatch Router> <dns/ip of Hono HTTP Adapter> <#Devices to simulate> <#Consumers to simulate>
    # e.g. 
    # ./load-test-http-router.sh /home/hono/apache-jmeter-5.1.1 registry.hono.yourdomain.com router.hono.yourdomain.com http.hono.yourdomain.com 10 2

    ./load-test-mqtt-router.sh <apache jmeter install dir> <dns/ip of Hono Device Registry> <dns/ip of Hono Dispatch Router> <dns/ip of Hono MQTT Adapter> <#Devices to simulate> <#Consumers to simulate>
    # e.g.
    # ./load-test-mqtt-router.sh /home/hono/apache-jmeter-5.1.1 registry.hono.yourdomain.com router.hono.yourdomain.com mqtt.hono.yourdomain.com 10 2

    ./load-test-messaging-router.sh <apache jmeter install dir> <dns/ip of Hono Device Registry> <#Sender/Consumers to simulate>
    # e.g.
    # ./load-test-messaging-router.sh /home/hono/apache-jmeter-5.1.1 router.hono.yourdomain.com 4

## Container based execution

In order to run the load tests you will need the following:

* a successful maven build of Eclipse/Hono

Execute load test:

    docker run -it -e REGISTRATION_HOST=<dns/ip of Hono Device Registry> -e ROUTER_HOST=<dns/ip of Hono Dispatch Router> -e HTTP_ADAPTER_HOST=<dns/ip of Hono HTTP Adapter> -e DEVICE_COUNT=<#Devices to simulate> <jmeter container image name> /bin/bash load-test-http-router.sh
    # e.g.
    # docker run -it -e REGISTRATION_HOST=registry.hono.yourdomain.com -e ROUTER_HOST=router.hono.yourdomain.com -e HTTP_ADAPTER_HOST=http.hono.yourdomain.com -e DEVICE_COUNT=5 yourregistry.io/eclipse/hono-jmeter /bin/bash load-test-messaging-router.sh

    docker run -it -e REGISTRATION_HOST=<dns/ip of Hono Device Registry> -e ROUTER_HOST=<dns/ip of Hono Dispatch Router> -e MQTT_ADAPTER_HOST=<dns/ip of Hono MQTT Adapter> -e DEVICE_COUNT=<#Devices to simulate> <jmeter container image name> /bin/bash load-test-mqtt-router.sh
    # e.g.
    # docker run -it -e REGISTRATION_HOST=registry.hono.yourdomain.com -e ROUTER_HOST=router.hono.yourdomain.com -e MQTT_ADAPTER_HOST=mqtt.hono.yourdomain.com -e DEVICE_COUNT=5 yourregistry.io/eclipse/hono-jmeter /bin/bash load-test-messaging-router.sh

    docker run -it -e REGISTRATION_HOST=<dns/ip of Hono Device Registry> -e ROUTER_HOST=<dns/ip of Hono Dispatch Router> -e MQTT_ADAPTER_HOST=<dns/ip of Hono MQTT Adapter> -e HTTP_ADAPTER_HOST=<dns/ip of Hono HTTP Adapter> -e DEVICE_COUNT=<#Devices to simulate> <jmeter container image name> /bin/bash load-test-messaging-router.sh
    # e.g.
    # docker run -it -e REGISTRATION_HOST=registry.hono.yourdomain.com -e ROUTER_HOST=router.hono.yourdomain.com -e MQTT_ADAPTER_HOST=mqtt.hono.yourdomain.com -e HTTP_ADAPTER_HOST=http.hono.yourdomain.com -e DEVICE_COUNT=5 yourregistry.io/eclipse/hono-jmeter /bin/bash load-test-messaging-router.sh

It is possible to modify following parameters via docker env variables:

| Parameter | Type | Default |
|-----------|------|---------|
|REGISTRATION_HOST|mandatory||
|REGISTRATION_PORT|optional|28080|
|ROUTER_HOST|mandatory||
|ROUTER_PORT|optional|15672|
|MQTT_ADAPTER_HOST|mandatory||
|MQTT_ADAPTER_PORT|optional|1883|
|HTTP_ADAPTER_HOST|mandatory||
|HTTP_ADAPTER_PORT|optional|8080|
|SAMPLE_LOG|optional|load-test-<mqtt/http/messaging>-router.jtl|
|TEST_LOG|optional|load-test-<mqtt/http/messaging>-router.log|
|DEVICE_COUNT|optional|10|
|CONSUMER_COUNT|optional|2|