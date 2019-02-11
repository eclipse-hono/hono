# Demo Certificates for Hono

The individual components of Hono communicate with each other using arbitrary TCP based protocol. In order to improve the confidentiality and privacy for the data being transferred these connections can (and should!) be secured using TLS. All components support the configuration of keys and certificates for that purpose.

This module provides some example key stores containing public/private key pairs and certificate chains to be used for configuring TLS for all of Hono's components. Please take a look at the documentation for the individual components for information regarding how to configure the components to use the keys and certificates.

### Provided Keys and Certificates

You can find the example keys, certificates and key stores in the `certs` folder. They have been generated using teh `create_certs.sh` shell script. You can run that script from the command line in order to either *refresh* the example keys (i.e. extend their validity period) or you can adapt the script to your needs and create your own custom keys and certificates to be used with your Hono installation.

The script creates the following keys and certificates:

1. A pair of private/public keys along with a self signed certificate which together represent the *root CA* identity. The key file is `root-key.pem` (not password protected) and the corresponding certificate is `root-cert.pem`.
1. A pair of private/public keys along with a certificate signed with the root CA's key which together represent an *intermediary CA* identity. The key file is `ca-key.pem` (not password protected) and the corresponding certificate is `ca-cert.pem`.
1. A pair of private/public keys along with a certificate signed with the intermediary CA's key which together assert the identity of the *Qpid Dispatch Router* component. The key file is `qdrouter-key.pem` (not password protected) and the corresponding certificate is `qdrouter-cert.pem`.
1. A pair of private/public keys along with a certificate signed with the intermediary CA's key which together assert the identity of the *Auth server* component. The key file is `auth-server-key.pem` (not password protected) and the corresponding certificate is `auth-server-cert.pem`. They are both contained in the PKCS12 key store `authServerKeyStore.p12` using `authkeys` as the password.
1. A pair of private/public keys along with a certificate signed with the intermediary CA's key which together assert the identity of the *Device registry server* component. The key file is `device-registry-key.pem` (not password protected) and the corresponding certificate is `device-registry-cert.pem`. They are both contained in the PKCS12 key store `deviceRegistryKeyStore.p12` using `deviceregistrykeys` as the password.
1. A pair of private/public keys along with a certificate signed with the intermediary CA's key which together assert the identity of the *HTTP adapter* component. The key file is `http-adapter-key.pem` (not password protected) and the corresponding certificate is `http-adapter-cert.pem`. They are both contained in the PKCS12 key store `httpKeyStore.p12` using `httpkeys` as the password.
1. A pair of private/public keys along with a certificate signed with the intermediary CA's key which together assert the identity of the *MQTT adapter* component. The key file is `mqtt-adapter-key.pem` (not password protected) and the corresponding certificate is `mqtt-adapter-cert.pem`. They are both contained in the PKCS12 key store `mqttKeyStore.p12` using `mqttkeys` as the password.
1. A pair of private/public keys along with a certificate signed with the intermediary CA's key which together assert the identity of the *Apache Artemis broker* component. The key file is `artemis-key.pem` (not password protected) and the corresponding certificate is `artemis-cert.pem`. They are both contained in the PKCS12 key store `artemisKeyStore.p12` using `artemiskeys` as the password.
1. A pair of private/public keys along with a certificate signed with the intermediary CA's key which together assert the identity of the *AMQP adapter* component. The key file is `amqp-adapter-key.pem` (not password protected) and the corresponding certificate is `amqp-adapter-cert.pem`. They are both contained in the PKCS12 key store `amqpKeyStore.p12` using `amqpkeys` as the password.
1. A pair of private/public keys along with a certificate signed with the intermediary CA's key which together assert the identity of the *KURA adapter* component. The key file is `kura-adapter-key.pem` (not password protected) and the corresponding certificate is `kura-adapter-cert.pem`. They are both contained in the PKCS12 key store `kuraKeyStore.p12` using `kurakeys` as the password.
1. A pair of private/public keys along with a certificate signed with the intermediary CA's key which together assert the identity of the *COAP adapter* component. The key file is `coap-adapter-key.pem` (not password protected) and the corresponding certificate is `coap-adapter-cert.pem`. They are both contained in the PKCS12 key store `coapKeyStore.p12` using `coapkeys` as the password.
1. A pair of private/public keys along with a certificate signed with the intermediary CA's key which together assert the identity of the *device 4711*. The key file is `device-4711-key.pem` (not password protected) and the corresponding certificate is `device-4711-cert.pem`. 

**Trust Store**

The `trustStore.jks` contains both the self-signed certificate for the root CA (alias `root`) as well as the certificate for the intermediary CA (alias `ca`). These certificates are supposed to be used as the *trust anchor* in clients that want to access one of Hono's components using TLS.

The password for accessing the trust store is `honotrust` by default.

### Creating the Keys and Certificates

The key stores containing the demo keys and certificates can be recreated by means of the `create_certs.sh` script. It relies on `openssl` as well as the standard Java `keytool` to create the keys and certificates.

You can also use the script to create your own certificates for use with Hono. Simply alter the script at the places where you want to use other values as the default, e.g. your own distinguished names for the certificates and/or different key store names.

Then simply run the script from the command line:

    $~/hono/demo-certs> ./create_certs.sh

The script will create the `certs` subfolder (if it already exists it will be removed first) and then create all keys and certificates in that subfolder.
