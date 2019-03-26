This folder contains the content of the Hono website.
It is built using the [Hugo template system](https://gohugo.io).
The website consists of the homepage and the documentation, which are two separate hugo projects. 

# Building

In order to build the site

1. [Install Hugo](https://gohugo.io/overview/installing/) on your local system.
2. Run

    ~/hono/site$ mvn clean install

This will render the HTML pages constituting the site into `~/hono/site/target`.

Please refer to the Hugo documentation for more options.

**The result of this build is not what is being deployed to the actual Hono web site, which is more complex and includes
 documentation of older versions.** This is done by the jenkins pipelines `jenkins/Hono-Release-Pipeline.groovy` 
 and `jenkins/Hono-Website-Pipeline.groovy`.

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
This is performed every night by the Eclipse build server using the Jenkins pipeline 
script `~/hono/jenkins/Hono-Website-Pipeline.groovy`.
