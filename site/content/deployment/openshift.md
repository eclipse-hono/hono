+++
title = "OpenShift / OKD"
weight = 475
aliases = [
    "/deployment/openshift_s2i"
]
+++

This guide describes how Eclipse Hono™ can be deployed on OpenShift with
EnMasse, using the source-to-image (S2I) way. Using this approach, it is possible
to customize and refresh the base images where Hono runs in. It also uses
a more complex, multi-project setup and separates EnMasse and Grafana from
the core Hono project.
<!--more-->

{{% warning title="Use for demos only" %}}
While this deployment model is closer to a production-ready deployment
it still is missing some important aspects, so please do use this only as a
base for your setup or as a demo setup. The following topics are currently
not covered by this example deployment:

* Integration between EnMasse and Hono authentication
* Use your own versions of the device registry and authorization service

These are all subjects to current and future developments in this area. This
document will be updated accordingly with the progress. 
{{% /warning %}}

## Prerequisites

In order to work through this example deployment you will need the OpenShift
client tools installed. Please align the version of the client tools with
the version of your OpenShift cluster. This guide was tested with
OpenShift 3.11.0. It might work with older or newer versions as well, but that
is untested.

### Assumptions

This tutorial makes the following assumptions about your environment, if those
assumptions are not true in your specific environment you will need to adapt
the following instructions:

* The admin URL of your OpenShift cluster is: `https://my-cluster:8443`
* The name of your OpenShift user is `developer`
* All scripts and paths are relative to the folder
  `deploy/src/main/deploy/openshift`
* Some parts of this tutorial may need *cluster admin* privileges. When cluster
  admin privileges are required, the tutorial will indicate this by the command
  `oc login -u admin`. It will indicate the end of a section requiring cluster
  admin privileges by the command `oc login -u developer`.
  
  **Note:** Those command may be different on your installation. They only act
  as an example.

### Linux like environment

The deployment guide assumes that you have a Linux like environment with things
like `bash`, `curl`, `git`, … Mac OS X 10.13+ works as well, Windows with
some kind of Unix tooling should also be possible.

### OpenShift Origin client tools

