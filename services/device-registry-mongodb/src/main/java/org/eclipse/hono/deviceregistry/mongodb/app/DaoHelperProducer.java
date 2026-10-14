/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.deviceregistry.mongodb.app;

import java.io.FileInputStream;
import java.util.Base64;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigOptions;
import org.eclipse.hono.deviceregistry.util.CryptVaultBasedFieldLevelEncryption;
import org.eclipse.hono.deviceregistry.util.FieldLevelEncryption;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import com.bol.config.CryptVaultAutoConfiguration;
import com.bol.config.CryptVaultAutoConfiguration.CryptVaultConfigurationProperties;
import com.bol.crypt.CryptVault;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * A producer of helpers needed for the registry's Data Access Objects.
 */
@ApplicationScoped
public class DaoHelperProducer {

    /**
     * Creates the field level encryption helper for the Credential DAO.
     *
     * The helper is used to encrypt/decrypt PSK type credentials.
     *
     * @param options The credentials service options.
     * @return The field level encryption helper. The helper will be a no-op if no encryption key file is specified in
     *         the options.
     */
    @Produces
    @Singleton
    public FieldLevelEncryption fieldLevelEncryption(final MongoDbBasedCredentialsConfigOptions options) {
        return options.encryptionKeyFile()
                .map(this::fieldLevelEncryption)
                .orElse(FieldLevelEncryption.NOOP_ENCRYPTION);
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = """
            The path that the CryptVault properties are read from is determined from configuration properties that
            are supposed to be passed in during startup of the component only.
            """)
    private FieldLevelEncryption fieldLevelEncryption(final String path) {
        try (FileInputStream in = new FileInputStream(path)) {
            final Yaml yaml = new Yaml(
                    new Constructor(CryptVaultConfigurationProperties.class, new LoaderOptions()));
            final CryptVaultConfigurationProperties config = yaml.load(in);
            final CryptVault cryptVault = new CryptVault();
            for (CryptVaultAutoConfiguration.Key key : config.getKeys()) {
                final byte[] secretKeyBytes = Base64.getDecoder().decode(key.getKey());
                cryptVault.with256BitAesCbcPkcs5PaddingAnd128BitSaltKey(key.getVersion(),
                        secretKeyBytes);
            }

            Optional.ofNullable(config.getDefaultKey()).ifPresent(cryptVault::withDefaultKeyVersion);
            return new CryptVaultBasedFieldLevelEncryption(cryptVault);
        } catch (final Exception e) {
            throw new IllegalArgumentException(
                    String.format("error reading CryptVault configuration from file [%s]", path),
                    e);
        }
    }
}
