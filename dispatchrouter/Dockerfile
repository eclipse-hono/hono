FROM fedora:latest

# installing needed libraries (i.e. for SASL, SSL/TLS, Python, ...)
RUN dnf -y install cyrus-sasl-lib cyrus-sasl-plain libuuid openssl python cyrus-sasl-sql && dnf -y update && dnf clean all

# adding binaries for the Qpid Proton and Qpid Dispatch Router
ADD qpid-proton-binary.tar.gz /
ADD qpid-dispatch-binary.tar.gz /

CMD ["bash"]
