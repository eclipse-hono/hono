<!--
    Copyright (c) 2020 Contributors to the Eclipse Foundation

    See the NOTICE file(s) distributed with this work for additional
    information regarding copyright ownership.

    This program and the accompanying materials are made available under the
    terms of the Eclipse Public License 2.0 which is available at
    http://www.eclipse.org/legal/epl-2.0

    SPDX-License-Identifier: EPL-2.0
 -->
# Eclipse Hono Quickstart tutorial

Note: This tutorial follows the Tutorial from the official homepage https://www.eclipse.org/hono/getting-started/.

## How to use the Script?

First, install the necessary packages from the `requirements.txt` file.
Then, just run the `quickstart.py` script.

## What does the script do?

* Setup a Tenant
* Setup a Device in the tenant
* Add credentials to the device
* Start an (northbound) AMQP Receiver as "Consumer" of the data
* Send a Telemetry message via HTTP API
* Send a Telemetry message via MQTT API 

The output should roughly look like this

```
Registered tenant f184ce49-d906-4a8c-af27-21df4c4acbf1
Registered device 6f948c41-c2da-47f5-a52e-bf013f09e670
Password is set!
We could use the Hono Client now...

java -jar hono-cli-*-exec.jar --hono.client.host=hono.eclipseprojects.io --hono.client.port=15672 --hono.client.username=consumer@HONO --hono.client.password=verysecret --spring.profiles.active=receiver --tenant.id=f184ce49-d906-4a8c-af27-21df4c4acbf1

Using source: amqp://hono.eclipseprojects.io:15672/telemetry
Using address: telemetry/f184ce49-d906-4a8c-af27-21df4c4acbf1
Starting (northbound) AMQP Connection...
Started
Send Telemetry Message via HTTP
HTTP sent successful
Send Telemetry Message via MQTT
Got a message:
b'{"temp": 5, "transport": "http"}'
Got a message:
b'{"temp": 17, "transport": "mqtt"}'
Stopping (northbound) AMQP Connection...
```