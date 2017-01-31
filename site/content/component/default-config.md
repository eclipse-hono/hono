+++
title = "Default Configuration"
weight = 305
+++

The Default Configuration component provides default/example configuration files as well as keys and certificates to be used by the other Hono components.
<!--more-->

The Default Configuration is provided as the `eclipsehono/hono-default-config` Docker image. The image defines and exposes a Docker *volume* at `/etc/hono`. The volume can be *mounted* from other Docker containers during startup by means of Docker `run`'s `--volumes-from` command line switch.

## Contents

The configuration files, keys & certificates contained in the volume are:

| File                                     | Description                                                      |
| :--------------------------------------- | :--------------------------------------------------------------- |
| `/etc/hono/permissions.json`          | Example access control lists for Hono's endpoint resources.      |
| `/etc/hono/qdrouter/qdrouterd.json`  | The Dispatch Router configuration file. |
| `/etc/hono/qdrouter/qdrouter-sasl.conf` | The configuration file for the [Cyrus SASL Library](http://www.cyrusimap.org/sasl/getting_started.html) used by Dispatch Router for authenticating clients. This configuration file can be adapted to e.g. configure LDAP or a database for verifying credentials.
| `/etc/hono/qdrouter/qdrouterd.sasldb` | The Berkley DB file used by Cyrus SASL that contains the example users which are supported by the Dispatch Router.
| `/etc/hono/certs/qdrouter-key.pem`    | An example private key to use for enabling TLS on the Dispatch Router. |
| `/etc/hono/certs/qdrouter-cert.pem`   | A certificate containing the public key corresponding to the example private key. |
| `/etc/hono/certs/trusted-certs.pem`   | A list of the CA certificates used to sign the example keys and certificates. |
| `/etc/hono/certs/honoKeyStore.p12`    | A key store containing an example private key and matching certificate for enabling TLS on the Hono server. |
| `/etc/hono/certs/restKeyStore.p12`    | A key store containing an example private key and matching certificate for enabling TLS on the REST adapter. |
| `/etc/hono/certs/mqttKeyStore.p12`    | A key store containing an example private key and matching certificate for enabling TLS on the MQTT adapter. |

## Usage

The volume exposed by the Default Configuration can be accessed from other containers after it has been *mounted*.
To do so the Default Configuration image needs to be run once as a Docker container.

    $ docker run -d --name hono-config eclipsehono/hono-default-config:latest

From now on, the Docker daemon provides access to the volume's content to other containers by referring to the volume container name (*hono-config* in this case).

{{% note %}}
The *hono-default-config* container needs to be run only once. The Docker daemon takes control over the volume exposed by the container and provides access to its contents to other containers.
{{% /note %}}

As an example, the following command runs the [Dispatch Router]({{< ref "dispatch-router.md" >}}) and mounts the configuration volume into the container's local file system (`--volumes-from=hono-config`) from where the Dispatch Router configuration file is then read (`-c /etc/hono/qdrouter/qdrouterd.json`):

~~~sh
$ docker run -d --name qdrouter -p 15671:5671 -p 15672:5672 -p 15673:5673 \
> --volumes-from=hono-config gordons/qpid-dispatch:0.7.0 /usr/sbin/qdrouterd -c /etc/hono/qdrouter/qdrouterd.json
~~~
