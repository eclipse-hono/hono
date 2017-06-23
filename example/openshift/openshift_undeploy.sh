#!/bin/sh

echo UNDEPLOYING ECLIPSE HONO FROM OPENSHIFT

# deleting entire project with related resources
oc login -u developer
oc delete project hono

# deleting Hono Server persistent volume
oc login -u system:admin
oc delete pv hono-pv

# deleting the directory for hosting persistent volume
rm -rf /tmp/hono

echo ECLIPSE HONO UNDEPLOYED FROM OPENSHIFT