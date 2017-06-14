+++
title = "Developing Hono"
menu = "main"
weight = 600
+++

This page contains information for people involved in developing Hono.

## Conventions

1. The *groupId* of modules is `org.eclipse.hono`, *artifactId* always starts with `hono-`.
1. Modules producing a Docker image generally use `eclipsehono/${artifactId}` as the image's repository name. In some cases we do not use the <em>artifactId</em> but use a more descriptive name, e.g. the `application` module produces an image using `eclipsehono/hono-server` as the repository name, because the <em>application</em> module simply wraps the code from `server` into a Spring Boot application and creates a Docker image from it.
1. All code complies with the formatting rules defined by the settings files in the `eclipse` folder.
1. Modules implementing one of the Hono APIs are sub-modules of `services`.
