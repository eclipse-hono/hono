+++
title = "Docker Swarm"
weight = 480
+++

Eclipse Hono&trade; components are distributed by means of Docker images which can be deployed to arbitrary environments where Docker is available. This section provides step-by-step instructions for deploying Hono to a cluster of Docker Engine nodes running in *Swarm mode*.
<!--more-->

The focus of this guide is on describing how custom configuration files can be used with Hono when deploying to a Docker Swarm.

## Prerequisites

The only requirement for this guide is a working cluster of Docker Engine nodes running in Swarm mode. While this guide also works with a single Docker Engine node in Swarm mode (see [Getting started]({{< relref "getting-started.md" >}})), the main deployment target will probably be a multi-node cluster running on some cloud infrastructure, e.g. a [Docker for AWS](https://docs.docker.com/docker-for-aws/) installation running on Amazon Web Services infrastructure. Please refer to the Docker for AWS documentation for instructions on how to set up a Swarm on AWS.

## Deployment

It is very easy to deploy the containers comprising a Hono instance to an existing Docker Swarm based on a [Docker *compose* file](https://docs.docker.com/compose/compose-file/). The remainder of this guide will use the example compose file created in the `example/target/hono` folder during the build process for that purpose. Once the build has finished, the process of deploying Hono to a cloud based, multi-node cluster is similar to the way described in the [Getting started guide]({{< relref "getting-started.md" >}}):

~~~sh
$ export DOCKER_SWARM_MANAGER=my-swarm.my-domain.com:2375
$ docker -h $DOCKER_SWARM_MANAGER stack deploy -c docker-compose.yml hono
~~~

Make sure to replace `my-swarm.my-domain.com:2375` with the host name or IP address and port of one of the Docker Swarm managers of the cluster to deploy to. When deploying to a swarm running on cloud infrastructure, direct access to the swarm manager(s) might not be possible, e.g. because the swarm runs on a private network behind a firewall. In such cases an `ssh` tunnel can usually be established with one of the swarm managers, providing a local TCP socket which can be used to transparently communicate with the Docker daemon running on the (remote) swarm manager. The Docker documentation contains details on how to set up and use such an `ssh` tunnel to [deploy an application to Docker for AWS](https://docs.docker.com/docker-for-aws/deploy/).

## Custom Configuration

The Hono *Auth Server* Docker image (`eclipsehono/hono-service-auth`) contains a default `permissions.json` file from the `services/auth` module which defines the identities and authorizations of clients connecting to any of the Hono service endpoints. During the build process this file is packaged into a JAR file and copied to the Docker image. The *Auth Server* component loads the file from its default resource location (`classpath:/permissions.json` when starting up.

In the remainder of this guide we will employ [Docker Swarm's *Secret*](https://docs.docker.com/engine/swarm/secrets/) mechanism for configuring an *Auth Server* Docker container to load a custom `permissions.json` file from a file system resource instead of the default one.

{{% warning %}}
You will need at least Docker version 17.03.1 (corresponding to 1.13.1 in Docker's old versioning scheme) in order to run the example in this guide.
{{% /warning %}}

### Docker Secrets

The `example/target/hono/docker-compose.yml` file defines infrastructure and services that comprise a full Hono *stack*.
In particular, the stack consists of an Auth Server, a Dispatch Router, a Hono server, a REST adapter and an MQTT adapter instance.

The file contains the following service definition for the Auth Server:

~~~json
...

services:

  auth-server:
    image: eclipsehono/hono-service-auth:0.5-M6-SNAPSHOT
    networks:
      hono-net:
        aliases:
          - auth-server.hono
    environment:
      - HONO_AUTH_BIND_ADDRESS=0.0.0.0
      - HONO_AUTH_KEY_PATH=/etc/hono/certs/auth-server-key.pem
      - HONO_AUTH_CERT_PATH=/etc/hono/certs/auth-server-cert.pem
      - HONO_AUTH_MAX_INSTANCES=1
      - LOGGING_CONFIG=classpath:logback-spring.xml
      - SPRING_PROFILES_ACTIVE=authentication-impl,dev

...
~~~

In order to provide access to resources that are not part of the Docker image itself, a local file system path can be mounted as a [Docker *volume*](https://docs.docker.com/engine/reference/commandline/run/#mount-tmpfs---tmpfs) when starting a container. Using this approach, resources from the local host's file system can be accessed from within the running container via the mapped file system path. However, the problem with this approach is that it does not work well with clusters of Docker Engines (i.e. a Swarm) because of the dependency on the Docker Engine's local host file system.

Since Docker 17.03.1, the new and preferred way to provide containers access to private resources is by means of defining [*Secrets*](https://docs.docker.com/engine/swarm/secrets/) at the Swarm level. A container running in the Swarm can be configured to get access to any subset of the Swarm's secrets, regardless of the particular node it is running on. The Swarm manager provides access to the secrets to containers by means of a `tmpfs` file system mounted at `/run/secrets` where the secrets are represented as normal files that the container can read from. Please refer to the [Secrets documentation](https://docs.docker.com/engine/swarm/secrets/) for details.

### Create custom Permissions

1. Create a copy of the default `services/auth/src/main/resources/permissions.json` file:

       ~~~sh
       ~/tmp$ cp $HONO/services/auth/src/main/resources/permissions.json ./my-permissions.json
       ~~~

1. Open the `my-permissions.json` file and authorize a `new-user` with password `mypassword` to act as a protocol adapter, i.e. write telemetry data and events for all tenants, and manage device registration information. In order to do so, simply add a `new-user@HONO` element at the end of the file as shown below:

       ~~~json
        {
          ...
          "users": {
            "rest-adapter@HONO": {
              "mechanism": "PLAIN",
              "password": "rest-secret",
              "authorities": [ "hono-component", "protocol-adapter", "device-manager" ]
            },
            "mqtt-adapter@HONO": {
              "mechanism": "PLAIN",
              "password": "mqtt-secret",
              "authorities": [ "hono-component", "protocol-adapter" ]
            },
            "hono-client@HONO": {
              "mechanism": "PLAIN",
              "password": "secret",
              "authorities": [ "protocol-adapter", "device-manager" ]
            },
            "new-user@HONO": {
              "mechanism": "PLAIN",
              "password": "mypassword",
              "authorities": [ "protocol-adapter", "device-manager" ]
            }
          }
        }
       ~~~

1. Save the file.

### Modifying the Compose File

1. Create a copy of the default `/example/target/hono/docker-compose.yml` file.

       ~~~sh
       ~/tmp$ cp $HONO/example/target/hono/docker-compose.yml ./my-docker-compose.yml
       ~~~

1. Define the custom permissions file as a secret by means of adding a `secrets` section at the beginning of the compose file:

       ~~~json
        version: '3.1'
        
        networks:
          hono-net:
            driver: overlay
        
        secrets:
          custom-permissions:
            file: my-permissions.json
        
        ...
       ~~~
        
1. Configure the *auth-server* service to get access to the secret:

       ~~~json
        ...
        services:
          ...
          auth-server:
            image: eclipsehono/hono-service-auth:0.5-M6-SNAPSHOT
            networks:
              hono-net:
                aliases:
                  - auth-server.hono
            secrets:
              - source: custom-permissions
                mode: 0644
            environment:
              - HONO_AUTH_BIND_ADDRESS=0.0.0.0
              - HONO_AUTH_KEY_PATH=/etc/hono/certs/auth-server-key.pem
              - HONO_AUTH_CERT_PATH=/etc/hono/certs/auth-server-cert.pem
              - HONO_AUTH_MAX_INSTANCES=1
              - HONO_AUTH_PERMISSIONS_PATH=file:/run/secrets/custom-permissions
              - LOGGING_CONFIG=classpath:logback-spring.xml
              - SPRING_PROFILES_ACTIVE=authentication-impl,dev
       ~~~
        
    The newly added `secrets` section defines the secret that the service should get access to during runtime. In this case it is also necessary to adjust the file permissions of the secret file so that the *Auth Server* process can actually read from it. This is because the *Auth Server* runs under user `hono` but secrets by default are created with file permissions `0600` which restricts access to the `root` user only.

    Note that the newly added `HONO_AUTH_PERMISSIONS_PATH` variable points to `/run/secrets/custom-permissions` which is the file system path that the secret has been mounted to by the Swarm manager.

1. Save the file.

### Deploying the Stack

Now that the compose file has been updated, it is time to deploy and start up the stack using the custom permissions.
Make sure to replace `my-swarm.my-domain.com` with the host name or IP address of one of the Docker Swarm managers you want to deploy the stack to.

~~~sh
~/tmp$ docker -h my-swarm.my-domain.com:2375 stack deploy -c my-docker-compose.yml hono
~~~

The log output of the *Auth Server* should contain a line similar to this:

~~~sh
14:02:15.790 [vert.x-eventloop-thread-1] INFO  o.e.h.a.i.FileBasedAuthenticationService - loading permissions from resource file:/run/secrets/custom-permissions
~~~

Once the stack is up and running, start up a consumer as described by the [Getting started Guide]({{< relref "getting-started.md#starting-a-consumer" >}}). You should then be able to connect to the Hono server using the example sender from the `example` module, specifying `new-user@HONO` as the user name.

~~~sh
~/hono/example$ mvn spring-boot:run -Drun.profiles=sender,ssl -Drun.arguments=--hono.client.username=new-user@HONO,--hono.client.password=mypassword,--hono.client.hostname=my-swarm.my-domain.com
~~~

Again, make sure to replace `my.swarm.my-domain.com` with the host name or IP address of one of the Swarm nodes.

Once the sender is up and running you can enter some message(s) to be uploaded as telemetry data which should then be logged to the console where the consumer is running.

## Conclusion

Docker Swarm's *Secrets* mechanism can be used to provide containers running in a Swarm with access to confidential resources, regardless of the node the container is running on.

The example given of using a custom permissions file can be easily adapted to other use cases as well, e.g. for configuring custom keys and certificates or for using a custom configuration file for the Dispatch Router.

## Further Reading

Alex Ellis has blogged about [Docker Secrets in Action](http://blog.alexellis.io/swarm-secrets-in-action/) which provides an excellent introduction to Docker Secrets and how they can be used.