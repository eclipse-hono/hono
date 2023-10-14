# Eclipse Hono quick start tutorial

Note: This tutorial follows the Tutorial from the [official home page](https://www.eclipse.org/hono/docs/getting-started/).

## How to use the Script?

First, install the necessary packages from the `requirements.txt` file using

```bash
pip install -r requirements.txt
```

Then run the `quickstart.py` script.

## What does the script do?

* Setup a Tenant and configure it to use Kafka based messaging infrastructure
* Setup a Device in the tenant
* Add credentials to the device
* Start a north bound Kafka consumer for the messages sent by the tenant's devices
* Send a Telemetry message via HTTP API
* Send a Telemetry message via MQTT API 

The output should roughly look like this

```
Registered tenant f184ce49-d906-4a8c-af27-21df4c4acbf1
Registered device 6f948c41-c2da-47f5-a52e-bf013f09e670
Password is set!
You could now start the Hono Command Line Client in another terminal to consume messages from devices:

java -jar hono-cli-2.*-exec.jar app --sandbox consume --tenant=f184ce49-d906-4a8c-af27-21df4c4acbf1

created Kafka consumer ...
waiting for Kafka messages
Sending Telemetry message via HTTP adapter
Sending Telemetry message via MQTT adapter
Got a message: {"temp": 5, "transport": "http"}
Got a message: {"temp": 17, "transport": "mqtt"}
```