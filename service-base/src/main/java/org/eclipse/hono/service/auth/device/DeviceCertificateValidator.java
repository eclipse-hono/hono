/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.service.auth.X509CertificateChainValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;


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

        Objects.requireNonNull(chain);
        Objects.requireNonNull(trustAnchor);

        return validate(chain, Set.of(trustAnchor));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> validate(final List<X509Certificate> chain, final Set<TrustAnchor> trustAnchors) {

        Objects.requireNonNull(chain);
        Objects.requireNonNull(trustAnchors);

        if (chain.isEmpty()) {
            throw new IllegalArgumentException("certificate chain must not be empty");
        } else if (trustAnchors.isEmpty()) {
            throw new IllegalArgumentException("trust anchor list must not be empty");
        }

        final Promise<Void> result = Promise.promise();

        try {
            final PKIXParameters params = new PKIXParameters(trustAnchors);
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
            if (e instanceof CertificateException) {
                result.fail(e);
            } else {
                result.fail(new CertificateException("validation of device certificate failed", e));
            }
        }
        return result.future();
    }
}
