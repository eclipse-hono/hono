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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.vertx.core.Future;
import io.vertx.core.impl.CompositeFutureImpl;

/**
 * A function for validating certificate paths.
 *
 */
@FunctionalInterface
public interface X509CertificateChainValidator {

    /**
     * Validates a certificate path based on a trust anchor.
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

    /**
     * Validates a certificate path based on a list of trust anchors.
     * <p>
     * This default implementation simply calls {@link #validate(List, TrustAnchor)} for each trust anchor in the list.
     * 
     * @param chain The certificate chain to validate. The end certificate
     *              must be at position 0.
     * @param trustAnchors The list of trust anchors to use for validating the chain.
     * @return A completed future if the certificate path is valid for any of the trust anchors.
     *         Otherwise, the future will be failed when all the trust anchors cannot be validated by the certificate path.
     *
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if the chain and/or trust anchors is empty.
     */
    default Future<Void> validate(List<X509Certificate> chain, List<TrustAnchor> trustAnchors) {

        Objects.requireNonNull(chain);
        Objects.requireNonNull(trustAnchors);

        if (chain.isEmpty() || trustAnchors.isEmpty()) {
            throw new IllegalArgumentException("certificate chain and/or trust anchors must not be empty");
        }

        final Future<Void> result = Future.future();
        final List<Future<Void>> futures = new ArrayList<>();

        trustAnchors.forEach(trustAnchor -> {
            futures.add(validate(chain, trustAnchor));
        });
        CompositeFutureImpl.any(futures.toArray(Future[]::new)).setHandler(validationCheck -> {
            if (validationCheck.succeeded()) {
                result.complete();
            } else {
                result.fail(validationCheck.cause());
            }
        });
        return result;

    }
}
