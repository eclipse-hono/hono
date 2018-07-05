+++
title = "OpenShift with source-to-image (S2I)"
weight = 476
+++

This guide described how Eclipse Hono™ can be deployed on OpenShift with
EnMasse using the source-to-image (S2I) way. Using this approach it is possible
to customize and refresh the base images where Hono runs in. It also uses
a more complex, multi-project setup and separates EnMasse and Grafana from
the core Hono project.

<!--more-->

{{% warning title="Use for demos only" %}}
While this deployment model is closer to a production-ready deployment
it still is missing some important aspects, so please do use this only as a
base for your setup or as a demo setup. The following topics are currently
not covered by this example deployment:

* Running EnMasse with full authentication enabled
* Integration between EnMasse and Hono authentication
* Configuring and supporting multi-tenancy in both platforms
* Use your own versions of the device registry and authorization service

These are all subjects to current and future developments in this area. This
document will be updated accordingly with the progress. 
{{% /warning %}}

## Prerequisites

In order to work through this example deployment you will need the OpenShift
client tools installed. Please align the version of the client tools with
the version of your OpenShift cluster. This guide was tested with
OpenShift 3.7.2. It should work with older or newer versions as well, but that
is untested.

{{% warning title="Mac OS X 10.13+" %}}
The `curl` binary on Mac OS X before 10.13 suffers from an issue with TLS SNI.
Also see: https://github.com/curl/curl/issues/1533

As the use of SNI is required for Kubernetes/OpenShift when it comes to routing
requests to services, it is not possible to use the provided version of
`curl` on Mac OS X before 10.13.

You can install a working `curl` version using in those Mac OS X released with
the following commands:

    brew install curl --with-openssl

Or upgrade your existing installation using:

    brew reinstall curl --with-openssl

{{% /warning %}}

### Assumptions

This tutorial makes the following assumptions about your environment, if those
assumptions are not true in your specific environment you will need to adapt
the following instructions:

* The admin URL of your OpenShift cluster is: `https://my-cluster:8443`
* The name of your OpenShift user is `developer`
* All scripts and paths are relative to the folder
  `example/src/main/deploy/openshift_s2i`

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

### Persistent volumes

You will need three persistent volumes for this deployment. The default
required volumes are:

| Name        | Size       | Purpose                             |
| ----------- | ----------:| ----------------------------------- |
| hono-pv     | 128 Mi     | Storing device registry information |
| grafana-pv  | 1 Gi       | Grafana configuration database      |
| influxdb-pv | 20 Gi      | Hono interal metrics storage        |

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
    oc create -f admin/storage/<type>/influxdb-pv.yml

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

Those projects must be allowing to perform internal communication.

If projects will be created for you, then you can ignore the
calls to `oc new-project` in the following sections. 

## Clone the Hono repository

In order to have access to some of the requires scripts and resource of this
deployment guide, you will need to clone the Hono repository to your local
machine. You can do this with the following command:

    git clone https://github.com/eclipse/hono.git

Or if you want to check out a specific branch (e.g. `0.6.x`):

    git clone -b 0.6.x https://github.com/eclipse/hono.git

{{% note title="Different branches" %}}
This deployment description is based on the master branch of Hono. However this
branch is also the current development and may be unstable at times.

If you experience any problems, starting with release 0.6.0 of Hono, it is
recommended to switch to the release branch (e.g. `0.6.x`) instead of using
the default master branch. However this documentation is only published from
the master branch, so there may be inconsistencies between the repository
content and the documentation. In this case you can read through `index.md`
file, checked out from the branch you cloned. The file is located in the 
directory `hono-site/content/deployment/openshift_s2i`.
{{% /note %}}

## Setting up EnMasse

Start by creating a new project using:

    oc new-project enmasse --display-name='EnMasse Instance'

Then download and unpack EnMasse:

    curl -LO https://github.com/EnMasseProject/enmasse/releases/download/0.20.0/enmasse-0.20.0.tgz
    tar xzf enmasse-0.20.0.tgz

{{% note title="Newer versions" %}}
Newer versions of EnMasse might work as well, or might require some changes to
the deployment guide. Unless you explicitly want to try out a different version
it is recommended to stick to the version mentioned in this tutorial.
{{% /note %}}

    ./enmasse-0.20.0/deploy.sh -n enmasse

Wait for the admin console to completely start up. You can check this with
the following command:

    oc get deploy/api-server

Verify that the "AVAILABLE" column shows "1":

    NAME         DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
    api-server   1         1         1            1           1m

