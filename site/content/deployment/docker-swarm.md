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

It is very easy to deploy the containers comprising a Hono instance to an existing Docker Swarm based on a script. The remainder of this guide will use the example deploy script created in the `example/target/deploy/docker` folder during the build process for that purpose. Once the build has finished, the process of deploying Hono to a cloud based, multi-node cluster is similar to the way described in the [Getting started guide]({{< relref "getting-started.md" >}}):

~~~sh
~/hono/example$ export DOCKER_HOST=tcp://my-swarm.my-domain.com:2375
~/hono/example$ target/deploy/docker/swarm_deploy.sh
~~~

Make sure to replace `my-swarm.my-domain.com:2375` with the host name or IP address and port of one of the Docker Swarm managers of the cluster to deploy to. When deploying to a swarm running on cloud infrastructure, direct access to the swarm manager(s) might not be possible, e.g. because the swarm runs on a private network behind a firewall. In such cases an `ssh` tunnel can usually be established with one of the swarm managers, providing a local TCP socket which can be used to transparently communicate with the Docker daemon running on the (remote) swarm manager. The Docker documentation contains details on how to set up and use such an `ssh` tunnel to [deploy an application to Docker for AWS](https://docs.docker.com/docker-for-aws/deploy/).

## Custom Configuration

The Hono *Auth Server* Docker image (`eclipse/hono-service-auth`) contains a default `permissions.json` file from the `services/auth` module which defines the identities and authorizations of clients connecting to any of the Hono service endpoints. During the build process this file is packaged into a JAR file and copied to the Docker image. The *Auth Server* component loads the file from its default resource location (`classpath:/permissions.json` when starting up.

In the remainder of this guide we will employ [Docker Swarm's *Secret*](https://docs.docker.com/engine/swarm/secrets/) mechanism for configuring an *Auth Server* Docker container to load a custom permissions file from a file system resource instead of the default one.

{{% warning %}}
You will need at least Docker version 17.03.1 (corresponding to 1.13.1 in Docker's old versioning scheme) in order to run the example in this guide.
{{% /warning %}}

### Docker Secrets

The `example/target/deploy/docker/swarm_deploy.sh` script defines infrastructure and services that comprise a full Hono *stack*.

Among others, the file contains the following service definition for the Auth Server:

~~~sh
...

echo Deploying Authentication Server ...
docker secret create -l $NS auth-server-key.pem $CERTS/auth-server-key.pem
docker secret create -l $NS auth-server-cert.pem $CERTS/auth-server-cert.pem
docker secret create -l $NS hono-service-auth-config.yml $CONFIG/hono-service-auth-config.yml
docker service create -l $NS --detach --name hono-service-auth --network $NS \
  --secret auth-server-key.pem \
  --secret auth-server-cert.pem \
  --secret trusted-certs.pem \
  --secret hono-service-auth-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-auth-config.yml \
  --env SPRING_PROFILES_ACTIVE=authentication-impl,dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  eclipse/hono-service-auth:${project.version}
echo ... done

...
~~~

In order to provide access to resources that are not part of the Docker image itself, a local file system path can be mounted as a [Docker *volume*](https://docs.docker.com/engine/reference/commandline/run/#mount-tmpfs---tmpfs) when starting a container. Using this approach, resources from the local host's file system can be accessed from within the running container via the mapped file system path. However, the problem with this approach is that it does not work well with clusters of Docker Engines (i.e. a Swarm) because of the dependency on the Docker Engine's local host file system.

Since Docker 17.03.1, the new and preferred way to provide containers access to private resources is by means of defining [*Secrets*](https://docs.docker.com/engine/swarm/secrets/) at the Swarm level. A container running in the Swarm can be configured to get access to any subset of the Swarm's secrets, regardless of the particular node it is running on. The Swarm manager provides access to the secrets to containers by means of a `tmpfs` file system mounted at `/run/secrets` where the secrets are represented as normal files that the container can read from. Please refer to the [Secrets documentation](https://docs.docker.com/engine/swarm/secrets/) for details.

The `swarm_deploy.sh` script already creates and uses Docker secrets for providing configuration properties and keys and certificates to the arbitrary services. We will use the same mechanism to configure a custom permissions file to be used by the Auth Server component.

### Create custom Permissions

1. Create a copy of the default `services/auth/src/main/resources/permissions.json` file:

       ~~~sh
       ~/tmp$ cp ~/hono/services/auth/src/main/resources/permissions.json ./my-permissions.json
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

### Modifying the Deploy Script

1. Open the default `~/hono/example/src/main/deploy/docker/swarm_deploy.sh` file in a text editor and add a `docker secret create` command for `my-permissions.json` to the already existing ones for the Auth Service. Make sure to specify the correct path to the `my-permissions.json` file you have created.

       ~~~sh
       ...
       echo Deploying Authentication Server ...
       docker secret create -l $NS auth-server-key.pem $CERTS/auth-server-key.pem
       docker secret create -l $NS auth-server-cert.pem $CERTS/auth-server-cert.pem
       docker secret create -l $NS hono-service-auth-config.yml $CONFIG/hono-service-auth-config.yml
       docker secret create -l $NS my-permissions.json ~/tmp/my-permissions.json
       ...
       ~~~
        
1. Configure the *hono-service-auth* service to get access to the secret and configure it to use the custom permissions file:

       ~~~sh
       ...
       docker service create -l $NS --detach --name hono-service-auth --network $NS \
         --secret auth-server-key.pem \
         --secret auth-server-cert.pem \
         --secret trusted-certs.pem \
         --secret hono-service-auth-config.yml \
         --secret my-permissions.json \
         --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-auth-config.yml \
         --env SPRING_PROFILES_ACTIVE=authentication-impl,dev \
         --env LOGGING_CONFIG=classpath:logback-spring.xml \
         eclipse/hono-service-auth:${project.version}
       echo ... done
       ...
       ~~~

    The newly added `docker secret create` command defines the secret that the service should get access to during runtime.

1. Save the file.

1. Open the `~/hono/example/src/main/config/hono-service-auth-config.yml` file in a text editor and add the *hono.auth.svc.permissionsPath* property pointing to the custom property file:

       ~~~json
       hono:
         app:
           maxInstances: 1
         auth:
           amqp:
             bindAddress: 0.0.0.0
             keyPath: /run/secrets/auth-server-key.pem
             certPath: /run/secrets/auth-server-cert.pem
             trustStorePath: /run/secrets/trusted-certs.pem
             insecurePortEnabled: true
             insecurePortBindAddress: 0.0.0.0
           svc:
             permissionsPath: file:///run/secrets/my-permissions.json
       ~~~

    Note that the newly added *hono.auth.svc.permissionsPath* property points to `file:///run/secrets/my-permissions.json` which is the file system resource that the secret will be mounted to by the Swarm manager.

1. Save the file.

1. Build the deploy script.

       ~~~sh
       ~/hono/example$ mvn install
       ~~~


### Deploying the Stack

Now that the deploy script has been updated, it is time to deploy and start up the stack using the custom permissions.
Make sure to replace `my-swarm.my-domain.com` with the host name or IP address of one of the Docker Swarm managers you want to deploy the stack to.

~~~sh
~/hono/example$ export DOCKER_HOST=tcp://my-swarm.my-domain.com:2375
~/hono/example$ target/deploy/docker/swarm_deploy.sh
~~~

The log output of the *Auth Server* should contain a line similar to this:

~~~sh
14:02:15.790 [vert.x-eventloop-thread-1] INFO  o.e.h.a.i.FileBasedAuthenticationService - loading permissions from resource file:/run/secrets/my-permissions.json
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