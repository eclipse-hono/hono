FROM fedora:latest

# installing needed libraries (i.e. for SASL, SSL/TLS, Python, ...)
RUN dnf -y install libuuid openssl \
                   cyrus-sasl-lib cyrus-sasl-plain cyrus-sasl-sql cyrus-sasl-gssapi cyrus-sasl-md5 \
                   qpid-dispatch-router-${qpid-dispatch.version} qpid-dispatch-tools-${qpid-dispatch.version} && \
    dnf -y update && \
    dnf clean all

CMD ["bash"]
