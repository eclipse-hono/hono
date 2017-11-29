+++
title = "EnMasse"
weight = 475
+++

For more scalable messaging infrastructure, we should use messaging cloud platform like [EnMasse](http://enmasse.io/) instead of a single router/broker instance. The deployment in this case is very similar to the [standard OpenShift deployment]({{< relref "openshift.md" >}}), so check that out for basic information and prerequisites. For the rest of this guide we will assume that you have Minishift properly installed and configured.
<!--more-->

{{% warning %}}
The current deployment is mostly suitable for developing, testing and demo purposes. For full production usage, you'll need to consider the following items:

* Running EnMasse with full authentication enabled
* Integration between EnMasse and Hono authentication
* Configuring and supporting multi-tenancy in both platforms

These are all subjects to current and future developments in this area. This document will be updated accordingly with the progress. 
{{% /warning %}}

## Deploy EnMasse

First thing we need is to run EnMasse messaging platform on OpenShift in a project called `hono`. For that, download the EnMasse release from the [download page](https://github.com/EnMasseProject/enmasse/releases). These instructions were tested using version `0.13.2`. Unzipping the release and executing

~~~sh
./deploy-openshift.sh -n hono -m https://$(minishift ip):8443
~~~

should get it running. For more information on how to run and configure EnMasse, take a look at the [documentation](http://enmasse.io/documentation/).

## Deploy Hono

Now we are ready to deploy Hono. From the `example/target/deploy/openshift` directory, run 

~~~sh
~hono/example/target/deploy/openshift$ chmod +x *
~hono/example/target/deploy/openshift$ ./enmasse_deploy.sh
~~~

This should start all necessary Hono components, configured to connect to the EnMasse. See the [original OpenShift guide]({{< relref "openshift.md" >}}) to see how to check the status Hono components.

The script will try to use Minishift cluster address by default. If you wish to deploy Hono to some other OpenShift cluster, you should specify the address of the cluster as an argument, like

~~~sh
~hono/example/target/deploy/openshift$ ./enmasse_deploy.sh https://192.168.64.3:8443
~~~

## Extract Certificate

In order to connect the external consumer to EnMasse, we need to use a proper SSL certificate. We can extract one from the OpenShift using the following command (from the `example` directory).

~~~sh
~hono/example$ oc extract secret/external-certs-messaging --to=target/config/hono-demo-certs-jar/ -n hono
~~~

This will create two new files with the key and certificate which we will use to connect the Hono consumer.

## Start Consumer

We can start the telemetry consumer in a similar fashion as it's described in the [Getting Started]({{< relref "getting-started.md" >}}) guide. From the `example` directory run

~~~sh
~hono/example$ mvn spring-boot:run -Drun.arguments=--hono.client.host=messaging-hono.$(minishift ip).nip.io,--hono.client.port=443,--hono.client.trustStorePath=target/config/hono-demo-certs-jar/server-cert.pem
~~~

Note that we used the EnMasse certificate obtained in the previous step. One more thing worth noting is that we don't use any username and password to authenticate our client. That's because EnMasse is deployed without authentication services. This will be changed in the future.

## Register Device

Similarly as in other deployments, we can now register the device and send telemetry data. First let's register a device using an OpenShift route for the `device registry` service.

~~~sh
~hono/example$ curl -X POST -i -H 'Content-Type: application/json' -d '{"device-id": "4711"}' http://hono-service-device-registry-http-hono.$(minishift ip).nip.io/registration/DEFAULT_TENANT
~~~

## Send Telemetry

Now we can use HTTP protocol adapter, to post actual telemetry data

~~~sh
~hono/example$ curl -X POST -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' --data-binary '{"temp": 5}' http://hono-adapter-rest-vertx-hono.$(minishift ip).nip.io/telemetry
~~~

## Undeploy

We can use the same `openshift_undeploy.sh` script to remove `hono` application from OpenShift.

~~~sh
~hono/example/target/deploy/openshift$ ./openshift_undeploy.sh
~~~

This will undeploy both EnMasse and Hono components.

