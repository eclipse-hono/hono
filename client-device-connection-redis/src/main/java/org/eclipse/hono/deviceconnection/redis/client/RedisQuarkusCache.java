/*
TODO
 */

package org.eclipse.hono.deviceconnection.redis.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.hono.deviceconnection.common.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.transactions.OptimisticLockingTransactionResult;
import io.quarkus.redis.datasource.transactions.ReactiveTransactionalRedisDataSource;
import io.quarkus.redis.datasource.transactions.TransactionResult;
import io.quarkus.redis.datasource.value.ReactiveTransactionalValueCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * An implementation of the Redis device connection class using the Quarkus Redis client library.
 */
//@javax.enterprise.context.ApplicationScoped
public class RedisQuarkusCache implements Cache<String, String> {
    private static final Logger LOG = LoggerFactory.getLogger(RedisCache.class);

    //private final RedisAPI redisApi;
    private final ReactiveRedisDataSource reactiveRedisDataSource;
    private final ReactiveValueCommands<String, String> valueCommands;

    /**
     * TODO.
     * @param reactiveRedisDataSource TODO
     */
    public RedisQuarkusCache(final ReactiveRedisDataSource reactiveRedisDataSource) {
        //this.redisApi = redisApi;
        this.reactiveRedisDataSource = reactiveRedisDataSource;
        valueCommands = reactiveRedisDataSource.value(String.class, String.class);
    }


    @Override
    public Future<JsonObject> checkForCacheAvailability() {
        LOG.info("QREDIS: checking for cache availability");
        return Future.fromCompletionStage(
                valueCommands.set("TEST_KEY", "TEST_VALUE")
                .onItem().transform(setResult -> valueCommands.get("TEST_KEY"))
                .onItem().transform(getResult -> new JsonObject())
                        .subscribeAsCompletionStage());
    }

    @Override
    public Future<Void> put(final String key, final String value) {
        LOG.info("QREDIS: put {}={}", key, value);
        return Future.fromCompletionStage(valueCommands.set(key, value).subscribeAsCompletionStage());
    }

    @Override
    public Future<Void> put(final String key, final String value, final long lifespan, final TimeUnit lifespanUnit) {
        LOG.info("QREDIS: put {}={} ({} {})", key, value, lifespan, lifespanUnit);
        final long millis = lifespanUnit.toMillis(lifespan);
        return Future.fromCompletionStage(valueCommands.set(key, value, new SetArgs().px(millis)).subscribeAsCompletionStage());
    }

    @Override
    public Future<Void> putAll(final Map<? extends String, ? extends String> data) {
        LOG.info("QREDIS: putAll ({})", data.size());
        return Future.fromCompletionStage(valueCommands.mset((Map<String, String>) data).subscribeAsCompletionStage());
    }

    @Override
    public Future<Void> putAll(final Map<? extends String, ? extends String> data, final long lifespan, final TimeUnit lifespanUnit) {
        LOG.info("QREDIS: putAll ({}) ({} {})", data.size(), lifespan, lifespanUnit);
        final long millis = lifespanUnit.toMillis(lifespan);
        final Function<ReactiveTransactionalRedisDataSource, Uni<Void>> txBlock = ds -> {
            final ReactiveTransactionalValueCommands<String, String> txValueCommends = ds.value(String.class, String.class);
            final List<Uni<Void>> unis = new ArrayList<>();
            data.forEach((k, v) -> {
                unis.add(txValueCommends.set(k, v, new SetArgs().px(millis)));
            });
            return Uni.join().all(unis).andFailFast().replaceWithVoid();
        };
        final Uni<TransactionResult> result = reactiveRedisDataSource.withTransaction(txBlock);
        return Future.fromCompletionStage(result.onItem().transformToUni(txResult -> Uni.createFrom().voidItem()).subscribeAsCompletionStage());
    }

    @Override
    public Future<String> get(final String key) {
        LOG.info("QREDIS: get {}", key);
        return Future.fromCompletionStage(valueCommands.get(key).subscribeAsCompletionStage());
    }

    @Override
    public Future<Boolean> remove(final String key, final String value) {
        LOG.info("QREDIS: remove {}={}", key, value);
        final Function<ReactiveRedisDataSource, Uni<String>> preTxBlock = ds -> {
            return ds.value(String.class, String.class).get(key);
        };
        final BiFunction<String, ReactiveTransactionalRedisDataSource, Uni<Void>> txBlock = (redisKeyValue, ds) -> {
            if (value.equals(redisKeyValue)) {
                return ds.key(String.class).del(key);
            } else {
                return ds.discard();
            }
        };
        final Uni<OptimisticLockingTransactionResult<String>> result = reactiveRedisDataSource.withTransaction(preTxBlock, txBlock, key);
        return Future.fromCompletionStage(
                result.onItem().transformToUni(txResult -> Uni.createFrom().item(!txResult.isEmpty())).subscribeAsCompletionStage());
    }

    @Override
    public Future<Map<String, String>> getAll(final Set<? extends String> keys) {
        LOG.info("QREDIS: getAll {}", keys.size());
        return Future.fromCompletionStage(
                valueCommands.mget(keys.toArray(new String[0]))
                        .onItem().transform(result -> {
                            result.values().removeIf(Objects::isNull);
                            return result;
                        })
                        .subscribeAsCompletionStage());
    }
}
