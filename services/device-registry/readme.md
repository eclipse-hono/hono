This module contains an implementation of Hono's Device Registry as a separate microservice.
It implements the Credentials API, the Registration API and the Tenant API - all of them based on a file.

Production ready implementations can replace the file mechanism by better persistence layers (that are prepared for horizontal scalability e.g.).
The file implementation only serves as a blueprint and to have a fully working (but not scalable) implementation.

Please refer to the [Eclipse Hono APIs](<a href="https://www.eclipse.org/hono/api/"/>
) for details.
