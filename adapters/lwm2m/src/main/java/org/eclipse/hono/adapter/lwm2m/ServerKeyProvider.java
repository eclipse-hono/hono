/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.lwm2m;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * Provides access to the server's private keys and trust anchor.
 *
 */
public interface ServerKeyProvider {

    /**
     * Returns the Server Public Key
     */
    PublicKey getServerPublicKey();

    /**
     * Returns the Server Private Key
     */
    PrivateKey getServerPrivateKey();

    /**
     * Returns the Server X509 Certificate Chain
     */
    X509Certificate[] getServerX509CertChain();

    /**
     * Returns the trusted certificates
     */
    Certificate[] getTrustedCertificates();
}
