# gets the latest Fedora from Docker Hub
FROM fedora:latest

# required dependencies
RUN dnf -y install gcc gcc-c++ make cmake libuuid-devel

# dependencies needed for ssl support
RUN dnf -y install openssl-devel

# dependencies needed for Cyrus SASL support
RUN dnf -y install cyrus-sasl-devel cyrus-sasl-plain cyrus-sasl-gssapi cyrus-sasl-md5 cyrus-sasl-sql

# dependencies needed for Python binding
RUN dnf -y install swig python-devel

# install wget and tar for getting source code
RUN dnf -y install wget tar

# output directory with binaries for Qpid Proton and Qpid Dispatch Router
ENV OUTDIR /binaries

# make the working directory for building, copying needed files
RUN mkdir working
ADD build_tarball /working
ADD Makefile /working

WORKDIR /working
CMD make all
