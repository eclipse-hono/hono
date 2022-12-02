/**
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.auth.device.x509;

import java.security.cert.Certificate;
import java.util.List;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A strategy for authenticating clients based on X.509 client certificates.
 *
 */
public interface X509Authentication {

    /**
     * Validates a certificate path.
     *
     * @param path The certificate path to validate.
     * @param requestedHostNames The host names conveyed by the client in a TLS SNI extension or {@code null} if
     *                          the client did not provide any.
     * @param spanContext The <em>OpenTracing</em> context in which the
     *                    validation should be executed, or {@code null}
     *                    if no context exists (yet).
     * @return A future indicating the outcome of the validation.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}
     *         if the certificate path could not be validated.
     *         <p>
     *         Otherwise, the future will be succeeded with a JSON object containing
     *         the authentication identifier of the device and the tenant that it
     *         belongs to.
     *         <p>
     *         Implementations should document the specific properties contained
     *         in the JSON object.
     * @throws NullPointerException if certificate path is {@code null}.
     */
    Future<JsonObject> validateClientCertificate(
            Certificate[] path,
            List<String> requestedHostNames,
            SpanContext spanContext);
}
