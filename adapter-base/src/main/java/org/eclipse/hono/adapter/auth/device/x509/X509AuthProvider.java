/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import org.eclipse.hono.adapter.auth.device.AuthHandler;
import org.eclipse.hono.adapter.auth.device.CredentialsApiAuthProvider;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.RequestResponseApiConstants;

import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;


/**
 * An authentication provider that verifies an X.509 certificate using
 * Hono's <em>Credentials</em> API.
 *
 */
public class X509AuthProvider extends CredentialsApiAuthProvider<SubjectDnCredentials> {

    /**
     * Creates a new provider for a given configuration.
     *
     * @param credentialsClient The client to use for accessing the Credentials service.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public X509AuthProvider(final CredentialsClient credentialsClient, final Tracer tracer) {
        super(credentialsClient, tracer);
    }

    /**
     * Creates a {@link SubjectDnCredentials} instance from information provided by a
     * device in its client (X.509) certificate.
     * <p>
     * The JSON object passed in is required to contain a <em>subject-dn</em> and a
     * <em>tenant-id</em> property.
     * <p>
     * If the JSON object does not contain an <em>auth-id-template</em> property, the value of
     * the <em>subject-dn</em> property is used as the authentication identifier of the returned
     * credentials object. Otherwise, the authentication identifier is generated using
     * the <em>auth-id-template</em>.
     * <p>
     * Any additional properties that might be present in the JSON object
     * are copied into the client context of the returned credentials.
     *
     * @param authInfo The credentials provided by the device. These usually get assembled via
     *            {@link AuthHandler#parseCredentials(org.eclipse.hono.util.ExecutionContext)}.
     * @return The credentials or {@code null} if the authentication information
     *         does not contain a tenant ID and subject DN.
     * @throws NullPointerException if the authentication info is {@code null}.
     */
    @Override
    public SubjectDnCredentials getCredentials(final JsonObject authInfo) {

        Objects.requireNonNull(authInfo);
        try {
            final String tenantId = authInfo.getString(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID);
            final String subjectDn = authInfo.getString(RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN);
            final String authIdTemplate = authInfo.getString(RequestResponseApiConstants.FIELD_PAYLOAD_AUTH_ID_TEMPLATE);
            if (tenantId == null || subjectDn == null) {
                return null;
            } else {
                final JsonObject clientContext = authInfo.copy();
                // credentials object already contains tenant ID and subject DN, so remove them from the client context
                clientContext.remove(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID);
                clientContext.remove(RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN);
                clientContext.remove(RequestResponseApiConstants.FIELD_PAYLOAD_AUTH_ID_TEMPLATE);
                return SubjectDnCredentials.create(tenantId, subjectDn, authIdTemplate, clientContext);
            }
        } catch (final ClassCastException | IllegalArgumentException e) {
            log.warn("Reading authInfo failed", e);
            return null;
        }
    }

    @Override
    protected Future<DeviceUser> doValidateCredentials(
            final SubjectDnCredentials deviceCredentials,
            final CredentialsObject credentialsOnRecord) {
        return Future.succeededFuture(new DeviceUser(deviceCredentials.getTenantId(), credentialsOnRecord.getDeviceId()));
    }
}
