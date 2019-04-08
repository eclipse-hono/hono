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

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;


/**
 * An authentication provider that verifies an X.509 certificate using
 * Hono's <em>Credentials</em> API.
 *
 */
public class X509AuthProvider extends CredentialsApiAuthProvider<SubjectDnCredentials> {

    private final ServiceConfigProperties config;

    /**
     * Creates a new provider for a given configuration.
     * 
     * @param credentialsClientFactory The factory to use for creating a Credentials service client.
     * @param config The configuration.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    @Autowired
    public X509AuthProvider(final CredentialsClientFactory credentialsClientFactory, final ServiceConfigProperties config, final Tracer tracer) {
        super(credentialsClientFactory, tracer);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Creates a {@link SubjectDnCredentials} instance from information provided by a
     * device in its client (X.509) certificate.
     * <p>
     * The JSON object passed in is required to contain a <em>subject-dn</em> property.
     * If the <em>singleTenant</em> service config property is {@code false}, then the
     * object is also required to contain a <em>tenant-id</em> property, otherwise
     * the default tenant is used.
     * 
     * @param authInfo The authentication information provided by the device.
     * @return The credentials or {@code null} if the authentication information
     *         does not contain a tenant ID and subject DN.
     * @throws NullPointerException if the authentication info is {@code null}.
     */
    @Override
    protected SubjectDnCredentials getCredentials(final JsonObject authInfo) {

        Objects.requireNonNull(authInfo);
        try {
            final String tenantId = Optional.ofNullable(authInfo.getString(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID))
                    .orElseGet(() -> {
                        if (config.isSingleTenant()) {
                            return Constants.DEFAULT_TENANT;
                        } else {
                            return null;
                        }
                    });
            final String subjectDn = authInfo.getString(CredentialsConstants.FIELD_PAYLOAD_SUBJECT_DN);
            if (tenantId == null || subjectDn == null) {
                return null;
            } else {
                return SubjectDnCredentials.create(tenantId, subjectDn);
            }
        } catch (ClassCastException e) {
            return null;
        }
    }

    @Override
    protected Future<Device> doValidateCredentials(
            final SubjectDnCredentials deviceCredentials,
            final CredentialsObject credentialsOnRecord) {
        return Future.succeededFuture(new Device(deviceCredentials.getTenantId(), credentialsOnRecord.getDeviceId()));
    }
}
