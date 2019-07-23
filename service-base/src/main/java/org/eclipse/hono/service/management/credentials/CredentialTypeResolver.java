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

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;

/**
 * Type resolver for credentials.
 * <p>
 * This type resolver knows the types Password, PSK and X509 Certificate. All other credentials are encoded in a
 * {@link CommonCredential}.
 */
public class CredentialTypeResolver extends TypeIdResolverBase {

    private JavaType baseType;

    @Override
    public void init(final JavaType baseType) {
        this.baseType = baseType;
    }

    @Override
    public Id getMechanism() {
        return Id.NAME;
    }

    @Override
    public String idFromValue(final Object obj) {
        return idFromValueAndType(obj, obj.getClass());
    }

    @Override
    public String idFromValueAndType(final Object obj, final Class<?> subType) {
        if (obj instanceof GenericCredential) {
            return ((GenericCredential) obj).getType();
        } else if (obj instanceof PasswordCredential) {
            return RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD;
        } else if (obj instanceof PskCredential) {
            return RegistryManagementConstants.SECRETS_TYPE_PRESHARED_KEY;
        } else if (obj instanceof X509CertificateCredential) {
            return RegistryManagementConstants.SECRETS_TYPE_X509_CERT;
        }
        return null;
    }

    @Override
    public JavaType typeFromId(final DatabindContext context, final String id) {
        switch (id) {
        case RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD:
            return context.constructSpecializedType(this.baseType, PasswordCredential.class);
        case RegistryManagementConstants.SECRETS_TYPE_PRESHARED_KEY:
            return context.constructSpecializedType(this.baseType, PskCredential.class);
        case RegistryManagementConstants.SECRETS_TYPE_X509_CERT:
            return context.constructSpecializedType(this.baseType, X509CertificateCredential.class);
        default:
            return context.constructSpecializedType(this.baseType, GenericCredential.class);
        }
    }
}