The client tools can be downloaded from the
[OpenShift Origin](https://github.com/openshift/origin/releases) project
repository. Simply download the archive, unpack it and drop it into a directory
where it can be found by the local PATH lookup.

### Minishift

This tutorial is targeted towards running Hono on a production-ready OpenShift
cluster. However it still is possible and useful to run the same setup on
a local test cluster with "minishift".

Minishift is a tool that helps you run OpenShift locally by running a
single-node OpenShift cluster inside a VM. Follow
[this guide](https://docs.openshift.org/latest/minishift/getting-started/index.html)
for installing and having Minishift up and running.

The default resource limits for Minishift however are to small, so please
ensure that you are running Minishift with the following settings:

    minishift start --cpus 4 --memory 16GB --disk-size 40GB

{{% note title="Resource limits" %}}
Once you created your Minishift cluster instance with `minishift start` the
resource arguments (like `--cpus`) are ignored in future calls to
`minishift start` as the virtual machine has already been created. You will
need to destroy the instance using `minishift delete` before it will accept
the new resource limits.
{{% /note %}}

When using minishift you can find your cluster URL by executing the following
command:

    minishift console --url

Some of the operations may require *cluster admin* privileges. For minishift
an admin user can be created by executing the following commands, having a
running minishift instance:

    minishift addons apply admin-user

This will create a user named `admin`, with a password of `admin`, that has
cluster admin privileges.

### Operator Lifecycle Manager

A simple way to install additional operators in your cluster is via the
[Operator Lifecycle Manager](https://github.com/operator-framework/operator-lifecycle-manager) (aka OLM).
It can be enabled in OpenShift 3.11+ by setting `openshift_enable_olm=true`
in the Ansible inventory file.

For Minishift, it can be enabled by execute the following commands as
cluster admin:

    git clone https://github.com/operator-framework/operator-lifecycle-manager
    cd operator-lifecycle-manager
    
    oc login -u admin
    oc new-project operator-lifecycle-manager
    oc create -f deploy/okd/manifests/latest/
    oc login -u developer

See also: https://github.com/operator-framework/operator-lifecycle-manager/blob/master/Documentation/install/install.md

This guide will use OLM in order to install the optional
[Prometheus Operator](https://github.com/coreos/prometheus-operator),
which enables the aggregation of metrics from different Hono components.

### Persistent volumes

You will need two persistent volumes for this deployment. The default
required volumes are:

| Name        | Size       | Purpose                             |
| ----------- | ----------:| ----------------------------------- |
| hono-pv     | 128 Mi     | Storing device registry information |
| grafana-pv  | 1 Gi       | Grafana configuration database      |

In the folder `admin/storage` you will find a set of sub folders containing
YAML files for setting up the persistent volumes (PVs). As there are multiple
storage providers available you need to choose **only one** and must modify it
to match your OpenShift setup (see the following subsections).

It may also be possible that your cluster supports automatic provisioning of
storage, in this case you don't need to create PVs explicitly.

The PVs can be created by executing the following command with a user that
has cluster wide privileges:

    oc create -f admin/storage/<type>/grafana-pv.yml
    oc create -f admin/storage/<type>/hono-pv.yml

#### Minishift

When you are running minishift, then you have automatic provisioning for PVs
available. There is no need to create any PVs manually.

#### Local

In the `admin/storage/local` folder you will find YAML files for setting up
local storage volumes. Those volumes will be kept available when pods are
restarted, but are only available on he local disk of a node. So pods can
only run on this single node and not be migrated throughout a cluster.

If you have a single node cluster anyway, it might still be an easy option.

You will need to change the field `.spec.hostPath.path` to point to an
existing directory which grants all access to "other" (`chmod a+rwx .`).

The storage capacity will only be used for matching PVs with PVCs and is not
being enforced with this storage type.

#### NFS

The folder `admin/storage/nfs` contains a set of YAML files for setting up
NFS based PVs. For this to work you will need a running NFS server and set up
proper exports. It is possible to re-use the NFS server of your OpenShift
installation if you already have one.

You will need to change the following fields:

* `.spec.nfs.server` – Hostname of the NFS server
* `.spec.nfs.path` – The name of the exported path

{{% warning title="Don't use in production" %}}
Applications running of top of NFS may have issues with things like file locking
behavior, …. So it isn't recommended to use NFS without proper testing and
ensuring that it works the way you expect it. If you accept the risk of
corrupted data, it might still be a simple setup for testing a multi-node setup.
{{% /warning %}}

Also see: <https://docs.openshift.org/latest/install_config/persistent_storage/persistent_storage_nfs.html>

### Ability to create new projects

The following guide requires three projects in the OpenShift cluster. It still
is possible to modify the deployment to deploy to a single project, however this
guide focuses on a setup having multiple projects.

If you don't have permissions to create new projects yourself, then you will
need to request three projects. The guide expects the projects names to be:

* `enmasse` – Hosting the EnMasse components.
* `hono` – For running Eclipse Hono.
* `grafana` – For running an instance of Grafana. Primarily
               for showing Hono dashboards.

Those projects must be allowed to perform internal communication.

If projects will be created for you, then you can ignore the
calls to `oc new-project` in the following sections.

### Certificates

Certificates are a difficult topic, as every components brings in its own
concept about how certificates are handled. This deployment guide tries to
align everything with OpenShifts capabilities of managing certificates. That
means that internal communication tries to use cluster generated certificates,
signed by the cluster CA. And externally it tries to re-use the OpenShift
router certificates, which are provided during the installation of OpenShift.
One exception to that are the AMQP and MQTT protocols. As those currently
cannot be re-encrypted.

If you deploy OpenShift without proper certificates, then you will automatically
have self-signed certificates. In this case it is required to disable e.g.
hostname validation later on. This deployment guide assumes that you have
proper certificates set up, and will try to assist if that is not the case.

{{% note title="Let's encrypt" %}}
As [Let's encrypt](https://letsencrypt.org/) now supports wildcard certificates,
having proper certificates may one be a few commands away for you.
{{% /note %}}

In general, `curl` commands require the parameter `--insecure` in order to
work with self-signed certificates.

{{% warning title="Mac OS X 10.13+" %}}
The `curl` binary on Mac OS X before 10.13 suffers from an issue with TLS SNI.
Also see: https://github.com/curl/curl/issues/1533

As the use of SNI is required for Kubernetes/OpenShift, when it comes to routing
requests to services, it is not possible to use the provided version of
`curl` on Mac OS X before 10.13.

You can install a working `curl` version using in those Mac OS X released with
the following commands:

    brew install curl --with-openssl

Or upgrade your existing installation using:

    brew reinstall curl --with-openssl

Or use proper certificates.

{{% /warning %}}

## Clone the Hono repository

In order to have access to some of the requires scripts and resource of this
deployment guide, you will need to clone the Hono repository to your local
machine. You can do this with the following command:

    git clone https://github.com/eclipse/hono.git

Or if you want to check out a specific branch (e.g. `0.9.x`):

    git clone -b 0.9.x https://github.com/eclipse/hono.git

{{% note title="Different branches" %}}
This deployment description is based on the master branch of Hono. However this
branch is also the current development and may be unstable at times.

If you experience any problems, it is
recommended to switch to a release branch (e.g. `0.9.x`) instead of using
the default master branch. However this documentation is only published from
the master branch, so there may be inconsistencies between the repository
content and the documentation. In this case you can read through `index.md`
file, checked out from the branch you cloned. The file is located in the 
directory `hono-site/content/deployment/openshift`.
{{% /note %}}

## Setting up EnMasse

This section describes how to install EnMasse, the messaging layer which Hono is
based on.

Start by downloading and unpacking EnMasse:

    curl -LO https://github.com/EnMasseProject/enmasse/releases/download/0.25.0/enmasse-0.25.0.tgz
    tar xzf enmasse-0.25.0.tgz

{{% note title="Other versions" %}}
Other versions of EnMasse might work as well, but are untested by this deployment
guide. Unless you explicitly want to try out a different version, it is
recommended to use to the version documented in this tutorial.
{{% /note %}}

First switch to a user with *cluster admin* privileges, e.g.:

    oc login -u admin

Then create a new project:

    oc new-project enmasse-infra --display-name='EnMasse'

And perform the deployment:

    oc apply -f enmasse-0.25.0/install/bundles/enmasse-with-standard-authservice

Wait for the admin console to completely start up. You can check this with
the following command:

    oc get deploy/api-server deploy/keycloak

Verify that the "AVAILABLE" column shows "1":

    NAME         DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
    api-server   1         1         1            1           1m
    keycloak     1         1         1            1           1m

Finally switch back to your normal application user:

    oc login -u developer

## Setting up Hono

Start by creating a new project using:

    oc new-project hono --display-name='Eclipse Hono™'

Then create the EnMasse address space to use:

    oc create -f hono-address-space.yml

Before proceeding to the next step, ensure that the address space has been
created and is ready. Executing the following command should contain
`Is Ready: true` in the status section:

    oc describe addressspace/default

You can also quickly check for `isReady` with a JSON path query:

    oc get addressspace/default -o jsonpath='{.status.isReady}'

Then process and execute the main Hono template in order to deploy the
Hono services:

    oc process -f hono-template.yml | oc create -f -

OpenShift templates allow to use *parameters* which can customize provided
templates. If you want to specify template parameters from the command line
use the following syntax:

    oc process -f hono-template.yml \
      -p GIT_REPOSITORY=https://github.com/your/hono.git \
      -p GIT_BRANCH=0.9.x| oc create -f -

{{% note title="Align branches" %}}
By default the Hono template uses the `master` branch for deploying Hono. As
this branch might be unstable this may not be what you are looking for, but it
also is the default branch of the Hono Git repository where you checked out
the deployment template.

It is recommended that when you execute the guide from an alternate branch
(e.g. `0.9.x`) that you also pass the same branch as `GIT_BRANCH` to the
template.
{{% /note %}}

And register the template for creating new Hono tenants. This only registers
the template, but does not create a new tenant yet:

    oc create -f hono-tenant-template.yml

Next you will need to create a tenant, execute the following command to create
the *default tenant*:

    oc process hono-tenant HONO_TENANT_NAME=DEFAULT_TENANT RESOURCE_NAME=defaulttenant CONSUMER_USER_NAME=consumer CONSUMER_USER_PASSWORD="$(echo -n verysecret | base64)"| oc create -f -

{{% note title="Creating tenants" %}}
Creating a new tenant using the template currently only creates the necessary
resources in EnMasse for the tenant. It does not create the tenant in the Hono
device registry.
{{% /note %}}

## Enabling metrics

The default OpenShift deployment of Hono does support the use of Prometheus as
a metrics backend. However it is still required to deploy an instance of
Prometheus and Grafana to your installation in order to actually gather and
visualize the metrics.

### Adding Prometheus support

This section will explain how to set up Prometheus via the OLM and the
Prometheus Operator. This is only an example, it is possible to install
Prometheus in different ways. Or skip the installation of Prometheus, if you
want to use a different metrics backend.

Run the following commands to register the prometheus operator and create a
new instance:

    oc project hono
    oc create -f ../resource-descriptors/prometheus/operator.yml
    oc create -f ../resource-descriptors/prometheus/instance.yml

### Setting up Grafana

Start by creating a new project using:

    oc new-project grafana --display-name='Grafana Dashboard'

Create the config resources:

    oc create configmap grafana-provisioning-dashboards --from-file=../../config/grafana/provisioning/dashboards
    oc create configmap grafana-dashboard-defs --from-file=../../config/grafana/dashboard-definitions
    oc label configmap grafana-provisioning-dashboards app=hono-metrics
    oc label configmap grafana-dashboard-defs app=hono-metrics

Then deploy the Grafana instance using:

    oc process -f grafana-template.yml \
      -p ADMIN_PASSWORD=admin | oc create -f -

OpenShift templates allow to use *parameters* which can customize provided
templates. If you want to specify template parameters from the command line
use the following syntax:

    oc process -f grafana-template.yml \
      -p ADMIN_PASSWORD=admin \
      -p HONO_NAMESPACE=hono \
      -p GIT_REPOSITORY=https://github.com/your/hono.git \
      -p GIT_BRANCH=0.9.x| oc create -f -

## Configuring the installation

The default installation of Hono can be tweaked in a few ways. The following
sub-sections describe a few aspects that can be modified.

### Configure maximum number of devices per tenant

The default settings for the example Hono device registry limit the number of
devices to 100 per tenant. If this is not enough for your setup you can change
the setting by executing the following command, which will increase the number
to 10.000 devices per tenant:

    oc set env -n hono dc/hono-service-device-registry HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT=10000

### Adding Jaeger support

By default Hono has the capability to work with "open tracing", and it also
provides a build profile for enabling the "Jaeger" implementation of
"open tracing".

There are a few manual steps required to modify the default Hono deployment
for OpenShift in order to enable this profile.

#### Enable the build profile

The Hono profile needs to be enabled, to include the Jaeger components in the
S2I builds.

Modify the Hono templates to add the `jaeger` profile in the builds. e.g.:

~~~diff
           name: fabric8-s2i-java-custom:2.3
         env:
         - name: MAVEN_ARGS_APPEND
-          value: -B -pl org.eclipse.hono:hono-adapter-mqtt-vertx --also-make -Pnetty-tcnative
+          value: -B -pl org.eclipse.hono:hono-adapter-mqtt-vertx --also-make -Pnetty-tcnative -Pjaeger
         - name: ARTIFACT_DIR
           value: adapters/mqtt-vertx/target
         - name: ARTIFACT_COPY_ARGS
~~~

Be sure to trigger new builds if you already built the container images before.

#### Add the Jaeger agent sidecar

In order to add capture output from the Jaeger client and forward it to the
main Jaeger application, a Jaeger agent is required. This will be deployed
alongside each Hono service, as a dedicated container, but in the same pod
(aka sidecar).

This requires a new image stream:

~~~yml
kind: ImageStream
apiVersion: v1
metadata:
  name: jaeger-agent
spec:
  lookupPolicy:
    local: false
  tags:
  - name: "latest"
    from:
      kind: DockerImage
      name: docker.io/jaegertracing/jaeger-agent:latest
    importPolicy:
      scheduled: true
    referencePolicy:
      type: Source
~~~

Then you need to modify the deployment configuration for each Hono service that
should use the Jaeger agent:

~~~diff
 - kind: DeploymentConfig
   apiVersion: v1
   metadata:
     name: hono-adapter-mqtt-vertx
     labels:
       app: hono-adapter
       deploymentconfig: hono-adapter-mqtt-vertx
   spec:
     replicas: 1
     selector:
       app: hono-adapter
       deploymentconfig: hono-adapter-mqtt-vertx
     strategy:
       type: Rolling
       rollingParams:
         timeoutSeconds: 3600
     triggers:
       - type: ConfigChange
       - type: ImageChange
         imageChangeParams:
           automatic: true
           containerNames:
             - eclipsehono-hono-adapter-mqtt-vertx
           from:
             kind: ImageStreamTag
             name: hono-adapter-mqtt-vertx:latest
+      - type: ImageChange
+        imageChangeParams:
+          automatic: true
+          containerNames:
+            - jaeger-agent
+          from:
+            kind: ImageStreamTag
+            name: jaeger-agent:latest
     template:
       metadata:
         labels:
           app: hono-adapter
           deploymentconfig: hono-adapter-mqtt-vertx
       spec:
         containers:
         - name: eclipsehono-hono-adapter-mqtt-vertx
           image: hono-adapter-mqtt-vertx
           imagePullPolicy: Always
           env:
           - name: SPRING_CONFIG_LOCATION
             value: file:///etc/config/
           - name: SPRING_PROFILES_ACTIVE
             value: 
           - name: LOGGING_CONFIG
             value: file:///etc/config/logback-spring.xml
           - name: KUBERNETES_NAMESPACE
             valueFrom:
               fieldRef:
                 fieldPath: metadata.namespace
           - name: HONO_MESSAGING_HOST
             valueFrom:
               configMapKeyRef:
                 name: hono-configuration
                 key: downstream.host
           - name: HONO_MESSAGING_PORT
             valueFrom:
               configMapKeyRef:
                 name: hono-configuration
                 key: downstream.port
           - name: HONO_COMMAND_HOST
             valueFrom:
               configMapKeyRef:
                 name: hono-configuration
                 key: downstream.host
           - name: HONO_COMMAND_PORT
             valueFrom:
               configMapKeyRef:
                 name: hono-configuration
                 key: downstream.port
           - name: HONO_REGISTRATION_HOST
             value: hono-service-device-registry.$(KUBERNETES_NAMESPACE).svc
           - name: HONO_CREDENTIALS_HOST
             value: hono-service-device-registry.$(KUBERNETES_NAMESPACE).svc
           - name: HONO_TENANT_HOST
             value: hono-service-device-registry.$(KUBERNETES_NAMESPACE).svc
           - name: MANAGEMENT_METRICS_EXPORT_GRAPHITE_HOST
             value: influxdb.$(KUBERNETES_NAMESPACE).svc
           - name: AB_JOLOKIA_USER
             value: jolokia
           - name: AB_JOLOKIA_PASSWORD_RANDOM
             value: "false"
           - name: AB_JOLOKIA_PASSWORD
             valueFrom:
               secretKeyRef:
                 name: hono-secrets
                 key: jolokia.password
           readinessProbe:
             httpGet:
               path: /readiness
               port: 8088
               scheme: HTTP
             initialDelaySeconds: 10
           livenessProbe:
             httpGet:
               path: /liveness
               port: 8088
               scheme: HTTP
             initialDelaySeconds: 180
           resources:
             limits:
               memory: 512Mi
           ports:
           - containerPort: 8778 
             name: jolokia
           - containerPort: 8088
             name: radan-http
             protocol: TCP
           - containerPort: 8883
             name: secure-mqtt
             protocol: TCP
           - containerPort: 1883
             name: mqtt
             protocol: TCP
           securityContext:
             privileged: false
           volumeMounts:
           - mountPath: /etc/config
             name: conf
           - mountPath: /etc/secrets
             name: secrets
             readOnly: true
           - mountPath: /etc/tls
             name: tls
             readOnly: true
+        - image: jaeger-agent
+          name: jaeger-agent
+          ports:
+          - containerPort: 5775
+            protocol: UDP
+          - containerPort: 5778
+          - containerPort: 6831
+            protocol: UDP
+          - containerPort: 6832
+            protocol: UDP
+          command:
+            - "/go/bin/agent-linux"
+            - "--collector.host-port=jaeger-collector.jaeger.svc:14267"
+          env:
+            - name: JAEGER_SERVICE_NAME
+              value: hono-adapter-mqtt
         volumes:
         - name: conf
           configMap:
             name: hono-adapter-mqtt-vertx-config
         - name: secrets
           secret:
             secretName: hono-mqtt-secrets
         - name: tls
           secret:
             secretName: hono-adapter-mqtt-vertx-tls
~~~

The important parts are only the modifications, which add a new image stream
trigger, and also add the additional agent container to the deployment. This
example assumes that the Jaeger collector will be available at the hostname
`jaeger-collector.jaeger.svc`. This will be true if you follow the next
section on deploying a development-only Jaeger cluster. Should you deploy
Jaeger differently, then this hostname and/or port may be different.

#### Deploy Jaeger

Setting up a full Jaeger cluster is a complicated task. However there is a good
tutorial at the Jaeger repository at: https://github.com/jaegertracing/jaeger-openshift

A simple deployment, for testing purposes only, can be performed by running
the development setup template of Jaeger for OpenShift:

~~~
oc new-project jaeger
oc process -f https://raw.githubusercontent.com/jaegertracing/jaeger-openshift/master/all-in-one/jaeger-all-in-one-template.yml | oc create -f -
~~~

Please be aware of the official note in the [documentation](https://github.com/jaegertracing/jaeger-openshift#development-setup):

> This template uses an in-memory storage with a limited functionality for local testing and development. Do not use this template in production environments.

## Using the installation

All following examples make use of the running Hono instance. They pretty much
follow the other examples. Please note that, compared to the more simple
OpenShift deployment, in this case we need to take care of different project
names when looking up route host names via `oc get`. You can use the command
line argument `-n <project>` to specify the project name without changing the
default selected project.

All examples in the following sub-sections assume that you are located in the
`cli` directory.

### Extract certificates

In order to connect the external consumer to Enmasse, we need to extract the
certificate which messaging enpoint of EnMasse uses. This allows to validate
the connection to the endpoint and encrypt the communication using TLS.

The following command extracts the certificate of the endpoint (not the key):

    oc -n hono get addressspace default -o jsonpath={.status.endpointStatuses[?(@.name==\'messaging\')].cert} | base64 -d > target/config/hono-demo-certs-jar/tls.crt

This will retrieve the certificate, decode the base64 encoded string and
store it in the file `target/config/hono-demo-certs-jar/tls.crt`. Although
the file is a "demo cert" as the path might indicate, it still is stored in
the same location in order to align the with the other example commands of the
Hono documentation.

### Running consumer

As described in the [Getting Started]({{< relref "getting-started.md" >}})
guide, data produced by devices is usually consumed by downstream applications
which connect directly to the router network service. You can start the client
from the `cli` folder as follows:

    mvn spring-boot:run -Drun.arguments=--hono.client.host=$(oc -n hono get addressspace default -o jsonpath={.status.endpointStatuses[?(@.name==\'messaging\')].externalHost}),--hono.client.port=443,--hono.client.username=consumer,--hono.client.password=verysecret,--hono.client.trustStorePath=target/config/hono-demo-certs-jar/tls.crt

### Register device

In order to upload telemetry data to Hono, the device needs to be registered
with the system. You can register the device using the *Device Registry* by
running the following command (i.e. for a device with ID `4711`):

    curl -X POST -i -H 'Content-Type: application/json' -d '{"device-id": "4711"}' https://$(oc get -n hono route hono-service-device-registry-https --template='{{.spec.host}}')/registration/DEFAULT_TENANT

### Uploading Telemetry with HTTP

After having the device registered, uploading telemetry is just a simple
HTTP POST command to the *HTTP Adapter*:

    curl -X POST -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' --data-binary '{"temp": 5}' https://$(oc -n hono get route hono-adapter-http-vertx-sec --template='{{.spec.host}}')/telemetry
