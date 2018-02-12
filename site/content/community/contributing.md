+++
title = "Contributing"
weight = 610
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

You might also want to take a look at our GitHub Issues page (see drawer on the left) and see if you can help out with any of the issues listed. We have put a `help wanted` label on those issues that we are particularly keen on receiving contributions for.

## Legal Requirements

In order to get you started as fast as possible we need to go through some organizational issues first, though.

Hono is an [Eclipse IoT](https://iot.eclipse.org) project and as such is governed by the Eclipse Development process.
This process helps us in creating great open source software within a safe legal framework.

For you as a contributor, the following preliminary steps are required in order for us to be able to accept your contribution:

1. Sign the [Eclipse Foundation Contributor License Agreement](https://eclipse.org/contribute/cla).
   In order to do so:

   * Obtain an Eclipse Foundation user ID. Anyone who currently uses Eclipse Bugzilla or Gerrit systems already has one of those. If you don't already have an account simply [register on the Eclipse web site](https://dev.eclipse.org/site_login/createaccount.php).
   * Once you have your account, log in to the [Eclipse Project Portal](https://projects.eclipse.org/), select *My Account* and then the *Contributor License Agreement* tab.

1. Add your GiHub username to your Eclipse Foundation account. Log in to Eclipse and go to [Edit my account](https://dev.eclipse.org/site_login/myaccount.php).

The easiest way to contribute code/patches/whatever is by creating a GitHub pull request (PR). When you do, make sure that you *Sign-off* your commit records using the same email address used for your Eclipse account.

You do this by adding the `-s` flag when you make the commit(s), e.g.

    $> git commit -s -m "Shave the yak some more"

You can find all the details in the [Contributing via Git](http://wiki.eclipse.org/Development_Resources/Contributing_via_Git) document on the Eclipse web site.

## Conventions

1. The *groupId* of all Hono modules is `org.eclipse.hono`, *artifactId* always starts with `hono-`.
1. Modules producing a Docker image generally use `eclipse/${artifactId}` as the image's repository name.
1. All code complies with the formatting rules defined by the settings files in the `eclipse` folder.
1. Modules implementing one of the Hono APIs are sub-modules of `services`.

## Making your Changes

1. [Fork the repository on GitHub](https://github.com/eclipse/hono#fork-destination-box).
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
         * terms of the Eclipse Public License 1.0 which is available at
         * https://www.eclipse.org/legal/epl-v10.html
         *
         * SPDX-License-Identifier: EPL-1.0
         */

6. Do not forget to add yourself or your organization to the copyright holder list in the NOTICE file in the parent folder if you haven't already done so in a previous contribution.
7. Make sure you include test cases for non-trivial features.
8. Make sure the test suite passes after your changes.
9. Commit your changes into your *feature branch*.
10. Use descriptive and meaningful commit messages.
11. Squash multiple commits related to the same feature/issue into a single one, if reasonable.
12. Make sure you use the `-s` flag when committing as explained above.
13. Push your changes to your branch in your forked repository.

## Submitting the Changes

Submit a pull request via the normal GitHub UI.

## After Submitting

* Do not use your *feature branch* for any other development, otherwise further changes that you make will be visible in the PR.

