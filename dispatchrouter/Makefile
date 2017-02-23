all: build

build:
	bash build_tarball ${ROUTER_SOURCE_URL}

clean:
	rm -rf proton_build proton_install qpid-dispatch.tar.gz qpid-dispatch-src qpid-proton.tar.gz qpid-proton-src staging build qpid-dispatch-binary.tar.gz qpid-proton-binary.tar.gz

.PHONY: build clean