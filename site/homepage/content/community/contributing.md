+++
linkTitle = "Contributing"
title = "How to get involved in the Hono Project"
description = "General requirements and specific instructions for contributing code and/or documentation to Hono."
weight = 610
type = "page"
[menu.main]
    parent = "Community"
+++

Thank you for considering to contribute to Eclipse Hono&trade;. We really appreciate the time and effort you want to
spend helping to improve things around here. And help we can use :-)
<!--more-->

Here is a (non-exclusive, non-prioritized) list of things you might be able to help us with:

* bug reports
* bug fixes
* improvements regarding code quality e.g. improving readability, performance, modularity etc.
* documentation (Getting Started guide, Examples, Deployment instructions for cloud environments)
* features (both ideas and code are welcome)

You might also want to take a look at our [GitHub Issues](https://github.com/eclipse-hono/hono/issues) page and see if you can help out with any of the issues listed. We have put a [help wanted](https://github.com/eclipse-hono/hono/labels/help%20wanted) label on those issues that we are particularly keen on receiving contributions for.

## Eclipse Contributor Agreement

In order to be able to contribute to Eclipse Foundation projects you must
electronically sign the Eclipse Contributor Agreement (ECA).

* https://www.eclipse.org/legal/ECA.php

The ECA provides the Eclipse Foundation with a permanent record that you agree
that each of your contributions will comply with the commitments documented in
the Developer Certificate of Origin (DCO). Having an ECA on file associated with
the email address matching the "Author" field of your contribution's Git commits
fulfills the DCO's requirement that you sign-off on your contributions.

For more information, please see the Eclipse Project Handbook:
https://www.eclipse.org/projects/handbook/#resources-commit

## Conventions

1. The *groupId* of all Hono modules is `org.eclipse.hono`, *artifactId* always starts with `hono-`.
1. Modules producing a Docker image generally use `eclipse/${artifactId}` as the image's repository name.
1. All code complies with the formatting rules defined by the settings files in the `eclipse` folder.
1. Modules implementing one of the Hono APIs are sub-modules of `services`.

## Making your Changes

1. [Fork the repository on GitHub](https://github.com/eclipse-hono/hono/fork).
2. Create a new *feature branch* for your changes.
3. Make your changes.
4. Make sure your code complies with the formatting rules defined by the settings files in the `eclipse` folder.
5.  If you are creating new class files, make sure that they include a [proper copyright header](https://www.eclipse.org/projects/handbook/#ip-copyright-headers) at the top.
    Any new file created should contain a header based on the following template:

        /**
         * Copyright (c) {year} Contributors to the Eclipse Foundation
         *
         * See the NOTICE file(s) distributed with this work for additional
         * information regarding copyright ownership.
         *
         * This program and the accompanying materials are made available under the
         * terms of the Eclipse Public License 2.0 which is available at
         * https://www.eclipse.org/legal/epl-2.0
         *
         * SPDX-License-Identifier: EPL-2.0
         */

6. Do not forget to add yourself or your organization to the copyright holder list in the NOTICE file in the parent folder if you haven't already done so in a previous contribution.
7. Make sure you include test cases for non-trivial features.
8. Make sure the test suite passes after your changes.
9. Commit your changes into your *feature branch*.
10. Use descriptive and meaningful commit messages.
11. Squash multiple commits related to the same feature/issue into a single one, if reasonable.
12. Push your changes to your branch in your forked repository.

## Submitting the Changes

Submit a pull request via the GitHub UI.

## After Submitting

Do not use your *feature branch* for any other development, otherwise further changes that you make will be visible in the PR.

