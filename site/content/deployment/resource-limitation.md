+++
title = "Limiting Resource Usage"
weight = 490
+++

Deploying Eclipse Hono&trade; to a container orchestration platform is easy thanks to the provided Docker images. This page provides some guidance for configuring the resource consumption of these containers in order to make sure that they get enough memory and CPU to run properly, but to also make sure that individual containers do not use up all the resources causing other containers to starve.
<!--more-->

Docker itself provides means to limit a container's consumption of memory and CPU resources by means of command line options that can be set when starting up a container. Both Kubernetes and OpenShift leverage this mechanism when defining resource limits of a *pod*. Please refer to the corresponding documentation of Docker, Kubernetes and OpenShift for details regarding the particular syntax to be used.

## Java's View of the World

Hono's service components are implemented in Java. When the corresponding Docker container for such a service is started, the only process being run inside the container is therefore a Java virtual machine (JVM). On startup, the JVM tries to determine the amount of memory and the number of CPU cores that it can use to execute workloads. By default the JVM queries the operating system for the corresponding parameters and adjusts its runtime parameters accordingly, e.g. it will by default limit the size of its heap memory to a quarter of the total memory available in order to leave enough memory for other processes running on the same system.

This is a reasonable approach when running on *bare metal* or a VM where other processes are expected to be running on the same machine, thus competing for the same computing resources. However, containers are usually configured to run a single process only so that it makes more sense to dedicate almost all of the available resources to running that process, leaving the (small) rest for the operating system itself.

As described above, a Docker container can easily be configured with a limit for memory and CPU resources that it may use during runtime. These limits are set and enforced using Linux *CGroups*. When a Java VM is run inside of such a Docker container which has been configured with a memory limit, then the result of the JVM's attempt to determine the available resources during startup will not reflect the memory limit imposed on the container. That is because the JVM by default does not consider the CGroup limit but instead queries the operating system for the overall amount of memory available. The same is true for the way that the JVM determines the number of available CPU cores.

## Limiting a Container's Memory Consumption

Recent versions of OpenJDK have introduced the experimental `-XX:+UseCGroupMemoryLimitForHeap` option to make the JVM consider CGroup limits when determining the amount of available memory. Using this option it is possible to explicitly configure a JVM's memory consumption within the boundaries of the container's (limited) resources.

All of the Docker images provided by Hono make use of this JVM option by default, ensuring that the JVM considers any memory limits configured for the container when setting its maximum heap size during startup. However, the default algorithm will still only allocate a quarter of the (limited) amount of memory, thus leaving a lot of memory available to the container unused.

The following JVM options can be used in order to change this behavior:

* `-XX:MaxRAMFraction` can be used to set the fraction of total memory that may be allocated for the heap. The default value is 4 (meaning that up to a quarter of the memory will be allocated), so in order to increase the amount of memory, the value can be set to 2 (using up to 50% of the memory) or 1 (using up to 100% of the memory). Setting the option to 1 is strongly discouraged because it would leave no memory left for the JVM's other memory areas nor any additional processes run by the operating system.
* `-Xmx` can be used to explicitly set the maximum amount of memory used for the heap. As a rule of thumb, setting this value to 60-70% of the container's (limited) amount of memory should usually work. Based on the application's memory usage characteristics, increasing the value to 80 or even 90% might also work.

### Docker Swarm

As stated above, the Docker images provided by Hono already make use of the `-XX:+UseCGroupMemoryLimitForHeap` option by default. Additional options can be passed to the JVM during startup by means of setting the `_JAVA_OPTIONS` environment variable on the container. The following example from the Docker Swarm deployment script illustrates this mechanism:

~~~sh
$ docker service create --name hono-adapter-http-vertx -p 8080:8080 \
  --secret http-adapter.credentials \
  --secret hono-adapter-http-vertx-config.yml \
  --limit-memory 256m \
  --env _JAVA_OPTIONS=-Xmx180m \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-http-vertx-config.yml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  eclipse/hono-adapter-http-vertx:0.6
~~~

The fourth line sets a hard limit of 256 MB of total memory that the container may use. The fifth line then configures the JVM to use up to 180 MB for its heap, which corresponds to about 70% of the 256 MB total memory.

### Kubernetes

In Kubernetes (and OpenShift) the resource limits for a *pod*, and thus the container(s) that are part of the pod, can be configured in the corresponding *PodSpec*. The following example from the HTTP adapter's Kubernetes *Deployment* resource descriptor illustrates the mechanism:

~~~json
apiVersion: apps/v1beta1
kind: Deployment
metadata:
  name: hono-adapter-http-vertx
spec:
  template:
    metadata:
      labels:
        app: hono-adapter-http-vertx
        version: "${project.version}"
        group: ${project.groupId}
    spec:
      containers:
      - image: eclipse/hono-adapter-http-vertx:${project.version}
        name: eclipse-hono-adapter-http-vertx
        resources:
          limits:
            memory: "256Mi"
        ports:
        - containerPort: 8080
          protocol: TCP
        env:
        - name: SPRING_CONFIG_LOCATION
          value: file:///etc/hono/
        - name: SPRING_PROFILES_ACTIVE
          value: dev
        - name: LOGGING_CONFIG
          value: classpath:logback-spring.xml
        - name: _JAVA_OPTIONS
          value: -Xmx180m
        volumeMounts:
        - mountPath: /etc/hono
          name: conf
          readOnly: true
      volumes:
      - name: conf
        secret:
          secretName: hono-adapter-http-vertx-conf
~~~

The `resources` property defines the overall limit of 256 MB of memory that the pod may use. The `_JAVA_OPTIONS` environment variable is again used to configure the JVM to use up to 180 MB for its heap.