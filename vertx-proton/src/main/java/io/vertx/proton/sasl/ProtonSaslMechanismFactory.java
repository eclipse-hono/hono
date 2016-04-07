package io.vertx.proton.sasl;

public interface ProtonSaslMechanismFactory {

    /**
     * Creates an instance of the authentication mechanism implementation.
     *
     * @return a new mechanism instance.
     */
    ProtonSaslMechanism createMechanism();

    /**
     * Get the name of the mechanism supported by the factory
     *
     * @return the name of the mechanism
     */
    String getMechanismName();
}
