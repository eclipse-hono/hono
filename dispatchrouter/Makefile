PROJECT_NAME=qpid-dispatch
REPO=eclipsehono/$(PROJECT_NAME)
DOCKERFILE_DIR ?= .
COMMIT ?= latest

all: build
	echo "Running docker build"
	bash ./docker-build.sh $(REPO) $(DOCKERFILE_DIR)

.PHONY: build