/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */
package org.eclipse.hono.util;

import java.lang.reflect.Method;

import org.apache.qpid.proton.engine.Link;

import io.vertx.proton.ProtonLink;
import io.vertx.proton.impl.ProtonReceiverImpl;
import io.vertx.proton.impl.ProtonSenderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for working with Proton {@code Link}s.
 */
public final class LinkHelper {

    private static final Logger LOG = LoggerFactory.getLogger(LinkHelper.class);

    private LinkHelper() {
    }

    /**
     * Frees resources concerning the given link.
     * <p>
     * Note: this method will become obsolete with the availability of ProtonLink#free() (see vertx-proton#83).
     *
     * @param protonLink the link to free resources for.
     */
    public static void freeLinkResources(final ProtonLink<?> protonLink) {
        if (protonLink != null) {
            try {
                final Link link;
                if (protonLink instanceof ProtonReceiverImpl) {
                    final Method getReceiverMethod = ProtonReceiverImpl.class.getDeclaredMethod("getReceiver");
                    getReceiverMethod.setAccessible(true);
                    link = (Link) getReceiverMethod.invoke(protonLink);
                } else if (protonLink instanceof ProtonSenderImpl) {
                    final Method getSenderMethod = ProtonSenderImpl.class.getDeclaredMethod("sender");
                    getSenderMethod.setAccessible(true);
                    link = (Link) getSenderMethod.invoke(protonLink);
                } else {
                    throw new IllegalArgumentException("unknown ProtonLink class: " + protonLink.getClass());
                }
                link.free();
                LOG.trace("freed link resources");
            } catch (final Exception e) {
                LOG.error("error freeing link resources", e);
            }
        }
    }
}
