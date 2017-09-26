/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.deviceregistry;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * FileBasedCredentialsServiceTest
 *
 */
@RunWith(VertxUnitRunner.class)
public class FileBasedCredentialsServiceTest {

    Vertx vertx;
    Context context;

    /**
     * Sets up fixture.
     */
    @Before
    public void setUp() {
        vertx = Vertx.vertx();
    }

    /**
     * Verifies that credentials are successfully loaded from file.
     *  
     * @param ctx The test context.
     */
    @Test()
    public void testLoadCredentials(final TestContext ctx) {

        FileBasedCredentialsConfigProperties config = new FileBasedCredentialsConfigProperties();
        config.setCredentialsFilename("credentials.json");
        FileBasedCredentialsService svc = new FileBasedCredentialsService();
        svc.setConfig(config);

        vertx.deployVerticle(svc, ctx.asyncAssertSuccess(s -> {
            svc.getCredentials(Constants.DEFAULT_TENANT, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "sensor1",
                    ctx.asyncAssertSuccess(creds -> {
                        assertThat(creds.getStatus(), is(200));
                    }));
        }));
    }

}
