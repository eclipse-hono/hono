package io.vertx.proton.sasl.impl;

import io.vertx.proton.sasl.ProtonSaslMechanism;

public abstract class ProtonSaslMechanismImpl implements ProtonSaslMechanism {

    protected static final byte[] EMPTY = new byte[0];

    private String username;
    private String password;

    @Override
    public int compareTo(ProtonSaslMechanism other) {

        if (getPriority() < other.getPriority()) {
            return -1;
        } else if (getPriority() > other.getPriority()) {
            return 1;
        }

        return 0;
    }

    @Override
    public void setUsername(String value) {
        this.username = value;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setPassword(String value) {
        this.password = value;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String toString() {
        return "SASL-" + getName();
    }
}
