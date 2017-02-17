This folder contains the content of the Hono website.
It is built using the [Hugo template system](https://gohugo.io).

# Building

In order to build the site

1. [Install Hugo](https://gohugo.io/overview/installing/) on your local system.
2. Run

    ~/hono/site$ ./build-site.sh

This will render the HTML pages constituting the site into the `public` folder.

Please refer to the Hugo documentation for more options.

# Debugging

In order to locally debug the web site you can use Hugo's built-in web server like this:

    ~/hono/site$ hugo server -v

# Publishing

Hono's web site is hosted on infrastructure operated and provided by the Eclipse Foundation.
The web site is available at https://www.eclipse.org/hono.

Publishing changes to the web site is done by means of pushing HTML files and other content to Hono's
web site Git repository at the Eclipse Foundation which can be accessed using any of the following URIs, depending on the protocol you want to use.

`ssh://committer_id@git.eclipse.org:29418/www.eclipse.org/hono`

or

`https://committer_id@git.eclipse.org/r/www.eclipse.org/hono`

In any case the `committer_id` needs to be replaced with your Eclipse *committer id* which you should be able to determine as described to me by Eclipse support staff as follows:

> You should have it (the committer id) in the very first emails you received when becoming a committer. It's true that this is a piece of information that might easily be forgotten now that many projects just seldom commit on eclipse.org's git repos...
FWIW I think you can also find the information in Gerrit, at https://git.eclipse.org/r/#/settings/ (you can login using your email :-))

## Cloning the Web Site Repo

Once you have figured out your committer id you can then clone the web site repository as usual:

    $ git clone ssh://committer_id@git.eclipse.org:29418/www.eclipse.org/hono hono-web-site

You can now make your changes to the working copy (under `hono-web-site` in this case) and commit your changes locally.

## Making changes

The intended way of making changes to the web site is by means of editing the source files under `site/content` in Hono's GitHub repository and then generating the web site (the HTML files etc) using Hugo.
While editing the source files you can constantly debug your changes as described above.

Once you are done, you can publish the whole web site (including your changes) to the checked out web site repository using Hugo as follows:

    ~/hono/site$ rm -rf ~/hono-web-site/*
    ~/hono/site$ hugo -v -d ~/hono-web-site

The first command removes all previous content from the web site folder (except for the `.git` folder). This is important for making sure that any files you have removed from the source folder does not show up in the web site anymore. The second command then creates the whole web site from the current source files.

## Pushing to the Web Site Repo

Once you are done with your changes make sure that you have committed them locally. You can then push the changes using

    ~/hono-web-site$ git push origin HEAD:refs/heads/master
