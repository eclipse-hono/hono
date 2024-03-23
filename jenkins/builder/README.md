The Dockerfile in this module is used to create a container image that is used by the Hono build pipeline
definitions in $HONO_HOME/jenkins. The container image provides a consistent set of Graal SDK, Maven and other
tools which form the basis of reproducible builds.

The image can be built using

```sh
# in folder $HONO_HOME/jenkins/builder
docker build -t eclipse/hono-builder:2.5.0 -f HonoBuilder.dockerfile .
```

The resulting image needs to be pushed (manually) to Docker Hub before running any of the build pipelines
on the Eclipse Jenkins build infrastructure:

```sh
docker push eclipse/hono-builder:2.5.0
```

