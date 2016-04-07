package io.vertx.proton.sasl.impl;

import io.vertx.proton.sasl.ProtonSaslMechanism;
import io.vertx.proton.sasl.ProtonSaslMechanismFactory;

public class ProtonSaslAnonymousFactoryImpl implements ProtonSaslMechanismFactory {

    @Override
    public ProtonSaslMechanism createMechanism() {
        return new ProtonSaslAnonymousImpl();
    }

    @Override
    public String getMechanismName() {
        return ProtonSaslAnonymousImpl.MECH_NAME;
    }
}
