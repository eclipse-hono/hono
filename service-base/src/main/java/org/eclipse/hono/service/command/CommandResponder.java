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

package org.eclipse.hono.service.command;

import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A command responder with a receiver/sender pair.
 */
class CommandResponder {

    private ProtonReceiver protonReceiver;
    private ProtonSender protonSender;

    CommandResponder(final ProtonReceiver receiver) {
        this.protonReceiver = receiver;
        this.protonReceiver.closeHandler(onClose -> {
            close();
        });
    }

    ProtonSender getSender() {
        return protonSender;
    }

    void setSender(final ProtonSender protonSender) {
        this.protonSender = protonSender;
    }

    boolean isOpen() {
        return protonReceiver.isOpen();
    }

    void close() {
        if (protonReceiver.isOpen()) {
            protonReceiver.close();
        }
        if (protonSender != null && protonSender.isOpen()) {
            protonSender.close();
        }
    }

}
