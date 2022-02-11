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


package org.eclipse.hono.deviceconnection.infinispan.client;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A dummy class for registering third party classes for reflection with Quarkus.
 *
 */
@RegisterForReflection(
        targets = {
            io.netty.channel.socket.nio.NioSocketChannel.class,
            org.infinispan.CoreModuleImpl.class,
            org.infinispan.client.hotrod.impl.async.DefaultAsyncExecutorFactory.class,
            org.infinispan.client.hotrod.impl.consistenthash.SegmentConsistentHash.class,
            org.infinispan.configuration.parsing.Parser.class,
            org.infinispan.configuration.parsing.SFSToSIFSConfigurationBuilder.class,
            org.infinispan.distribution.ch.impl.HashFunctionPartitioner.class,
            org.infinispan.persistence.file.SingleFileStore.class,
            org.infinispan.protostream.types.java.CommonContainerTypesSchema.class,
            org.infinispan.protostream.types.java.CommonTypesSchema.class,
            org.infinispan.protostream.types.protobuf.AnySchemaImpl.class,
            org.infinispan.protostream.types.protobuf.DurationSchemaImpl.class,
            org.infinispan.protostream.types.protobuf.EmptySchemaImpl.class,
            org.infinispan.protostream.types.protobuf.TimestampSchemaImpl.class,
            org.infinispan.protostream.types.protobuf.WrappersSchemaImpl.class,
            org.wildfly.security.password.impl.DigestPasswordAlgorithmParametersSpiImpl.class,
            org.wildfly.security.password.impl.IteratedSaltedPasswordAlgorithmParametersSpiImpl.class,
            org.wildfly.security.password.impl.OneTimePasswordAlgorithmParametersSpiImpl.class,
            org.wildfly.security.password.impl.SaltedPasswordAlgorithmParametersSpiImpl.class,
            org.wildfly.security.password.impl.PasswordFactorySpiImpl.class,
            org.wildfly.security.sasl.digest.DigestClientFactory.class,
            org.wildfly.security.sasl.digest.WildFlyElytronSaslDigestProvider.class,
            org.wildfly.security.sasl.external.ExternalSaslClientFactory.class,
            org.wildfly.security.sasl.external.WildFlyElytronSaslExternalProvider.class,
            org.wildfly.security.sasl.gs2.Gs2SaslClientFactory.class,
            org.wildfly.security.sasl.gs2.WildFlyElytronSaslGs2Provider.class,
            org.wildfly.security.sasl.gssapi.GssapiClientFactory.class,
            org.wildfly.security.sasl.gssapi.WildFlyElytronSaslGssapiProvider.class,
            org.wildfly.security.sasl.oauth2.OAuth2SaslClientFactory.class,
            org.wildfly.security.sasl.oauth2.WildFlyElytronSaslOAuth2Provider.class,
            org.wildfly.security.sasl.plain.PlainSaslClientFactory.class,
            org.wildfly.security.sasl.plain.WildFlyElytronSaslPlainProvider.class,
            org.wildfly.security.sasl.scram.ScramSaslClientFactory.class,
            org.wildfly.security.sasl.scram.WildFlyElytronSaslScramProvider.class
        },
        classNames = {
            "java.util.Arrays$ArrayList",
            "java.util.Collections$UnmodifiableRandomAccessList",
            "java.util.Collections$EmptyList",
            "java.util.Collections$EmptyMap",
            "java.util.Collections$EmptySet",
            "java.util.Collections$SingletonList",
            "java.util.Collections$SingletonMap",
            "java.util.Collections$SingletonSet",
            "java.util.Collections$SynchronizedSet",
            "java.util.Collections$UnmodifiableSet"
        })
class QuarkusReflectionRegistration {

    private QuarkusReflectionRegistration() {
        // prevent instantiation
    }
}
