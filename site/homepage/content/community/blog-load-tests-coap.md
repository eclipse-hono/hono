+++
linkTitle = "Hono Load Tests"
title = "Hono Load Tests with CoAP devices"
description = "This page is presenting load tests of an Eclipse Hono deployment with simulated CoAP client devices."
weight = 650
type = "page"
+++

This page is presenting load tests of an Eclipse Hono deployment with simulated CoAP client devices. It shows how the system is configured and what load it could handle.

## Components

The setup consists of following components.

### Kubernetes

The Kubernetes is AWS EKS cluster using 3 x `c5a.large machines`.

### Hono

Hono is deployed using [Eclipse IoT Packages helm chart](https://github.com/eclipse/packages/tree/master/charts/hono), version 2.6.0.

Specific helm values:

~~~sh
# Configuration properties for protocol adapters.
adapters:
  # kafkaMessagingSpec contains the configuration used by all protocol
  # adapters for connecting to the Kafka cluster to be used for messaging.
  # This property MUST be set if "messagingNetworkTypes" contains "kafka" and
  # "kafkaMessagingClusterExample.enabled" is set to false.
  # Please refer to https://www.eclipse.org/hono/docs/admin-guide/common-config/#kafka-based-messaging-configuration
  # for a description of supported properties.
  kafkaMessagingSpec:
    commonClientConfig:
      bootstrap.servers: "xxx"
      ssl.endpoint.identification.algorithm: https
      security.protocol: SSL
      connections.max.idle.ms: 240000
    command:
      consumerConfig:
        "compression.type": none
    commandInternal:
      adminClientConfig:
        "compression.type": none
    commandResponse:
      producerConfig:
        acks: all
        delivery.timeout.ms: 5000
        request.timeout.ms: 1250
        max.block.ms: 2500
        "compression.type": none
    event:
      producerConfig:
        acks: all
        delivery.timeout.ms: 5000
        request.timeout.ms: 1250
        max.block.ms: 2500
        "compression.type": none
    telemetry:
      producerConfig:
        acks: all
        delivery.timeout.ms: 5000
        request.timeout.ms: 1250
        max.block.ms: 2500
        "compression.type": none

  # tenantSpec contains Hono client properties used by all protocol adapters for
  # connecting to the Tenant service.
  # This property MUST be set if "deviceRegistryExample.enabled" is set to false.
  # Please refer to https://www.eclipse.org/hono/docs/admin-guide/hono-client-configuration/
  # for a description of supported properties.
  tenantSpec:
    flowLatency: 1000
    requestTimeout: 500

  # deviceRegistrationSpec contains Hono client properties used by all protocol adapters for
  # connecting to the Device Registration service.
  # This property MUST be set if "deviceRegistryExample.enabled" is set to false.
  # Please refer to https://www.eclipse.org/hono/docs/admin-guide/hono-client-configuration/
  # for a description of supported properties.
  deviceRegistrationSpec:
    flowLatency: 1000
    responseCacheMaxSize: 10000
    requestTimeout: 500

  # credentialsSpec contains Hono client properties used by all protocol adapters for
  # connecting to the Credentials service.
  # This property MUST be set if "deviceRegistryExample.enabled" is set to false.
  # Please refer to https://www.eclipse.org/hono/docs/admin-guide/hono-client-configuration/
  # for a description of supported properties.
  credentialsSpec:
    flowLatency: 1000
    requestTimeout: 500

  # commandRouterSpec contains Hono client properties used by all protocol adapters for
  # connecting to the Command Router service.
  # If not set, default properties will be used for establishing a TLS based connection
  # to the command router server.
  # Please refer to https://www.eclipse.org/hono/docs/admin-guide/hono-client-configuration/
  # for a description of supported properties.
  commandRouterSpec:
    flowLatency: 1000
    requestTimeout: 500

  amqp:
    enabled: false

  coap:
    enabled: true

    # quarkusLoggingProfile indicates at which level the Quarkus based variant of the component should log.
    # Supported values are "prod", "dev" or "trace"
    quarkusLoggingProfile: "prod"
    # resources contains the container's requests and limits for CPU and memory
    # as defined by the Kubernetes API.
    # Refer to https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
    # for a description of the properties' semantics.
    resources:
      requests:
        cpu: "500m"
        memory: "640Mi"
      limits:
        cpu: "3"
        memory: "2048Mi"
    svc:
      # Provide Network Load Balancer service to make the CoAP server accessible from internet
      annotations:
        service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
        service.beta.kubernetes.io/aws-load-balancer-nlb-target-type: "instance"
        service.beta.kubernetes.io/aws-load-balancer-scheme: "internet-facing"
        service.beta.kubernetes.io/aws-load-balancer-cross-zone-load-balancing-enabled: "true"
        service.beta.kubernetes.io/aws-load-balancer-healthcheck-interval: "10"
        service.beta.kubernetes.io/aws-load-balancer-healthcheck-timeout: "6"
        service.beta.kubernetes.io/aws-load-balancer-healthcheck-unhealthy-threshold: "2"

    # hono contains the adapter's configuration properties
    hono:
      # coap contains configuration properties for the adapter's
      # exposed CoAP endpoints.
      # If not set, the adapter by default exposes the secure port
      # using an example key and certificate.
      coap:
         tenantIdleTimeout: "1h"
         maxPayloadSize: 64000
         timeoutToAck: 500
         coapThreads: 8
         dtlsThreads: 8
         maxConnections: 80000

  http:
    enabled: false

  mqtt:
    enabled: false

# authServer contains configuration properties for the Auth Server component.
authServer:
  # quarkusLoggingProfile indicates at which level the Quarkus based variant of the component should log.
  # Supported values are "prod", "dev" or "trace"
  quarkusLoggingProfile: "prod"
  # resources contains the container's requests and limits for CPU and memory
  # as defined by the Kubernetes API.
  # Refer to https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
  # for a description of the properties' semantics.
  resources:
    requests:
      cpu: "500m"
      memory: "512Mi"
    limits:
      cpu: "3"
      memory: "1Gi"

# deviceRegistryExample contains configuration properties for the
# example Device Registry.
deviceRegistryExample:

  # Sets the type of the device registry to be deployed. The following types are defined:
  # - embedded: Embedded JDBC based device registry. Also refer to the section "embeddedJdbcDeviceRegistry"
  # for additional configuration.
  # - mongodb: MongoDB based device registry. Also refer to the section "mongoDBBasedDeviceRegistry"
  # for additional configuration.
  # - jdbc: JDBC based device registry. Also refer to the section "jdbcBasedDeviceRegistry"
  # for additional configuration.
  type: "mongodb"

  # addExampleData indicates whether example data is inserted after the device registry is deployed
  addExampleData: false

  # hono contains the Device Registry's configuration properties as defined in
  # https://www.eclipseorg/hono/docs/admin-guide/device-registry-config/
  hono:
    # auth contains Hono client properties used by the example registry for
    # connecting to the Authentication service.
    # If not set, the registry by default uses the Auth Server component to
    # authenticate clients.
    auth:
      flowLatency: 1000
      connectTimeout: 2000

  # mongoDBBasedDeviceRegistry contains configuration properties specific to the
  # MongoDB based device registry.
  mongoDBBasedDeviceRegistry:

    # quarkusLoggingProfile indicates at which level the Quarkus based variant of the component should log.
    # Supported values are "prod", "dev" or "trace"
    quarkusLoggingProfile: "prod"

    # resources contains the container's requests and limits for CPU and memory
    # as defined by the Kubernetes API.
    # Refer to https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
    # for a description of the properties' semantics.
    resources:
      requests:
        cpu: "500m"
        memory: "512Mi"
      limits:
        cpu: "3"
        memory: "2Gi"
    # mongodb contains the configuration properties to connect to the MongoDB database instance.
    # If you would like to use an already existing MongoDB database instance, then configure
    # the below section accordingly.
    mongodb:
      # The connection string for MongoDb.
      # Could be taken from Atlas UI with username and password substitution and adding the database name as a suffix.
      # Optional parameters could be passed as well (using '?')
      connectionString: "xxx"

# commandRouterService contains configuration properties for the
# Command Router service.
commandRouterService:

  # quarkusLoggingProfile indicates at which level the Quarkus based variant of the component should log.
  # Supported values are "prod", "dev" or "trace"
  quarkusLoggingProfile: "prod"
  # resources contains the container's requests and limits for CPU and memory
  # as defined by the Kubernetes API.
  # Refer to https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
  # for a description of the properties' semantics.
  resources:
    requests:
      cpu: "500m"
      memory: "512Mi"
    limits:
      cpu: "3"
      memory: "1Gi"

  # hono contains the service's configuration properties as defined in
  # https://www.eclipse.org/hono/docs/admin-guide/command-router-config/
  hono:
    app:
      # maxInstances defines the number of adapter Verticle instances to deploy
      # to the vert.x runtime during start-up.
      maxInstances: 2
    # auth contains Hono client properties used by the service for
    # connecting to the Authentication service.
    # If not set, the service by default uses the Auth Server component to
    # authenticate clients.
    auth:
      flowLatency: 1000

    commandRouter:
      # cache contains properties configuring the embedded or remote cache that
      # the Command Router uses for storing routing information. Please refer to Hono's Command Router admin guide at
      # https://www.eclipse.org/hono/docs/admin-guide/command-router-config/#data-grid-connection-configuration for
      # details regarding cache configuration.
      # If not set explicitly here and "dataGridExample.enabled" is "true", the example data grid is used.
      # If "dataGridExample.enabled" is "false", an embedded cache with a default configuration will be used.
      # Note that when using the embedded cache, the Command Router pod does NOT support scaling up to a replica
      # count > 1
      cache:
        remote:
          serverList: "eclipse-hono-data-grid:11222"
          authServerName: "eclipse-hono-data-grid"
          authUsername: "xxx"
          authPassword: "xxx"
          authRealm: "ApplicationRealm"
          saslMechanism: "SCRAM-SHA-512"
          connectTimeout: 500
          socketTimeout: 500
          connectionPool:
            minIdle: 10
            maxActive: 10
            maxWait: 500
            maxPendingRequests: 400
          defaultExecutorFactory:
            poolSize: 200

# kafkaMessagingClusterExample contains properties for configuring an example Kafka cluster
# to be used for messaging if "messagingNetworkTypes" contains "kafka"
kafkaMessagingClusterExample:
  # set to false as external Kafka is used (MSK)
  enabled: false

jaegerBackendExample:
  enabled: true
  # resources contains the container's requests and limits for CPU and memory
  # as defined by the Kubernetes API.
  # Refer to https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
  # for a description of the properties' semantics.
  resources:
    requests:
      cpu: "1"
      memory: "1Gi"
    limits:
      cpu: "2"
      memory: "2Gi"

  # env contains environment variables to set for the Jaeger all-in-one container.
  env:
  - name: "MEMORY_MAX_TRACES"
    value: "20000"

# dataGridExample contains properties for configuring an example data grid
# to be used by the Command Router component
dataGridExample:
  enabled: true
  # resources contains the container's requests and limits for CPU and memory
  # as defined by the Kubernetes API.
  # Refer to https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
  # for a description of the properties' semantics.
  resources:
    requests:
      cpu: "500m"
      memory: "512Mi"
    limits:
      cpu: "3"
      memory: "2Gi"
~~~

Additional configuration.
This is applied manually after helm install as the settings were not possible via helm chart.
~~~sh
# Remove device-registry public access manually as not possible via the helm chart
kubectl -n $namespace delete svc eclipse-hono-service-device-registry-ext
# Remove jaeger query public access manually as not possible via the helm chart
kubectl -n $namespace patch svc eclipse-hono-jaeger-query -p '{"spec": {"type":"ClusterIP"}}'

# Increase data grid memory as not possible via the helm chart
kubectl get cm eclipse-hono-data-grid-conf -n hono -o yaml | sed -e's|max-size=\"30 MB\"|max-size=\"100 MB\"|' | kubectl apply -f -
kubectl -n $namespace rollout restart statefulset eclipse-hono-data-grid
kubectl -n $namespace rollout status statefulset eclipse-hono-data-grid

# Scale infinispan manually as not possible via the helm chart
kubectl -n $namespace scale statefulset eclipse-hono-data-grid --replicas=3
# Scale auth service manually as not possible via the helm chart
kubectl -n $namespace scale deployment eclipse-hono-service-auth --replicas=2
# Scale device registry manually as not possible via the helm chart
kubectl -n $namespace scale deployment eclipse-hono-service-device-registry --replicas=2
# Scale command router manually as not possible via the helm chart
kubectl -n $namespace scale deployment eclipse-hono-service-command-router --replicas=2
# Scale coap adapter manually as not possible via the helm chart
kubectl -n $namespace scale deployment eclipse-hono-adapter-coap --replicas=3
# Scale command router manually as not possible via the helm chart
kubectl -n $namespace scale deployment eclipse-hono-service-command-router --replicas=2
~~~

### MongoDB
The device registry is MongoDB based using MongoDB Atlas service with M2 cluster tier.
The network access between Atlas and AWS is via peering connection.

### Kafka
The Messaging infrastructure is AWS MSK with `kafka.m5.large` broker size.

### Device simulator
It is developed and used a device simulator that could produce configurable amount of load.

### Backend simulator
It is developed and used backend simulator that acts as business application connected to the Kafka broker.

## Scenarios and configuration
The load tests are covering device connectivity with CoAP protocol only.
Device authentication is `pre-shared` (PSK) based. All devices belong to single tenant.
The Hono communication patterns under test are:
- Publishing Telemetry data with fixed rate.
- Publishing Event data with fixed rate.
- Command and Control with fixed rate via publishing specific telemetry message (command query) that activates command from the backend side.

The payload format is [Ditto](https://eclipse.dev/ditto/protocol-specification.html) with message size less than 1k bytes.

The backend simulator is started first and then the device simulator is started for the period of the test.

The configuration of tenant, devices and credentials is done via a script writing directly to MongoDB.
This is faster than calling the [Device Registry Management REST API]({{% doclink "/api/management/" %}}) but requires knowledge of the database record structure.

Kafka topics are configured with following partition count:

<style>
table th, td, tr {
border: 1px solid;
padding: 5px
}

thead th {
background-color: rgba(134, 134, 134, .166)
}
</style>
| Topic                               | Partition Count |
| :---------------------------------- | :-------------- |
| *hono.telemetry.tload_tests*        | 10              |
| *hono.event.tload_tests*            | 4               |
| *hono.command.tload_tests*          | 3               |
| *hono.command_response.tload_tests* | 2               |
| *hono.notification.registry-device* | 4               |
| *hono.notification.registry-tenant* | 4               |

## Results

Table below illustrates results from two tests. Both of them are executed on a clean system.
The device simulator is running for 15 minutes. Here is description of the terms:
- coapRttSendTelemetry: round-trip time between publishing telemetry and receiving adapter response
- coapRttSendEvent: round-trip time between publishing event and receiving adapter response
- coapRttSendCommandRequest: round-trip time between publishing command query and receiving adapter response with backend command
- coapRttSendCommandResponse:  round-trip time between publishing command response and receiving adapter response
- coapErrorsSendTelemetry: errors during publishing telemetry
- coapErrorsSendEvent: errors during publishing event
- coapErrorsSendCommandRequest: errors during publishing command query

<table>
<thead>
<tr>
<th>Test number</th>
<th>Number of connected devices</th>
<th>Telemetry<br/>req/sec</th>
<th>Events<br/>req/sec</th>
<th>CommandQuery<br/>req/sec</th>
<th>Result</th>
</tr>
</thead>
<tr>
<td>1</td>
<td>2500</td>
<td>300</td>
<td>300</td>
<td>300</td>
<td>

```json
{
  "type" : "device-simulator",
  "numClientSuccessfulConnects" : 2500,
  "numClientDisconnects" : 0,
  "numClientConnectFailureClients" : 0,
  "numClientExceptionWhileOpening" : 0,
  "publishedTelemetryCount" : 127026,
  "publishedEventsCount" : 127026,
  "answeredCommandsCount" : 125602,
  "receivedCommands" : 125784,
  "publishedCommandQueries" : 127026,
  "coapRttSendTelemetry" : {
    "all" : 126824,
    "avg" : "14 ms",
    "95%" : "39 ms",
    "99%" : "74 ms",
    "99.9%" : "519 ms",
    "max" : "965 ms"
  },
  "coapRttSendEvent" : {
    "all" : 126824,
    "avg" : "15 ms",
    "95%" : "39 ms",
    "99%" : "74 ms",
    "99.9%" : "509 ms",
    "max" : "2654 ms"
  },
  "coapRttSendCommandRequest" : {
    "all" : 125784,
    "avg" : "68 ms",
    "95%" : "164 ms",
    "99%" : "279 ms",
    "99.9%" : "854 ms",
    "max" : "9953 ms"
  },
  "coapRttSendCommandResponse" : {
    "all" : 125602,
    "avg" : "18 ms",
    "95%" : "44 ms",
    "99%" : "64 ms",
    "99.9%" : "99 ms",
    "max" : "384 ms"
  },
  "coapErrorsSendTelemetry" : {
    "5.03" : 94,
    "unknownCoapError" : 107
  },
  "coapErrorsSendEvent" : {
    "5.03" : 79,
    "unknownCoapError" : 123
  },
  "coapErrorsSendCommandRequest" : {
    "missingCommand" : 1031,
    "5.03" : 90,
    "unknownCoapError" : 121
  },
  "runtimeInMinutes" : 15
}
```

</td>
</tr>
<tr>
<td>2</td>
<td>2500</td>
<td>600</td>
<td>600</td>
<td>600</td>
<td>

```json
{
  "type" : "device-simulator",
  "numClientSuccessfulConnects" : 2500,
  "numClientDisconnects" : 0,
  "numClientConnectFailureClients" : 0,
  "numClientExceptionWhileOpening" : 0,
  "publishedTelemetryCount" : 508118,
  "publishedEventsCount" : 506539,
  "answeredCommandsCount" : 491054,
  "receivedCommands" : 492646,
  "publishedCommandQueries" : 494335,
  "coapRttSendTelemetry" : {
    "all" : 507157,
    "avg" : "60 ms",
    "95%" : "139 ms",
    "99%" : "564 ms",
    "99.9%" : "2799 ms",
    "max" : "8946 ms"
  },
  "coapRttSendEvent" : {
    "all" : 505797,
    "avg" : "65 ms",
    "95%" : "144 ms",
    "99%" : "544 ms",
    "99.9%" : "2819 ms",
    "max" : "8901 ms"
  },
  "coapRttSendCommandRequest" : {
    "all" : 492645,
    "avg" : "185 ms",
    "95%" : "369 ms",
    "99%" : "1649 ms",
    "99.9%" : "3094 ms",
    "max" : "9318 ms"
  },
  "coapRttSendCommandResponse" : {
    "all" : 491054,
    "avg" : "41 ms",
    "95%" : "79 ms",
    "99%" : "129 ms",
    "99.9%" : "494 ms",
    "max" : "3005 ms"
  },
  "coapErrorsSendTelemetry" : {
    "5.03" : 349,
    "unknownCoapError" : 605
  },
  "coapErrorsSendEvent" : {
    "5.03" : 218,
    "unknownCoapError" : 516
  },
  "coapErrorsSendCommandRequest" : {
    "missingCommand" : 570,
    "5.03" : 719,
    "unknownCoapError" : 392
  },
  "runtimeInMinutes" : 15
}
```
</td>
</tr>
</table>

## Findings

* The Hono deployment configured as described is working stable for the loads less than those in test number 2.
* A bottleneck is the initial filling of the Infinispan cache required by the command flow, where it is involved Hotrod client in Command Router.
* Infinispan cache requires fine-tuning of the Hotrod client connection as the default helm chart values are not production grade.
* Log level should be set to *prod* in order to optimize Hono throughput and service performance.
* Increasing the load, increases the errors of type `5.03` that happen on a cold start (clean system).
* If the tests are executed against a system that was already running, the errors of type `5.03` are less.
* If telemetry and event flow is used only (no commands), the message rate could be much higher.
