# The FROM field will be overridden by our build configuration
# in the Hono template and is here just for completeness.

FROM fabric8/s2i-java:2.2

#
# A custom base image for running our applications
#

MAINTAINER Jens Reimann <jreimann@redhat.com>
LABEL maintainer "Jens Reimann <jreimann@redhat.com>"

ENV \
    NETTY_TCNATIVE_VERSION=2.0.10.Final

USER root

RUN yum update -y
RUN yum install -y iproute less
RUN yum install -y --enablerepo=base-debuginfo java-1.8.0-openjdk-debuginfo

#
# Install APR for performance improved TLS/SSL
#

RUN yum install -y apr

#
# Install a locally compiled version of the JNI library for netty-tcnative
# matching with the locally available SSL library.
#

RUN \
    yum install -y git autoconf automake libtool which apr-devel make openssl-devel && \
    git clone https://github.com/netty/netty-tcnative && \
    cd netty-tcnative && \
    git checkout netty-tcnative-parent-$NETTY_TCNATIVE_VERSION && \
    cd .. && \
    mvn -B clean install -f netty-tcnative/openssl-dynamic/pom.xml -am && \
    install -m 0755 netty-tcnative/openssl-dynamic/target/native-lib-only/META-INF/native/linux64/libnetty_tcnative.so /usr/lib64/libnetty_tcnative.so && \
    rm -Rf ~/.m2/repository && \
    rm -Rf netty-tcnative && \
    yum autoremove -y git autoconf automake libtool which apr-devel make openssl-devel && yum -y clean all && rm -rf /var/cache/yum

#
# Switch back to user
#

USER 1000
