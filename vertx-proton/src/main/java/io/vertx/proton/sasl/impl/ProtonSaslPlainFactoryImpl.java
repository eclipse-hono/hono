package io.vertx.proton.sasl.impl;

import io.vertx.proton.sasl.ProtonSaslMechanism;
import io.vertx.proton.sasl.ProtonSaslMechanismFactory;

public class ProtonSaslPlainFactoryImpl implements ProtonSaslMechanismFactory {

    @Override
    public ProtonSaslMechanism createMechanism() {
        return new ProtonSaslPlainImpl();
    }

    @Override
    public String getMechanismName() {
        return ProtonSaslPlainImpl.MECH_NAME;
    }
}
