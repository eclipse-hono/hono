![License](https://img.shields.io/github/license/eclipse/hono)
![Continuous Integration Build](https://img.shields.io/github/actions/workflow/status/eclipse-hono/hono/ci.yml?branch=master)
![Docker Pulls](https://img.shields.io/docker/pulls/eclipse/hono-service-auth)
![Latest Release](https://img.shields.io/docker/v/eclipse/hono-service-auth?sort=semver)

![Hono logo](logo/PNG-150dpi/HONO-Logo_Bild-Wort_quadrat-w-200x180px.png)

[Eclipse Hono](https://www.eclipse.org/hono) provides uniform (remote) service interfaces for connecting large
numbers of IoT devices to a (cloud) back end. It specifically supports scalable and secure data ingestion
(*telemetry* data) as well as *command & control* type message exchange patterns and provides interfaces for
provisioning & managing device identity and access control rules.

## Getting started

Please refer to the [Getting Started guide](https://www.eclipse.org/hono/docs/getting-started/) on the project web site.

## Running Hono

Eclipse Hono consists of multiple micro service components provided as container images. Please refer to the
[Admin Guide](https://www.eclipse.org/hono/docs/admin-guide/) and
[User Guide](https://www.eclipse.org/hono/docs/user-guide/) sections on the project web site for details on how
to configure and use these components.

## Using Hono

Please take a look at the [Developer Guide](https://www.eclipse.org/hono/docs/dev-guide/) which provides examples of
how clients can interact with Hono and how to create a custom protocol adapter.

## Remote API

Applications can interact with Hono by means of Apache Kafka &trade; or AMQP 1.0 based message exchanges. Please refer
to the corresponding [API documentation pages](https://www.eclipse.org/hono/docs/api/) for details.

## Get in Touch

Please check out the [Eclipse Hono project home page](https://www.eclipse.org/hono/community/get-in-touch/) for
details regarding our Gitter channel and mailing list.
