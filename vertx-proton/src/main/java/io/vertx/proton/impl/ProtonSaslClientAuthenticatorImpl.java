package io.vertx.proton.impl;

import java.util.HashSet;
import java.util.Set;

import javax.security.sasl.SaslException;


import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Transport;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.NetSocket;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.sasl.ProtonSaslMechanism;
import io.vertx.proton.sasl.impl.ProtonSaslMechanismFinderImpl;

/**
 * Manage the SASL authentication process
 */
public class ProtonSaslClientAuthenticatorImpl implements ProtonSaslAuthenticator {

    private Sasl sasl;
    private final String username;
    private final String password;
    private ProtonSaslMechanism mechanism;
    private Set<String> mechanismsRestriction;
    private Handler<AsyncResult<ProtonConnection>> handler;
    private NetSocket socket;
    private ProtonConnectionImpl connection;

    /**
     * Create the authenticator and initialize it.
     *
     * @param username
     *        The username provide credentials to the remote peer, or null if there is none.
     * @param password
     *        The password provide credentials to the remote peer, or null if there is none.
     * @param allowedSaslMechanisms
     *        The possible mechanism(s) to which the client should restrict its
     *        mechanism selection to if offered by the server, or null if no restriction.
     * @param socket
     *        The socket associated with the connection this SASL process is for
     * @param handler
     *        The handler to convey the result of the SASL process to
     * @param connection
     *        The connection the SASL process is for
     */
    public ProtonSaslClientAuthenticatorImpl(String username, String password, String[] allowedSaslMechanisms,
                                             NetSocket socket, Handler<AsyncResult<ProtonConnection>> handler, ProtonConnectionImpl connection) {
        this.handler = handler;
        this.socket = socket;
        this.connection = connection;
        this.username = username;
        this.password = password;
        if(allowedSaslMechanisms != null) {
            Set<String> mechs = new HashSet<String>();
            for(int i = 0; i < allowedSaslMechanisms.length; i++) {
                String mech = allowedSaslMechanisms[i];
                if(!mech.trim().isEmpty()) {
                    mechs.add(mech);
                }
            }

            if(!mechs.isEmpty()) {
                this.mechanismsRestriction = mechs;
            }
        }
    }

    @Override
    public void init(Transport transport) {
        this.sasl = transport.sasl();
        sasl.client();
    }

    @Override
    public boolean process() {
        if(sasl == null) {
            throw new IllegalStateException("Init was not called with the associated transport");
        }

        boolean done = false;

        try {
            switch (sasl.getState()) {
                case PN_SASL_IDLE:
                    handleSaslInit();
                    break;
                case PN_SASL_STEP:
                    handleSaslStep();
                    break;
                case PN_SASL_FAIL:
                    done = true;
                    handler.handle(Future.failedFuture(new SecurityException("Failed to authenticate")));
                    break;
                case PN_SASL_PASS:
                    done = true;
                    handler.handle(Future.succeededFuture(connection));
                    break;
                default:
            }
        } catch (Exception e) {
            done = true;
            try {
                if(socket != null) {
                    socket.close();
                }
            } finally {
                handler.handle(Future.failedFuture(e));
            }
        }

        return done;
    }

    private void handleSaslInit() throws SecurityException {
        try {
            String[] remoteMechanisms = sasl.getRemoteMechanisms();
            if (remoteMechanisms != null && remoteMechanisms.length != 0) {
                mechanism = ProtonSaslMechanismFinderImpl.findMatchingMechanism(username, password, mechanismsRestriction, remoteMechanisms);
                if (mechanism != null) {
                    mechanism.setUsername(username);
                    mechanism.setPassword(password);

                    sasl.setMechanisms(mechanism.getName());
                    byte[] response = mechanism.getInitialResponse();
                    if (response != null) {
                        sasl.send(response, 0, response.length);
                    }
                } else {
                    throw new SecurityException("Could not find a suitable SASL mechanism for the remote peer using the available credentials.");
                }
            }
        } catch (SaslException se) {
            SecurityException sece = new SecurityException("Exception while processing SASL init.", se);
            throw sece;
        }
    }

    private void handleSaslStep() throws SecurityException {
        try {
            if (sasl.pending() != 0) {
                byte[] challenge = new byte[sasl.pending()];
                sasl.recv(challenge, 0, challenge.length);
                byte[] response = mechanism.getChallengeResponse(challenge);
                sasl.send(response, 0, response.length);
            }
        } catch (SaslException se) {
            SecurityException sece = new SecurityException("Exception while processing SASL step.", se);
            throw sece;
        }
    }
}
