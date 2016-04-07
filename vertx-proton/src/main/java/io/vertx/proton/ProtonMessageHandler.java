/**
 * Copyright 2015 Red Hat, Inc.
 */
package io.vertx.proton;

import org.apache.qpid.proton.message.Message;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface ProtonMessageHandler {
    void handle(ProtonDelivery delivery, Message message);
}
