/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.service.auth;

import java.security.cert.CertificateException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;

import io.vertx.core.Future;

/**
 * A function for validating certificate paths.
 *
 */
@FunctionalInterface
public interface X509CertificateChainValidator {

    /**
     * Validates a certificate path based on a given trust anchor.
     * 
     * @param chain The certificate chain to validate. The end certificate
     *              must be at position 0.
     * @param trustAnchor The trust anchor to use for validating the chain.
     * @return A completed future if the path is valid (according to the implemented tests).
     *         Otherwise, the future will be failed with a {@link CertificateException}.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if the chain is empty.
     */
    Future<Void> validate(List<X509Certificate> chain, TrustAnchor trustAnchor);
}
