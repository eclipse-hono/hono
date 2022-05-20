+++
title = "Limiting Resource Usage"
weight = 490
+++

Deploying Eclipse Hono&trade; to a container orchestration platform is easy thanks to the provided Docker images.
This page provides some guidance for configuring the resource consumption of these containers in order to make sure
that they get enough memory and CPU to run properly, but to also make sure that individual containers do not use up
all the resources causing other containers to starve.
<!--more-->

Docker itself provides means to limit a container's consumption of memory and CPU resources by means of command line
options that can be set when starting up a container. Both Kubernetes and OpenShift leverage this mechanism when
defining resource limits of a *pod*. Please refer to the corresponding documentation of Docker, Kubernetes and
OpenShift for details regarding the particular syntax to be used.

## Java's View of the World

Hono's service components are implemented in Java. When the corresponding Docker container for such a service is
started, the only process being run inside the container is therefore a Java virtual machine (JVM). On startup, the
JVM tries to determine the amount of memory and the number of CPU cores that it can use to execute workloads.
By default the JVM queries the operating system for the corresponding parameters and adjusts its runtime parameters
accordingly, e.g. it will by default limit the size of its heap memory to a quarter of the total memory available in
order to leave enough memory for other processes running on the same system.

This is a reasonable approach when running on *bare metal* or a VM where other processes are expected to be running
on the same machine, thus competing for the same computing resources. However, containers are usually configured to
run a single process only so that it makes more sense to dedicate almost all of the available resources to running
that process, leaving the (small) rest for the operating system itself.

As described above, a Docker container can easily be configured with a limit for memory and CPU resources that it may
use during runtime. These limits are set and enforced using Linux *CGroups*.

## Limiting a Component's Memory Consumption

Starting with Java 9, the JVM will correctly determine the total memory and number of CPUs available when running
inside of a container. All of the Docker images provided by Hono run with OpenJDK 11 by default, thus ensuring that
the JVM considers any memory limits configured for the container when configuring its heap during startup. However,
the default algorithm will still only allocate a quarter of the (limited) amount of memory, thus leaving a lot of
memory available to the container unused.

The following JVM options can be used in Java 9 and later in order to change this behavior:

* `-XX:MinRAMPercentage`, `-XX:MaxRAMPercentage` and `-XX:InitialRAMPercentage` can be used to set the (minimum, maximum and
initial) percentage of total memory that may be allocated for the heap. A value of 70-80% should work if no other
processes are running in the same container.

### Kubernetes

In Kubernetes (and OpenShift) the resource limits for a *pod*, and thus the container(s) that are part of the pod,
can be configured in the corresponding *PodSpec*. The following example from the HTTP adapter's Kubernetes *Deployment*
resource descriptor illustrates the mechanism:

~~~yaml
apiVersion: apps/v1beta1
kind: Deployment
metadata:
  name: hono-adapter-http
spec:
  template:
    metadata:
      labels:
        app: hono-adapter-http
        version: "${project.version}"
        group: ${project.groupId}
    spec:
      containers:
      - image: eclipse/hono-adapter-http:${project.version}
        name: eclipse-hono-adapter-http
        resources:
          limits:
            memory: "300Mi"
        ports:
        - containerPort: 8080
          protocol: TCP
        env:
        - name: JDK_JAVA_OPTIONS
          value: "-XX:MinRAMPercentage=80 -XX:MaxRAMPercentage=80"
        volumeMounts:
        - mountPath: /opt/hono/config
          name: conf
          readOnly: true
      volumes:
      - name: conf
        secret:
          secretName: hono-adapter-http-conf
~~~

The `resources` property defines the overall limit of 256 MB of memory that the pod may use. The `JDK_JAVA_OPTIONS`
environment variable is used to configure the JVM to use 80% of the total memory for its heap.

## Limiting the Number of Device Connections

Hono supports limiting the overall number of simultaneously connected devices per tenant. Please refer to the
[connections limit concept]({{< relref "/concepts/resource-limits#connections-limit" >}}) for more information.
The limit needs to be configured at the tenant level using the *resource-limits* configuration property.
Please refer to the [Tenant API]({{< ref "/api/tenant#tenant-information-format" >}}) for configuration details.

## Limiting the Overall Connection Time

Hono supports configuring limits based on the overall amount of time that the devices have already been connected to
protocol adapters for a tenant. Please refer to the
[connection duration limit]({{< relref "/concepts/resource-limits#connection-duration-limit" >}}) for more information.
Before accepting any connection requests from the devices, the protocol adapters verify that the configured connection
duration limit is not exceeded. If the limit has been already reached, then the connection request is rejected. The
limit needs to be configured at the tenant level using the *resource-limits* configuration property. Please refer to
the [Tenant API]({{< ref "/api/tenant#tenant-information-format" >}}) for configuration details.
 
## Limiting the Data Volume

Hono supports limiting the amount of data that devices of a tenant can publish to Hono during a given time interval.
Please refer to the [messages limit concept]({{< relref "/concepts/resource-limits#messages-limit" >}}) for more
information. The limit needs to be configured at the tenant level using the *resource-limits* configuration property.
Please refer to the [Tenant API]({{< ref "/api/tenant#tenant-information-format" >}}) for configuration details.
