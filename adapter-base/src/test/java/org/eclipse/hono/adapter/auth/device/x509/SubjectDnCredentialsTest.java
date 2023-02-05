/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Test;


/**
 * Verifies behavior of {@link SubjectDnCredentials}.
 *
 */
public class SubjectDnCredentialsTest {

    /**
     * Verifies that the auth-id created from a subject DN is normalized.
     */
    @Test
    public void testAuthIdIsRFC2253Compliant() {

        final String subjectDn = "emailAddress=hoge@acme.com, CN=devices, OU=ACME Department, O=ACME Corporation, L=Chiyoda, ST=Tokyo, C=JP";
        final SubjectDnCredentials credentials = SubjectDnCredentials.create("tenant", subjectDn);
        assertThat(credentials.getAuthId())
            .isEqualTo("1.2.840.113549.1.9.1=#160d686f67654061636d652e636f6d,CN=devices,OU=ACME Department,O=ACME Corporation,L=Chiyoda,ST=Tokyo,C=JP");
    }

    /**
     * Verifies that the create methods without client context parameter create a client context.
     */
    @Test
    public void testClientContextCreatedIsNotNull() {

        final SubjectDnCredentials credentials1 = SubjectDnCredentials.create("tenant", "CN=eclipse.org");
        assertThat(credentials1.getClientContext()).isNotNull();

        final SubjectDnCredentials credentials2 = SubjectDnCredentials.create("tenant",
                new X500Principal("CN=eclipse.org"));
        assertThat(credentials2.getClientContext()).isNotNull();
    }

    /**
     * Verifies that you can not pass null in for the client context in the create method that takes the subjectDn as a
     * string.
     */
    @Test
    public void testCreateDoesNotAllowNullClientContext() {

        assertThrows(NullPointerException.class, () -> SubjectDnCredentials.create("tenant", "CN=eclipse.org", null));
    }

    /**
     * Verifies that you can not pass null in for the client context in the create method that takes the subjectDn as a
     * X500Principal.
     */
    @Test
    public void testCreateDoesNotAllowNullClientContextWithPrincipal() {

        final var subjectDn = new X500Principal("CN=eclipse.org");
        assertThrows(NullPointerException.class, () -> SubjectDnCredentials.create("tenant", subjectDn, null));
    }

}
