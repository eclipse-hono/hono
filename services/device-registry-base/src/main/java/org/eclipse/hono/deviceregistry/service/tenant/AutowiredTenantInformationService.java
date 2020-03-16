/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.service.tenant;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.tenant.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * A default implementation of {@link TenantInformationService} that uses embedded {@link TenantService} to verify if tenant exists.
 */
@Component
public class AutowiredTenantInformationService implements TenantInformationService {

    TenantService service;

    @Override
    public Future<TenantKey> tenantExists(final String tenantId, final Span span) {
        return service.get(tenantId, span)
                .compose(result -> {
                    if (result.isOk()) {
                        return Future.succeededFuture(TenantKey.from(tenantId, tenantId));
                    } else {
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "Tenant does not exist"));
                    }
                });
    }

    @Autowired
    @Qualifier("backend")
    public void setService(final TenantService service) {
        this.service = service;
    }

}
