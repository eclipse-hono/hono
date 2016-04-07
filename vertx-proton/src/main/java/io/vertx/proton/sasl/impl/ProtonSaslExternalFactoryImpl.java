package io.vertx.proton.sasl.impl;

import io.vertx.proton.sasl.ProtonSaslMechanism;
import io.vertx.proton.sasl.ProtonSaslMechanismFactory;

public class ProtonSaslExternalFactoryImpl implements ProtonSaslMechanismFactory {

    @Override
    public ProtonSaslMechanism createMechanism() {
        return new ProtonSaslExternalImpl();
    }

    @Override
    public String getMechanismName() {
        return ProtonSaslExternalImpl.MECH_NAME;
    }
}
