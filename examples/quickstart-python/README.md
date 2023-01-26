# Eclipse Hono quick start tutorial

Note: This tutorial follows the Tutorial from the [official home page](https://www.eclipse.org/hono/docs/getting-started/).

## How to use the Script?

First, install the necessary packages from the `requirements.txt` file using

```bash
pip install -r requirements.txt
```

Then run the `quickstart.py` script.

## What does the script do?

* Setup a Tenant and configure it to use AMQP 1.0 based messaging infrastructure
* Setup a Device in the tenant
* Add credentials to the device
* Start a north bound AMQP 1.0 receiver for the messages sent by the tenant's devices
* Send a Telemetry message via HTTP API
* Send a Telemetry message via MQTT API 

The output should roughly look like this

```bash
Registered tenant f184ce49-d906-4a8c-af27-21df4c4acbf1
Registered device 6f948c41-c2da-47f5-a52e-bf013f09e670
Password is set!
You could now start the Hono Command Line Client in another terminal to consume messages from devices:

java -jar hono-cli-2.*-exec.jar app --sandbox consume --tenant=f184ce49-d906-4a8c-af27-21df4c4acbf1

Using source: amqp://hono.eclipseprojects.io:15672/telemetry
Using address: telemetry/f184ce49-d906-4a8c-af27-21df4c4acbf1
Starting (north bound) AMQP Connection...
Started
Send Telemetry Message via HTTP
HTTP sent successful
Send Telemetry Message via MQTT
Got a message:
b'{"temp": 5, "transport": "http"}
Got a message:
b'{"temp": 17, "transport": "mqtt"}
Stopping (north bound) AMQP Connection...
```