Next you will need to configure EnMasse to provide the required resources:

    curl -X POST --insecure -T addresses.json -H "content-type: application/json" https://$(oc -n enmasse get route restapi -o jsonpath='{.spec.host}')/apis/enmasse.io/v1alpha1/namespaces/enmasse/addressspaces/default/addresses

## Setting up Hono

Start by creating a new project using:

    oc new-project hono --display-name='Eclipse Hono™'

Create the InfluxDB ConfigMap from the local file:

    oc create configmap influxdb-config --from-file="../influxdb.conf"

Then process and execute the main Hono template:

    oc process -f hono-template.yml | oc create -f -

OpenShift templates allow to use *parameters* which can customize provided
templates. If you want to specify template parameters from the command line
use the following syntax:

    oc process -f hono-template.yml \
      -p "ENMASSE_NAMESPACE=enmasse" \
      -p "GIT_REPOSITORY=https://github.com/your/hono.git" \
      -p "GIT_BRANCH=0.6.x"| oc create -f -

{{% note title="Align branches" %}}
By default the Hono template uses the `master` branch for deploying Hono. As
this branch might be unstable this may not be what you are looking for, but it
also is the default branch of the Hono Git repository where you checked out
the deployment template.

It is recommended that when you execute the guide from an alternate branch
(e.g. `0.6.x`) that you also pass the same branch as `GIT_BRANCH` to the
template.
{{% /note %}}

## Setting up Grafana

Start by creating a new project using:

    oc new-project grafana --display-name='Grafana Dashboard'

Create the config resources:

    oc create configmap grafana-provisioning-datasources --from-file=../../config/grafana/provisioning/datasources
    oc create configmap grafana-provisioning-dashboards --from-file=../../config/grafana/provisioning/dashboards
    oc create configmap grafana-dashboard-defs --from-file=../../config/grafana/dashboard-definitions

Then deploy the Grafana instance using:

    oc process -f grafana-template.yml \
      -p ADMIN_PASSWORD=admin | oc create -f -

OpenShift templates allow to use *parameters* which can customize provided
templates. If you want to specify template parameters from the command line
use the following syntax:

    oc process -f grafana-template.yml \
      -p ADMIN_PASSWORD=admin \
      -p "GIT_REPOSITORY=https://github.com/your/hono.git" \
      -p "GIT_BRANCH=0.6.x"| oc create -f -

## Configuring the installation

There are a few ways to configure the deployment. The following sub-sections
will give you an introduction.

### Configure maximum number of devices per tenant

The default settings for the example Hono device registry limit the number of
devices to 100 per tenant. If this is not enough for your setup you can change
the setting by executing the following command, which will increate the number
to 10.000 devices per tenant:

    oc env -n hono dc/hono-service-device-registry HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT=10000

## Using the installation

All following examples make use of the running Hono instance. They pretty much
follow the other examples. Please note that, compared to the more simple
OpenShift deployment, in this case we need to take care of different project
names when looking up route host names via `oc get`. You can use the command
line argument `-n <project>` to specify the project name without changing the
default selected project.

All examples in the following sub-sections assume that you are located in the
`examples` directory.

### Extract certificates

In order to connect the external consumer to EnMasse, we need to use a
proper SSL certificate. We can extract one from the OpenShift using the
following command:

    oc extract secret/external-certs-messaging --to=target/config/hono-demo-certs-jar -n enmasse

This will create two files `tls.crt` and `tls.key` in the directory
`target/keys`.

### Running consumer

As described in the [Getting Started]({{< relref "getting-started.md" >}})
guide, data produced by devices is usually consumed by downstream applications
which connect directly to the router network service. You can start the client
from the `example` folder as follows:

    mvn spring-boot:run -Drun.arguments=--hono.client.host=$(oc get -n enmasse route messaging --template='{{.spec.host}}'),--hono.client.port=443,--hono.client.trustStorePath=target/config/hono-demo-certs-jar/tls.crt

### Register device

In order to upload telemetry data to Hono, the device needs to be registered
with the system. You can register the device using the *Device Registry* by
running the following command (i.e. for a device with ID `4711`):

    curl -X POST -i -H 'Content-Type: application/json' -d '{"device-id": "4711"}' http://$(oc get -n hono route hono-service-device-registry-http --template='{{.spec.host}}')/registration/DEFAULT_TENANT

### Uploading Telemetry with HTTP

After having the device registered, uploading telemetry is just a simple
HTTP POST command to the *HTTP Adapter*:

    curl -X POST -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' --data-binary '{"temp": 5}' http://$(oc get route hono-adapter-http-vertx --template='{{.spec.host}}')/telemetry
