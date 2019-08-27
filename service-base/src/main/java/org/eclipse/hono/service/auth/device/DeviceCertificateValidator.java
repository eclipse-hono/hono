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

package org.eclipse.hono.service.auth.device;

import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.service.auth.X509CertificateChainValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.impl.CompositeFutureImpl;


/**
 * Validates a device's certificate chain using a {@link CertPathValidator}.
 *
 */
public class DeviceCertificateValidator implements X509CertificateChainValidator {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceCertificateValidator.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> validate(final List<X509Certificate> chain, final TrustAnchor trustAnchor) {
        return validate(chain, List.of(trustAnchor));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> validate(final List<X509Certificate> chain, final List<TrustAnchor> trustAnchors) {

        Objects.requireNonNull(chain);
        Objects.requireNonNull(trustAnchors);

        if (chain.isEmpty() || trustAnchors.isEmpty()) {
            throw new IllegalArgumentException("certificate chain and/or trust anchors must not be empty");
        }

        final Future<Void> result = Future.future();
        final List<Future<Void>> futures = new ArrayList<>();

        trustAnchors.forEach(trustAnchor -> {
            futures.add(doValidate(chain, trustAnchor));
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

    private Future<Void> doValidate(final List<X509Certificate> chain, final TrustAnchor trustAnchor) {

        final Future<Void> result = Future.future();

        try {
            final PKIXParameters params = new PKIXParameters(Collections.singleton(trustAnchor));
            // TODO do we need to check for revocation?
            params.setRevocationEnabled(false);
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            final CertPath path = factory.generateCertPath(chain);
            final CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            validator.validate(path, params);
            LOG.debug("validation of device certificate [subject DN: {}] succeeded",
                    chain.get(0).getSubjectX500Principal().getName());
            result.complete();
        } catch (GeneralSecurityException e) {
            LOG.debug("validation of device certificate [subject DN: {}] failed",
                    chain.get(0).getSubjectX500Principal().getName(), e);
            if (CertificateException.class.isInstance(e)) {
                result.fail(e);
            } else {
                result.fail(new CertificateException("validation of device certificate failed", e));
            }
        }
        return result;
    }
}
