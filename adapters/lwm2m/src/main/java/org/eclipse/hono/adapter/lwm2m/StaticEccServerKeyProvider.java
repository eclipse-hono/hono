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

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.KeySpec;

import javax.annotation.PostConstruct;

import org.eclipse.leshan.util.Hex;
import org.springframework.stereotype.Component;

/**
 * Provides a static set of ECC based keys.
 * <p>
 * No X.509 certificates are supported.
 */
@Component
public class StaticEccServerKeyProvider implements ServerKeyProvider {

    private PublicKey serverPublicKey;
    private PrivateKey serverPrivateKey;

    @PostConstruct
    public void init() throws GeneralSecurityException {
        createKeys();
    }

    private void createKeys() throws GeneralSecurityException {

        // Get point values
        byte[] publicX = Hex
                .decodeHex("fcc28728c123b155be410fc1c0651da374fc6ebe7f96606e90d927d188894a73".toCharArray());
        byte[] publicY = Hex
                .decodeHex("d2ffaa73957d76984633fc1cc54d0b763ca0559a9dff9706e9f4557dacc3f52a".toCharArray());
        byte[] privateS = Hex
                .decodeHex("1dae121ba406802ef07c193c1ee4df91115aabd79c1ed7f4c0ef7ef6a5449400".toCharArray());

        // Get Elliptic Curve Parameter spec for secp256r1
        AlgorithmParameters algoParameters = AlgorithmParameters.getInstance("EC");
        algoParameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec parameterSpec = algoParameters.getParameterSpec(ECParameterSpec.class);

        // Create key specs
        KeySpec publicKeySpec = new ECPublicKeySpec(new ECPoint(new BigInteger(publicX), new BigInteger(publicY)),
                parameterSpec);
        KeySpec privateKeySpec = new ECPrivateKeySpec(new BigInteger(privateS), parameterSpec);

        // Get keys
        serverPublicKey = KeyFactory.getInstance("EC").generatePublic(publicKeySpec);
        serverPrivateKey = KeyFactory.getInstance("EC").generatePrivate(privateKeySpec);

    }

    @Override
    public PublicKey getServerPublicKey() {
        return serverPublicKey;
    }

    @Override
    public PrivateKey getServerPrivateKey() {
        return serverPrivateKey;
    }

    /**
     * @return An empty array.
     */
    @Override
    public X509Certificate[] getServerX509CertChain() {
        return new X509Certificate[0];
    }

    /**
     * @return An empty array.
     */
    @Override
    public Certificate[] getTrustedCertificates() {
        return new Certificate[0];
    }
}
