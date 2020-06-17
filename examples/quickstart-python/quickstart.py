
#  Copyright (c) 2020 Contributors to the Eclipse Foundation
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
from paho.mqtt.publish import single
from proton.handlers import MessagingHandler
from proton.reactor import Container
from requests.auth import HTTPBasicAuth

registryIp = "hono.eclipseprojects.io"
httpAdapterIp = "hono.eclipseprojects.io"
mqttAdapterIp = "hono.eclipseprojects.io"
amqpNetworkIp = "hono.eclipseprojects.io"

# Register Tenant
tenant = requests.post(f'http://{registryIp}:28080/v1/tenants').json()
tenantId = tenant["id"]

print(f'Registered tenant {tenantId}')

# Add Device to Tenant
device = requests.post(f'http://{registryIp}:28080/v1/devices/{tenantId}').json()
deviceId = device["id"]

print(f'Registered device {deviceId}')

# Set Device Password
devicePassword = "my-secret-password"

code = requests.put(f'http://{registryIp}:28080/v1/credentials/{tenantId}/{deviceId}',
                    headers={'content-type': 'application/json'},
                    data=json.dumps(
                        [{"type": "hashed-password", "auth-id": deviceId, "secrets": [{"pwd-plain": devicePassword}]}]))

if code.status_code == 204:
    print("Password is set!")
else:
    print("Unnable to set Password")

# Now we can start the client application
print("We could use the Hono Client now...")
print()
cmd = f'java -jar hono-cli-*-exec.jar --hono.client.host={amqpNetworkIp} ' \
    f'--hono.client.port=15672 --hono.client.username=consumer@HONO ' \
    f'--hono.client.password=verysecret --spring.profiles.active=receiver ' \
    f'--tenant.id={tenantId}'
print(cmd)
print()


# input("Press Enter to continue...")

class AmqpHandler(MessagingHandler):
    """
    Handler for "northbound side" where Messages are received
    via AMQP.
    """
    def __init__(self, server, address):
        super(AmqpHandler, self).__init__()
        self.server = server
        self.address = address

    def on_start(self, event):
        conn = event.container.connect(self.server, user="consumer@HONO", password="verysecret")
        event.container.create_receiver(conn, self.address)

    def on_connection_error(self, event):
        print("Connection Error")

    def on_link_error(self, event):
        print("Link Error")

    def on_message(self, event):
        print("Got a message:")
        print(event.message.body)


# Prepare the container
uri = f'amqp://{amqpNetworkIp}:15672'
address = f'telemetry/{tenantId}'
print("Using source: " + uri)
print("Using address: " + address)
container = Container(AmqpHandler(uri, address))

# run container in separate thread
print("Starting (northbound) AMQP Connection...")
thread = threading.Thread(target=lambda: container.run(), daemon=True)
thread.start()

# Give it some time to link
time.sleep(2)

# Send HTTP Message
print("Send Telemetry Message via HTTP")
response = requests.post(f'http://{httpAdapterIp}:8080/telemetry', headers={"content-type": "application/json"},
                         data=json.dumps({"temp": 5, "transport": "http"}),
                         auth=HTTPBasicAuth(f'{deviceId}@{tenantId}', f'{devicePassword}'))

if response.status_code == 202:
    print("HTTP sent successful")
else:
    print("HTTP message not sent")

# Send Message via MQTT
print("Send Telemetry Message via MQTT")
single("telemetry", payload=json.dumps({"temp": 17, "transport": "mqtt"}),
       hostname=mqttAdapterIp,
       auth={"username": f'{deviceId}@{tenantId}', "password": devicePassword})

# Wait a bit for the MQTT Message to arrive
time.sleep(2)

# Stop container
print("Stopping (northbound) AMQP Connection...")
container.stop()
thread.join(timeout=5)
