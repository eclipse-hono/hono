/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.management.credentials;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

/**
 * A credential type for storing the <a href="https://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a> formatted subject DN of
 * a client certificate.
 * <p>
 * See <a href="https://www.eclipse.org/hono/docs/api/credentials-api/#x-509-certificate">X.509 Certificate</a> for an
 * example of the configuration properties for this credential type.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class X509CertificateCredential extends CommonCredential {

    @JsonProperty
    private List<X509CertificateSecret> secrets = new LinkedList<>();

    @Override
    public List<X509CertificateSecret> getSecrets() {
        return secrets;
    }

    /**
     * Sets the list of X509 certificate secrets to use for authenticating a device to protocol adapters.
     * <p>
     * The list cannot be empty and each secret is scoped to the validity period of the certificate.
     *
     * @param secrets The secret to set.
     * @return        a reference to this for fluent use.
     */
    public X509CertificateCredential setSecrets(final List<X509CertificateSecret> secrets) {
        this.secrets = secrets;
        return this;
    }
}
