# The FROM field will be overridden by our build configuration
# in the Hono template and is here just for completness.

FROM fabric8/s2i-java:2.2

#
# A custom base image for running our applications
#

MAINTAINER Jens Reimann <jreimann@redhat.com>
LABEL maintainer "Jens Reimann <jreimann@redhat.com>"

USER root

RUN yum update -y
RUN yum install -y iproute less
RUN yum install -y --enablerepo=base-debuginfo java-1.8.0-openjdk-debuginfo

USER 1000
