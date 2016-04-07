package io.vertx.proton.impl;

import org.apache.qpid.proton.engine.Transport;

public interface ProtonSaslAuthenticator {

    void init(Transport transport);

    /**
     * Process the SASL authentication cycle until such time as an outcome is determined. This
     * should be called by the managing entity until the return value is true indicating a
     * the handshake completed (successfully or otherwise).
     *
     * @return true if the SASL handshake completed.
     */
    boolean process();
}