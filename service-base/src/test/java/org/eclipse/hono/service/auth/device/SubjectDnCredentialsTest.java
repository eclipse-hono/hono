/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.auth.device;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;


/**
 * Verifies behavior of {@link SubjectDnCredentials}.
 *
 */
public class SubjectDnCredentialsTest {

    /**
     * Verifies that the credentials contain the tenant-id.
     */
    @Test
    public void testCredentialsContainTenant() {

        final SubjectDnCredentials credentials = SubjectDnCredentials.create("tenant", "CN=device");
        assertThat(credentials.getTenantId()).isEqualTo("tenant");
    }

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

}
