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

package org.eclipse.hono.adapter.auth.device.x509;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertPathValidatorException.BasicReason;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.PKIXRevocationChecker.Option;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.service.auth.X509CertificateChainValidator;
import org.eclipse.hono.util.RevocableTrustAnchor;
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

        Exception lastException = null;
        // Need to validate each anchor separately to be able to configure specific revocation check settings
        for (TrustAnchor anchor : trustAnchors) {
            try {
                validateSingleAnchor(chain, anchor);
                // Successfully validated using this anchor
                result.complete();
                return result.future();
            } catch (CertPathValidatorException e) {
                lastException = e;
                if (e.getReason() == BasicReason.REVOKED || e.getReason() == BasicReason.UNDETERMINED_REVOCATION_STATUS) {
                    // Certificate trusted but revoked, exit now
                    LOG.warn("Certificate [subject DN: {}] revocation check failed.",
                            chain.get(0).getSubjectX500Principal().getName(), e);
                    break;
                }
            } catch (GeneralSecurityException e) {
                lastException = e;
            }
        }
        LOG.debug("validation of device certificate [subject DN: {}] failed",
                chain.get(0).getSubjectX500Principal().getName(), lastException);
        if (lastException instanceof CertificateException) {
            result.fail(lastException);
        } else {
            result.fail(new CertificateException("validation of device certificate failed", lastException));
        }
        return result.future();
    }

    private void validateSingleAnchor(final List<X509Certificate> chain, final TrustAnchor anchor)
            throws GeneralSecurityException {
        final PKIXParameters params = new PKIXParameters(Set.of(anchor));
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        final CertPath path = factory.generateCertPath(chain);
        final CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        configureRevocationCheck(validator, params, anchor);
        validator.validate(path, params);
        LOG.debug("validation of device certificate [subject DN: {}] succeeded",
                chain.get(0).getSubjectX500Principal().getName());
    }

    private void configureRevocationCheck(final CertPathValidator validator, final PKIXParameters parameters,
                                          final TrustAnchor trustAnchor) throws CertificateException {
        if (trustAnchor instanceof RevocableTrustAnchor revocableTrustAnchor
                && revocableTrustAnchor.isOcspEnabled()) {
            // Provide custom revocation check configuration per trusted anchor as described here:
            // https://docs.oracle.com/javase/8/docs/api/java/security/cert/CertPathValidator.html
            final PKIXRevocationChecker revocationChecker = (PKIXRevocationChecker) validator.getRevocationChecker();
            final Set<Option> options = EnumSet.noneOf(Option.class);
            parameters.setRevocationEnabled(true);
            if (revocableTrustAnchor.isOcspEnabled()) {
                if (revocableTrustAnchor.getOcspResponderUri() != null) {
                    revocationChecker.setOcspResponder(revocableTrustAnchor.getOcspResponderUri());
                }
                if (revocableTrustAnchor.getOcspResponderCert() != null) {
                    revocationChecker.setOcspResponderCert(revocableTrustAnchor.getOcspResponderCert());
                }
                // We always check end entities only for revocation in current implementation
                options.add(Option.ONLY_END_ENTITY);
                if (revocableTrustAnchor.isOcspNonceEnabled()) {
                    try {
                        // With default Java implementation, nonce can be enabled only by global java system property
                        // for all connections, we need to set extension manually to do it per trusted anchor
                        revocationChecker.setOcspExtensions(List.of(new OCSPNonceExtension()));
                    } catch (IOException e) {
                        throw new CertificateException("Cannot process OCSP nonce.", e);
                    }
                }
                // CRL is currently not supported
                options.add(Option.NO_FALLBACK);
            }
            revocationChecker.setOptions(options);
            parameters.addCertPathChecker(revocationChecker);
        } else {
            parameters.setRevocationEnabled(false);
        }
    }
}
