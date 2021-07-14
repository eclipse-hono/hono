This folder contains scripts and configuration files for setting up a Hono *sandbox* that runs in a Kubernetes cluster with the following properties:

* Clients can register devices for arbitrary tenants using Device Registry's REST interface (requires registering the device itself as well as corresponding credentials).
* Devices can publish telemetry data and events for the tenant they have been registered with using the deployed protocol adapters.
* Devices can subscribe for receiving commands. 
* An AMQP 1.0 based messaging network is provided as well as a Kafka cluster. The messaging type can be configured per tenant. Northbound clients need to use the corresponding API.
* Consumers can receive telemetry data and events for arbitrary tenants using the Telemetry and Event APIs.
* Consumers and devices are required to authenticate with the Dispatch Router and the adapters respectively.

# Deployment

The scripts need to be executed in the order of the numbers in the file names:

1. Install Kubernetes: `sudo ./00-install-k3s.sh`.
2. Deploy the [cert-manager](https://cert-manager.io/) & request a public certificate from Let's Encrypt: `sudo ./10-deploy-cert-manager.sh $LE_EMAIL` (creates the namespace "hono").
3. Deploy Hono: `sudo ./20-deploy-hono.sh`.

**NB:** Let's Encrypt has quite strict rate limits. Therefore the script `10-deploy-cert-manager.sh` does not use
the productive Let's Encrypt API by default. After the retrieval of a certificate has been successfully tested, 
execute the script again with `production` as the second argument to request a valid certificate from the productive API.

The scripts need to be executed with `sudo` because the Kube config is only readable by root.

# Updating the Let's Encrypt certificate

Cert-Manager automatically renews the public-facing Let's Encrypt certificate.

Once the certificate has been renewed, the Hono installation need to be upgraded in order to pick up the updated certificate:

`sudo helm upgrade eclipse-hono -f hono-values.yml -n hono eclipse-iot/hono`
