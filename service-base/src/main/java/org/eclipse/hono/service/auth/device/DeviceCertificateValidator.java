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
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.service.auth.X509CertificateChainValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;


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

        if (chain.isEmpty()) {
            throw new IllegalArgumentException("certificate chain must not be empty");
        }

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
            result.fail(e);
        }
        return result;
    }
}
