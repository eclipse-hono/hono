+++
title = "Sandbox"
menu = "main"
weight = 160
+++

You find our Eclipse hosted Hono sandbox under 

`hono.eclipse.org`  

From the browser with port 80 you will be redirected to this documentation page, you are just reading.

### Purpose

The sandbox hosts a publicly available Hono installation, which consists of the Hono docker installation, as described in the [Getting started]({{< relref "getting-started.md" >}}).

You could use it the same way without the need to install it yourself. The ports for all components are the same.

### Take note

- This is just a **sandbox**. It will be rolled out often and **all data will be lost**.   

- Devices and credentials could be created but not updated/deleted.

- The Grafana dashboard is not publicly available.

- The events are not backed by a broker, at the moment.

- You should choose a unique tenant name (e.g. a UUID), to be sure you do not interfere with other users on this test installation.  
