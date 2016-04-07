package io.vertx.proton.sasl.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.proton.sasl.ProtonSaslMechanism;
import io.vertx.proton.sasl.ProtonSaslMechanismFactory;

public class ProtonSaslMechanismFinderImpl {

    private static Logger LOG = LoggerFactory.getLogger(ProtonSaslMechanismFinderImpl.class);

    /**
     * Attempts to find a matching Mechanism implementation given a list of supported
     * mechanisms from a remote peer.  Can return null if no matching Mechanisms are
     * found.
     *
     * @param username
     *        the username, or null if there is none
     * @param password
     *        the password, or null if there is none
     * @param mechRestrictions
     *        The possible mechanism(s) to which the client should restrict its
     *        mechanism selection to if offered by the server, or null if there
     *        is no restriction
     * @param remoteMechanisms
     *        list of mechanism names that are supported by the remote peer.
     *
     * @return the best matching Mechanism for the supported remote set.
     */
    public static ProtonSaslMechanism findMatchingMechanism(String username, String password, Set<String> mechRestrictions, String... remoteMechanisms) {

        ProtonSaslMechanism match = null;
        List<ProtonSaslMechanism> found = new ArrayList<ProtonSaslMechanism>();

        for (String remoteMechanism : remoteMechanisms) {
            ProtonSaslMechanismFactory factory = findMechanismFactory(remoteMechanism);
            if (factory != null) {
                ProtonSaslMechanism mech = factory.createMechanism();
                if(mechRestrictions != null && !mechRestrictions.contains(remoteMechanism)) {
                    LOG.trace("Skipping {0} mechanism because it is not in the configured mechanisms restriction set", remoteMechanism);
                } else if(mech.isApplicable(username, password)) {
                    found.add(mech);
                } else {
                    LOG.trace("Skipping {0} mechanism because the available credentials are not sufficient", mech);
                }
            }
        }

        if (!found.isEmpty()) {
            // Sorts by priority using mechanism comparison and return the last value in
            // list which is the mechanism deemed to be the highest priority match.
            Collections.sort(found);
            match = found.get(found.size() - 1);
        }

        LOG.trace("Best match for SASL auth was: {0}", match);

        return match;
    }

    /**
     * Searches for a mechanism factory by using the scheme from the given name.
     *
     * @param name
     *        The name of the authentication mechanism to search for.
     *
     * @return a mechanism factory instance matching the name, or null if none was created.
     */
    protected static ProtonSaslMechanismFactory findMechanismFactory(String name) {
        if (name == null || name.isEmpty()) {
            LOG.warn("No SASL mechanism name was specified");
            return null;
        }

        ProtonSaslMechanismFactory factory = null;

        //TODO: make it pluggable?
        if (ProtonSaslPlainImpl.MECH_NAME.equals(name)) {
            factory = new ProtonSaslPlainFactoryImpl();
        } else if (ProtonSaslAnonymousImpl.MECH_NAME.equals(name)) {
            factory = new ProtonSaslAnonymousFactoryImpl();
        } else if (ProtonSaslExternalImpl.MECH_NAME.equals(name)) {
            factory = new ProtonSaslExternalFactoryImpl();
        }

        return factory;
    }
}
