package io.vertx.proton;

import io.vertx.core.net.NetClientOptions;

public class ProtonClientOptions extends NetClientOptions {

    private String[] allowedSaslMechanisms = null;

    /**
     * Get the mechanisms the client is currently restricted to use.
     *
     * @return the mechanisms, or null if there is no restriction in place
     */
    public String[] getAllowedSaslMechanisms() {
        return allowedSaslMechanisms;
    }

    /**
     * Set a restricted mechanism(s) that the client may use during the
     * SASL negotiation. If null or empty argument is given, no restriction
     * is applied and any supported mechanism can be used.
     *
     * @param mechanisms the restricted mechanism(s) or null to clear the restriction.
     */
    public void setAllowedSaslMechanisms(final String... saslMechanisms) {
        if(saslMechanisms == null || saslMechanisms.length == 0) {
            this.allowedSaslMechanisms = null;
        } else {
            this.allowedSaslMechanisms = saslMechanisms;
        }
    }

    //TODO: Use a delegate? Override methods to change return type?
    //TODO: Config for AMQP levle heartbeating /idle-timeout? Have
    //      that on the Connection instead?
}
