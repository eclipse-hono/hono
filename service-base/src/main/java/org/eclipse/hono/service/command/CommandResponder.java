package org.eclipse.hono.service.command;

import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * TODO.
 */
class CommandResponder {

    private ProtonReceiver protonReceiver;
    private ProtonSender protonSender;

    CommandResponder(final ProtonReceiver receiver) {
        this.protonReceiver = receiver;
        this.protonReceiver.closeHandler(onClose -> {
            close();
        });
        // TODO: set time for autoclosing of sender?
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
        if(protonReceiver.isOpen()) {
            protonReceiver.close();
        }
        if (protonSender != null && protonSender.isOpen()) {
            protonSender.close();
        }
    }

}
