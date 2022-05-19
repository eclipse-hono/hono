This folder contains the content of the Hono website.
It is built using the [Hugo template system](https://gohugo.io).
The website consists of the *homepage* and the *documentation*, which are two separate hugo projects. 

# Links between Homepage and Documentation

There are two Hugo shortcodes available for creating HTML links between the projects:

* use the `doclink` shortcode for creating a link to the documentation from the homepage
* use the `homelink` shortcode for creating a link to the homepage from the documentation

Both shortcodes accept a single (unnamed) parameter which is the URL of the link target relative to the respective document base.
A link from the homepage to the HTTP adapter user guide can be created using the following markdown:

```
[HTTP adapter user guide]({{% doclink "/user-guide/http-adapter/" %}})
```

The short codes use a site parameter set in the `config.toml` file to determine the base URL of the link target.

# Sequence diagrams

The UML Sequence Diagrams are created with the [PlantUML online server](http://www.plantuml.com/plantuml). The SVG files
contain the source code and can be edited by pasting their link on this page like e.g.:

    https://raw.githubusercontent.com/eclipse/hono/master/site/documentation/content/api/telemetry-kafka/consume_kafka.svg

# Building locally

In order to build the site

1. [Install Hugo](https://gohugo.io/overview/installing/) on your local system.
2. Run

    ~/hono/site$ mvn clean install

This will render the HTML pages constituting the site into `~/hono/site/target`.

Please refer to the Hugo documentation for more options.

**The result of this build is not what is being deployed to the actual Hono web site, which is more complex and includes
 documentation of older versions** (see below).

# Debugging

In order to locally debug the web site you can use Hugo's built-in web server like this:

    ~/hono/site/documentation$ hugo server -v
    
or, respectively:

    ~/hono/site/homepage$ hugo server -v

# Publishing

Hono's web site is hosted on infrastructure operated and provided by the Eclipse Foundation.
The web site is available at https://www.eclipse.org/hono.

Publishing changes to the web site is done by means of pushing HTML files and other content to Hono's
web site Git repository at the Eclipse Foundation.
This is performed every night by the Eclipse build server.

## Release and Versioning

For each supported version of Hono a separate version of the documentation is build. This is mostly automated by 
Jenkins pipelines. 

The build process of the documentation makes assumptions on the release of Hono. First of all the 
[Semantic Versioning](https://semver.org/) schema is used for version numbers. For the purpose of the documentation 
a version only includes MAJOR and MINOR level, meaning that the documentation always reflects the latest patch level 
of a MINOR (+ MAJOR) version of Hono.

Every *newly* released MINOR (or MAJOR) version is considered to be the stable version.

The files `site/documentation/versions_supported.csv` and `site/documentation/tag_stable.txt` determine which versions of the
documentation are published. They are _written_ by the release pipeline (`jenkins/Hono-Release-Pipeline.groovy`) and 
_read_ by the website pipeline (`jenkins/Hono-Website-Pipeline.groovy`).

The website pipeline builds the following versions:

* `dev`: always built from the current `HEAD` of the master branch
* `stable`: the version from the Git tag in the file `site/documentation/tag_stable.txt`
* each version in `site/documentation/versions_supported.csv`

### What are the checkboxes in the release pipeline doing?

If the checkbox *DEPLOY_DOCUMENTATION* is checked, the version that is being released is added (appended) to the file
 `site/documentation/versions_supported.csv`.
If the checkbox *STABLE_DOCUMENTATION* is checked additionally the Git tag of the version that is being released is 
written to the file `site/documentation/tag_stable.txt` (replaces the previous content). 
 
NB: Even though a change only on the patch level is not considered a new version, don't forget to check the 
checkboxes *DEPLOY_DOCUMENTATION* and *STABLE_DOCUMENTATION* in the release pipeline when releasing a patch for 
the stable version.

### How to set the checkboxes when releasing a new version?

| I want to release a new...    | Example   | `DEPLOY_DOCUMENTATION`    | `STABLE_DOCUMENTATION` |
 ---                            | ---       | ---                       | --- 
MINOR version                   | 1.**1**.0 | &#x2611;                  | &#x2611;       
MAJOR version                   | **2**.0.0 | &#x2611;                  | &#x2611;       
PATCH level on _stable_ version | 2.0.**1** | &#x2611;                  | &#x2611;       
PATCH level on older version    | 1.1.**1** | &#x2611;                  | &#x2610;       
pre-release version             | 2.1.0-M1  | &#x2610;                  | &#x2610;       

Versions that are no longer supported need to be manually removed from `site/documentation/versions_supported.csv`. Same goes
for old patch releases of a supported version. 
