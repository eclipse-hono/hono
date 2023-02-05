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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Type resolver for credentials.
 * <p>
 * This type resolver knows the types Password, PSK, Raw Public Key and X509 Certificate. All other credentials are
 * encoded in a {@link CommonCredential}.
 */
@RegisterForReflection
public final class CredentialTypeResolver extends TypeIdResolverBase {

    private JavaType baseType;

    /**
     * Creates a resolver.
     */
    public CredentialTypeResolver() {
        super();
    }

    @Override
    public void init(final JavaType baseType) {
        this.baseType = baseType;
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.NAME;
    }

    @Override
    public String idFromValue(final Object obj) {
        return idFromValueAndType(obj, obj.getClass());
    }

    @Override
    public String idFromValueAndType(final Object obj, final Class<?> subType) {

        if (obj instanceof CommonCredential) {
            return ((CommonCredential) obj).getType();
        }
        return null;
    }

    @Override
    public JavaType typeFromId(final DatabindContext context, final String id) {
        switch (id) {
        case RpkCredential.TYPE:
            return context.constructSpecializedType(this.baseType, RpkCredential.class);
        case PasswordCredential.TYPE:
            return context.constructSpecializedType(this.baseType, PasswordCredential.class);
        case PskCredential.TYPE:
            return context.constructSpecializedType(this.baseType, PskCredential.class);
        case X509CertificateCredential.TYPE:
            return context.constructSpecializedType(this.baseType, X509CertificateCredential.class);
        default:
            return context.constructSpecializedType(this.baseType, GenericCredential.class);
        }
    }
}
