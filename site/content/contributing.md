+++
title = "Contributing"
menu = "main"
weight = 620
+++

First of all, thanks for considering to contribute to Eclipse Hono&trade;. We really appreciate the time and effort you want to
spend helping to improve things around here. And help we can use :-)

Here is a (non-exclusive, non-prioritized) list of things you might be able to help us with:

* bug reports
* bug fixes
* improvements regarding code quality e.g. improving readability, performance, modularity etc.
* documentation (Getting Started guide, Examples, Deployment instructions ni cloud environments)
* features (both ideas and code are welcome)

You might also want to take a look at our GitHub Issues page (see drawer on the left) and see if you can help out with any of the issues listed.

## Legal Requirements

In order to get you started as fast as possible we need to go through some organizational issues first, though.

Hono is an [Eclipse IoT](https://iot.eclipse.org) project and as such is governed by the Eclipse Development process.
This process helps us in creating great open source software within a safe legal framework.

For you as a contributor, the following preliminary steps are required in order for us to be able to accept your contribution:

1. Sign the [Eclipse Foundation Contributor License Agreement](https://eclipse.org/contribute/cla).
In order to do so:

   * Obtain an Eclipse Foundation user ID. Anyone who currently uses Eclipse Bugzilla or Gerrit systems already has one of those.
If you don't already have an account simply [register on the Eclipse web site](https://dev.eclipse.org/site_login/createaccount.php).
   * Once you have your account, log in to the [projects portal](https://projects.eclipse.org/), select *My Account* and then the *Contributor License Agreement* tab.

1. Add your GiHub username to your Eclipse Foundation account. Log in to Eclipse and go to [Edit my account](https://dev.eclipse.org/site_login/myaccount.php).

The easiest way to contribute code/patches/whatever is by creating a GitHub pull request (PR). When you do make sure that you *Sign-off* your commit records using the same email address used for your Eclipse account.

You do this by adding the `-s` flag when you make the commit(s), e.g.

    $> git commit -s -m "Shave the yak some more"

You can find all the details in the [Contributing via Git](http://wiki.eclipse.org/Development_Resources/Contributing_via_Git) document on the Eclipse web site.

## Conventions

1. The *groupId* of modules is `org.eclipse.hono`, *artifactId* always starts with `hono-`.
1. Modules producing a Docker image generally use `eclipse/${artifactId}` as the image's repository name. In some cases we do not use the <em>artifactId</em> but use a more descriptive name, e.g. the `application` module produces an image using `eclipse/hono-server` as the repository name, because the <em>application</em> module simply wraps the code from `server` into a Spring Boot application and creates a Docker image from it.
1. All code complies with the formatting rules defined by the settings files in the `eclipse` folder.
1. Modules implementing one of the Hono APIs are sub-modules of `services`.

## Making your Changes

1. [Fork the repository on GitHub](https://github.com/eclipse/hono#fork-destination-box)
1. Create a new branch for your changes
1. Make your changes
1. Make sure your code complies with the formatting rules defined by the settings files in the `eclipse` folder.
1. Make sure you include test cases for non-trivial features
1. Make sure the test suite passes after your changes
1. Commit your changes into that branch
1. Use descriptive and meaningful commit messages
1. Squash multiple commits related to the same feature/issue into a single one, if reasonable
1. Make sure you use the `-s` flag when committing as explained above
1. Push your changes to your branch in your forked repository

## Submitting the Changes

Submit a pull request via the normal GitHub UI.

## After Submitting

* Do not use your branch for any other development, otherwise further changes that you make will be visible in the PR.

