package io.vertx.proton.sasl.impl;

public class ProtonSaslAnonymousImpl extends ProtonSaslMechanismImpl {

    public static final String MECH_NAME = "ANONYMOUS";

    @Override
    public byte[] getInitialResponse() {
        return EMPTY;
    }

    @Override
    public byte[] getChallengeResponse(byte[] challenge) {
        return EMPTY;
    }

    @Override
    public int getPriority() {
        return PRIORITY.LOWEST.getValue();
    }

    @Override
    public String getName() {
        return MECH_NAME;
    }

    @Override
    public boolean isApplicable(String username, String password) {
        return true;
    }
}
