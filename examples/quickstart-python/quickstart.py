
#  Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
#
#  See the NOTICE file(s) distributed with this work for additional
#  information regarding copyright ownership.
#
#  This program and the accompanying materials are made available under the
#  terms of the Eclipse Public License 2.0 which is available at
#  http://www.eclipse.org/legal/epl-2.0
#
#  SPDX-License-Identifier: EPL-2.0

from __future__ import print_function, unicode_literals

import json
import threading
import time

import requests

from kafka import KafkaConsumer
from paho.mqtt.publish import single
from requests.auth import HTTPBasicAuth

HONO_SANDBOX_HOSTNAME = "hono.eclipseprojects.io"
CA_FILE = "/etc/ssl/certs/ca-certificates.crt"
DEVICE_PASSWORD = "my-secret-password"

registry_base_url = f"https://{HONO_SANDBOX_HOSTNAME}:28443/v1"
http_adapter_ip = HONO_SANDBOX_HOSTNAME
mqtt_adapter_ip = HONO_SANDBOX_HOSTNAME
amqp_network_ip = HONO_SANDBOX_HOSTNAME

# Register Tenant
tenant = requests.post(
    url=f"{registry_base_url}/tenants",
    headers={"content-type": "application/json"},
    data=json.dumps({"ext": {"messaging-type": "kafka"}})).json()
tenant_id = tenant["id"]

print(f"Registered tenant {tenant_id}")

# Add Device to Tenant
device = requests.post(f"{registry_base_url}/devices/{tenant_id}").json()
device_id = device["id"]

print(f'Registered device {device_id}')

# Set Device Password
code = requests.put(
    url=f"{registry_base_url}/credentials/{tenant_id}/{device_id}",
    headers={"content-type": "application/json"},
    data=json.dumps(
        [{"type": "hashed-password", "auth-id": device_id, "secrets": [{"pwd-plain": DEVICE_PASSWORD}]}]))

if code.status_code == 204:
    print("Password is set!")
else:
    print("Unable to set Password")

# Now we can start the client application
print("You could now start the Hono Command Line Client in another terminal to consume messages from devices:")
print()
cmd = f"java -jar hono-cli-2.*-exec.jar app --sandbox consume --tenant={tenant_id}"
print(cmd)
print()


# input("Press Enter to continue...")

class DownstreamApp(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)
        self.stop_event = threading.Event()
        self._topic_name = f"hono.telemetry.{tenant_id}"

    def stop(self):
        self.stop_event.set()

    def run(self):

        consumer = KafkaConsumer(
            self._topic_name,
            bootstrap_servers=f"{HONO_SANDBOX_HOSTNAME}:9094",
            group_id="foo",
            max_poll_records=10,
            ssl_cafile=CA_FILE,
            security_protocol="SASL_SSL",
            sasl_mechanism="SCRAM-SHA-512",
            sasl_plain_username="hono",
            sasl_plain_password="hono-secret",
            consumer_timeout_ms=5000)

        print("created Kafka consumer ...")

        print("waiting for Kafka messages")
        # msg_count = 0
        for message in consumer:
            print(f"received message: {message.value.decode()}")
            # msg_count += 1
            if self.stop_event.is_set():
                break

        consumer.close()


task = DownstreamApp()
task.start()
# wait for topic to be created
time.sleep(3)


def send_message_via_http_adapter():
    # nosemgrep: no-auth-over-http
    return requests.post(
        url=f"https://{http_adapter_ip}:8443/telemetry",
        headers={"content-type": "application/json"},
        data=json.dumps({"temp": 5, "transport": "http"}),
        auth=HTTPBasicAuth(f"{device_id}@{tenant_id}", DEVICE_PASSWORD))


# Send HTTP Message
print("Sending Telemetry message via HTTP adapter")
response = send_message_via_http_adapter()
while response.status_code != 202:
    print(f"failed to send message via HTTP adapter, status code: {response.status_code}")
    time.sleep(2)
    print("trying again ...")
    response = send_message_via_http_adapter()

# Send Message via MQTT
print("Sending Telemetry message via MQTT adapter")
single(
    topic="telemetry",
    payload=json.dumps({"temp": 17, "transport": "mqtt"}),
    hostname=mqtt_adapter_ip,
    port=8883,
    auth={"username": f"{device_id}@{tenant_id}", "password": DEVICE_PASSWORD},
    tls={"ca_certs": CA_FILE})

task.stop()
task.join(timeout=5)
