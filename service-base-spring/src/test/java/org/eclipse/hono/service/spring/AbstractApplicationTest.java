/**
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.spring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.AbstractServiceBase;
import org.eclipse.hono.service.spring.AbstractApplication;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Verifies behavior of {@link AbstractApplication}.
 *
 */
@ExtendWith(VertxExtension.class)
class AbstractApplicationTest {

    private Vertx vertx;
    private AbstractApplication application;

    @BeforeEach
    public void setUp() {
        vertx = mock(Vertx.class);
        doAnswer(invocation -> {
            final Handler<AsyncResult<String>> resultHandler = invocation.getArgument(1);
            resultHandler.handle(Future.succeededFuture("id"));
            return null;
        }).when(vertx).deployVerticle(any(Verticle.class), VertxMockSupport.anyHandler());
        application = new AbstractApplication() {
        };
        application.setVertx(vertx);
    }

    private AbstractServiceBase<ServiceConfigProperties> newServiceInstance() {
        return new AbstractServiceBase<>() {

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                // TODO Auto-generated method stub
            }

            @Override
            public int getPortDefaultValue() {
                return 0;
            }

            @Override
            public int getInsecurePortDefaultValue() {
                return 0;
            }

            @Override
            protected int getActualPort() {
                return 0;
            }

            @Override
            protected int getActualInsecurePort() {
                return 0;
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDeploySupportsMultipleServiceInstances(final VertxTestContext ctx) {

        final ObjectFactory<AbstractServiceBase<ServiceConfigProperties>> factory = mock(ObjectFactory.class);
        when(factory.getObject()).thenReturn(newServiceInstance());

        final ApplicationConfigProperties props = new ApplicationConfigProperties();
        props.setMaxInstances(2);
        application.setApplicationConfiguration(props);
        application.addServiceFactories(Set.of(factory));
        application.deployVerticles()
        .onComplete(ctx.succeeding(ok -> {
            verify(factory, times(2)).getObject();
            verify(vertx, times(2)).deployVerticle(any(Verticle.class), VertxMockSupport.anyHandler());
            ctx.completeNow();
        }));
    }

}
