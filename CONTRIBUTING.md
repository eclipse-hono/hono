# How to contribute to Eclipse Hono

First of all, thanks for considering to contribute to Eclipse Hono. We really appreciate the time and effort you want to
spend helping to improve things around here. And help we can use :-)

Here is a (non-exclusive, non-prioritized) list of things you might be able to help us with:

* bug reports
* bug fixes
* improvements regarding code quality e.g. improving readability, performance, modularity etc.
* documentation (Getting Started guide, Examples, Deployment instructions for cloud environments)
* features (both ideas and code are welcome)

In order to get you started as fast as possible we need to go through some organizational issues first, though.

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

## Making your Changes

* Fork the repository on GitHub
* Create a new branch for your changes
* Make your changes
* When you create new files make sure you include a proper license header at the top of the file (see License Header section below).
* Make sure you include test cases for non-trivial features
* Make sure the test suite passes after your changes
* Commit your changes into that branch
* Use descriptive and meaningful commit messages. In particular, start the first line of the commit message with the
  number of the issue that the commit addresses, e.g. `[#9865] Add token based authentication.`
* Squash multiple commits that are related to each other semantically into a single one
* Push your changes to your branch in your forked repository

## Submitting the Changes

Submit a pull request via the normal GitHub UI.

## After Submitting

* Do not use your branch for any other development, otherwise further changes that you make will be visible in the PR.

## License Header

Please make sure any file you newly create contains a proper license header like this:

````
/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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
````
You should, of course, adapt this header to use the specific mechanism for comments pertaining to the type of file you create, e.g. using something like

````
<!--
 Copyright (c) 2018 Contributors to the Eclipse Foundation

 See the NOTICE file(s) distributed with this work for additional
 information regarding copyright ownership.

 This program and the accompanying materials are made available under the
 terms of the Eclipse Public License 2.0 which is available at
 https://www.eclipse.org/legal/epl-2.0

 SPDX-License-Identifier: EPL-2.0
-->
````

when adding an XML file.

**Important**

Please do not forget to add your name/organization to the `/legal/legal/NOTICE.md` file's *Copyright Holders* section. If this is not the first contribution you make, then simply update the time period contained in the copyright entry to use the year of your first contribution as the lower boundary and the current year as the upper boundary, e.g.

`Copyright 2017, 2018 ACME Corporation`

