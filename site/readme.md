This folder contains the content of the Hono website.
It is built using the [Hugo template system](https://gohugo.io).

## Building

In order to build the site

1. [Install Hugo](https://gohugo.io/overview/installing/) on your local system.
2. Run
   ~~~sh
   ~/hono/site$ ./build-site.sh
   ~~~

This will render the HTML pages constituting the site into the `public` folder.

Please refer to the Hugo documentation for more options.

## Debugging

In order to locally debug the web site you can use Hugo's built-in web server like this:

    ~/hono/site$ hugo server -d $DEST_DIR

