package io.vertx.proton.impl;

import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sasl.SaslOutcome;
import org.apache.qpid.proton.engine.Transport;

import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.sasl.impl.ProtonSaslAnonymousImpl;

/**
 * Manage the SASL authentication process
 */
public class ProtonSaslServerAuthenticatorImpl implements ProtonSaslAuthenticator {

    private Sasl sasl;
    private Handler<ProtonConnection> handler;
    private ProtonConnectionImpl connection;

    public ProtonSaslServerAuthenticatorImpl(Handler<ProtonConnection> handler, ProtonConnectionImpl connection) {
        this.handler = handler;
        this.connection = connection;
    }

    @Override
    public void init(Transport transport) {
        this.sasl = transport.sasl();
        sasl.server();
        sasl.allowSkip(false);
        sasl.setMechanisms(ProtonSaslAnonymousImpl.MECH_NAME);
    }

    @Override
    public boolean process() {
        if(sasl == null) {
            throw new IllegalStateException("Init was not called with the associated transport");
        }

        String[] remoteMechanisms = sasl.getRemoteMechanisms();
        if (remoteMechanisms.length > 0) {
            String chosen = remoteMechanisms[0];
            if(ProtonSaslAnonymousImpl.MECH_NAME.equals(chosen)) {
                sasl.done(SaslOutcome.PN_SASL_OK);
                handler.handle(connection);
                return true;
            } else {
                sasl.done(SaslOutcome.PN_SASL_AUTH);
            }
        }

        return false;
    }
}
