+++
title = "OpenShift / OKD"
weight = 475
aliases = [
    "/deployment/openshift_s2i"
]
+++

In Hono version 1.0 we dropped the OpenShift specific deployment using the
source-to-image (S2I) model, in favor of the Helm charts and the
Eclipse IoT Packages project.

You can still deploy to OpenShift and OKD, using the Helm charts. And you can
also use *routes* to expose services.
Deploying using S2I is also still possible, however the Hono project simply no
longer provides out-of-the box scripts for doing so.
