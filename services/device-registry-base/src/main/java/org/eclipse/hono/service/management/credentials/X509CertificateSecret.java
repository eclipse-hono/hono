/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.service.management.credentials;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * This class encapsulates secret information for an X509 certificate credentials type.
 */
@RegisterForReflection
public class X509CertificateSecret extends CommonSecret {

    /**
     * {@inheritDoc}
     * <p>
     * This method does nothing because this secret has no X.509 specific properties.
     */
    @Override
    protected void mergeProperties(final CommonSecret otherSecret) {
        // nothing to merge
    }
}
