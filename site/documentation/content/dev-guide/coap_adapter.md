+++
title = "Considerations for extending the CoAP Adapter"
weight = 398
+++

This page provides some hints worth considering when extending the CoAP protocol adapter's functionality.

## Option Numbers

The CoAP adapter uses several of the standard options defined by [RFC 7252](https://www.rfc-editor.org/rfc/rfc7252.html).
Unlike HTTP header fields which are simple strings, the options used in CoAP request and response messages
are defined by means of integer numbers. The assignment and registration of option numbers is defined in
[RFC 7252, Section 12.2](https://www.rfc-editor.org/rfc/rfc7252.html#section-12.2). In particular, the RFC reserves
the number range 65000-65535 for experimental use.

When adding new Hono specific options to the CoAP adapter, developers are encouraged to use numbers from the 65175-65349
range as [discussed and agreed with the Eclipse Californium project](https://github.com/eclipse-hono/hono/issues/3499#issuecomment-1602478483).
