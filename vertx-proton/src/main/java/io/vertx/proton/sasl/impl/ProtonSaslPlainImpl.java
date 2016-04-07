package io.vertx.proton.sasl.impl;

public class ProtonSaslPlainImpl extends ProtonSaslMechanismImpl {

    public static final String MECH_NAME = "PLAIN";

    @Override
    public int getPriority() {
        return PRIORITY.LOWER.getValue();
    }

    @Override
    public String getName() {
        return MECH_NAME;
    }

    @Override
    public byte[] getInitialResponse() {

        String username = getUsername();
        String password = getPassword();

        if (username == null) {
            username = "";
        }

        if (password == null) {
            password = "";
        }

        byte[] usernameBytes = username.getBytes();
        byte[] passwordBytes = password.getBytes();
        byte[] data = new byte[usernameBytes.length + passwordBytes.length + 2];
        System.arraycopy(usernameBytes, 0, data, 1, usernameBytes.length);
        System.arraycopy(passwordBytes, 0, data, 2 + usernameBytes.length, passwordBytes.length);
        return data;
    }

    @Override
    public byte[] getChallengeResponse(byte[] challenge) {
        return EMPTY;
    }

    @Override
    public boolean isApplicable(String username, String password) {
        return username != null && username.length() > 0 && password != null && password.length() > 0;
    }
}
