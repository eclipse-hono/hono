/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora.providers;

import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;

/**
 * Verifies the behavior of {@link KerlinkProviderCustomContentType}.
 */
@ExtendWith(VertxExtension.class)
public class KerlinkProviderCustomContentTypeTest extends LoraProviderTestBase<KerlinkProviderCustomContentType> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected KerlinkProviderCustomContentType newProvider() {
        return new KerlinkProviderCustomContentType();
    }
}
