/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.deviceregistry.mongodb.service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.mongodb.MongoException;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.mongo.AggregateOptions;
import io.vertx.ext.mongo.BulkOperation;
import io.vertx.ext.mongo.BulkWriteOptions;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientBulkWriteResult;
import io.vertx.ext.mongo.MongoClientDeleteResult;
import io.vertx.ext.mongo.MongoClientUpdateResult;
import io.vertx.ext.mongo.UpdateOptions;
import io.vertx.ext.mongo.WriteOption;

/**
 * A Mock implementation to the vertx mongoDbClient to ease the testing. Uses HashMaps to store the collections and
 * indexes for unique collection properties.
 */
class MongoDbClientMock implements MongoClient {

    /**
     * The error code from {@link com.mongodb.ErrorCategory} to signal creation of a duplicate.
     */
    private static final int DUPLICATE_KEY_ERROR_CODES = 11000;

    /**
     * The mocked internal MongoDb ObjectId.
     */
    final String OBJECT_ID_FIELD = "DummyObjectId";

    /**
     * The map to hold created collections.
     */
    private final Map<String, List<JsonObject>> mongoDbData = new HashMap<>();

    /**
     * The map to hold property names of unique fields for each collection.
     */
    private final Map<String, List<String>> mongoDbIndexes = new HashMap<>();

    /**
     * Default constructor for serialisation/deserialization.
     */
    MongoDbClientMock() {
    }

    @Override
    public MongoClient createIndexWithOptions(final String collection, final JsonObject key,
            final IndexOptions options, final Handler<AsyncResult<Void>> resultHandler) {
        // create new List for collection if not present
        if (this.mongoDbData.containsKey(collection)) {
            resultHandler.handle(Future.succeededFuture());
            return this;
        }
        this.mongoDbData.put(collection, new ArrayList<>());
        final ArrayList<String> indexes = new ArrayList<>(key.getMap().keySet());
        this.mongoDbIndexes.put(collection, indexes);
        resultHandler.handle(Future.succeededFuture());
        return this;
    }

    @Override
    public MongoClient find(final String collection, final JsonObject query,
            final Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        final List<JsonObject> collectionList = this.mongoDbData.get(collection);
        final List<JsonObject> foundDocuments = new ArrayList<>();

        // filter out searchAttr with a "." since these are not simple attributes and must be dealt with separately.
        final Map<String, Object> searchAttr = query.getMap().entrySet().stream().filter(e -> !e.getKey().contains(".")).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        final Map<String, Object> complexSearchAttr = query.getMap().entrySet().stream().filter(e -> e.getKey().contains(".")).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // get entity by comparing key-val-pairs, success if all match
        for (JsonObject document : collectionList) {
            final Map<String, Object> documentAttr = document.getMap();
            if ( searchAttr.entrySet().stream().allMatch(queryAttr -> queryAttr.getValue().equals(documentAttr.get(queryAttr.getKey())))) {
                // treat complex query attributes
                final Boolean allComplexSearchAttrSatisfied = complexSearchAttr.entrySet().stream().map(e -> {
                    if (e.getValue().getClass() == JsonObject.class) {
                     return checkForConditionInPath(document, e.getKey(), (JsonObject) e.getValue());
                    }
                    // else complexSearchAttr NotImplemented
                    throw new UnsupportedOperationException("Mock: MongoDb query attribute not implemented.");
                }).allMatch(e -> e);
                if (allComplexSearchAttrSatisfied) {
                foundDocuments.add(document);
                }
            }
        }
        // special query mocks:
        if (query.getMap().size() == 1 && query.getMap().values().toArray()[0].toString().contains("$eq")) {
            // getByCa query
            final List<JsonObject> documentsFoundGetByCa = findDocumentsByJsonPathEqSearch(collection, query);
            if (!documentsFoundGetByCa.isEmpty()) {
                foundDocuments.addAll(documentsFoundGetByCa);
            }
        }
        resultHandler.handle(Future.succeededFuture(foundDocuments));
        // nothing found
        return this;
    }

    /**
     * Traverse JsonObject path and check if it satisfy an {@code $exist} and {@code $in} condition.
     *
     * @param document The document to look in.
     * @param path the path.
     * @param condition the memberOf condition.
     * @return {@code true} if condition can be satisfied.
     * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/in/">MongodDB reference $in</a>
     */
    private Boolean checkForConditionInPath(final JsonObject document, final String path, final JsonObject condition) {
        final String[] pathElements = path.split("\\.");
        if (pathElements.length == 1 && document.getMap().containsKey(pathElements[0])) {
            final Map<String, Object> conditionMap = condition.getMap();
            // if condition is for $exist check in JsonArray
            if (conditionMap.containsKey("$exists") && conditionMap.get("$exists").equals(true)
                    && conditionMap.containsKey("$in") && conditionMap.get("$in").getClass() == JsonArray.class) {
                // if document contains JsonArray in path
                if (document.getMap().get(pathElements[0]).getClass() == JsonArray.class) {
                    final List<String> conditionList = ((JsonArray) conditionMap.get("$in")).getList();
                    final List<String> docInPathList = ((JsonArray) document.getMap().get(pathElements[0])).getList();
                    return docInPathList.containsAll(conditionList);
                }
            }
        } else {
            if (pathElements.length > 1 && document.getMap().containsKey(pathElements[0])) {
                final String restOfPath = String.join(".", Arrays.copyOfRange(pathElements, 1, pathElements.length));
                return checkForConditionInPath(document.getJsonObject(pathElements[0]), restOfPath, condition);
            }
        }
        return false;
    }

    @Override
    public MongoClient findOne(final String collection, final JsonObject query, @Nullable final JsonObject fields,
            final Handler<AsyncResult<JsonObject>> resultHandler) {
        final Promise<List<JsonObject>> documentFound = Promise.promise();
        this.find(collection, query, documentFound);
        documentFound.future()
                .compose(documents -> {
                    if (documents.isEmpty()) {
                        return Future.succeededFuture(null);
                    }
                    return Future.succeededFuture(documents.get(0));
                })
                .setHandler(resultHandler);
        return this;
    }

    /**
     * Searches for {@code $eq}-MongoDb-query in a collection.
     *
     * @param collection the collection to search in.
     * @param query the {@code $eq} query
     * @return the documents if found.
     */
    private List<JsonObject> findDocumentsByJsonPathEqSearch(final String collection, final JsonObject query) {
        final List<JsonObject> documentsFound = new ArrayList<>();
        final String path = query.getMap().keySet().toArray()[0].toString();
        final JsonObject searchObject = new JsonObject(query.getMap().values().toArray()[0].toString());
        final String eqSearchValue = searchObject.getString("$eq");
        final String[] pathElements = path.split("\\.");
        final List<JsonObject> collectionList = this.mongoDbData.get(collection);
        for (JsonObject doc : collectionList) {
            if (findEntityByFindPropertyPath(doc, pathElements, eqSearchValue)) {
                documentsFound.add(doc);
            }
        }
        return documentsFound;
    }

    /**
     * Checks if provided document contain a search string in the provided path.
     *
     * @param document the document to search in.
     * @param path the path to traverse.
     * @param eqSearchValue the search string to compare to after path traversing.
     * @return {@code true } if search string can be found in provided path.
     */
    private Boolean findEntityByFindPropertyPath(final Object document, final String[] path,
            final String eqSearchValue) {
        if (path.length > 0) {
            final Object pathElem = path[0];
            if (document.getClass() == JsonObject.class) {
                final Map<String, Object> currentNodeMap = ((JsonObject) document).getMap();
                if (!currentNodeMap.containsKey(pathElem)) {
                    return false;
                }
                return findEntityByFindPropertyPath(currentNodeMap.get(pathElem),
                        Arrays.copyOfRange(path, 1, path.length), eqSearchValue);
            } else if (document.getClass() == JsonArray.class) {
                final JsonArray documentArray = ((JsonArray) document);
                return documentArray.stream()
                        .anyMatch(arrayElem -> findEntityByFindPropertyPath(arrayElem, path, eqSearchValue));
            } else {
                return false;
            }
        }
        return document.equals(eqSearchValue);
    }

    @Override
    public MongoClient insert(final String collection, final JsonObject document,
            final Handler<AsyncResult<String>> resultHandler) {
        final List<JsonObject> collectionList = this.mongoDbData.get(collection);
        // extract indexed keys from provided document
        final List<String> indexes = this.mongoDbIndexes.get(collection);
        final Map<String, Object> indexesInDocument = indexes.stream().map(index -> {
            if (!document.containsKey(index)) {
                // provided document does not contain index
                return null;
            }
            return new AbstractMap.SimpleEntry<String, Object>(index, document.getString(index));
        }).filter(Objects::nonNull)
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        // search for document with given unique keys
        final Promise<JsonObject> existingDocumentFound = Promise.promise();
        this.findOne(collection, new JsonObject(indexesInDocument), null, existingDocumentFound);

        existingDocumentFound.future()
                .compose(existingDocument -> {
                    if (existingDocument != null) {
                        return Future.failedFuture(new MongoException(DUPLICATE_KEY_ERROR_CODES,
                                "Duplicate tenantId dummyException"));
                    }
                    final JsonObject documentWithId = document.copy().put(OBJECT_ID_FIELD,
                            UUID.randomUUID().toString());
                    collectionList.add(documentWithId);
                    return Future.succeededFuture(documentWithId.getString(OBJECT_ID_FIELD));
                })
                .setHandler(resultHandler);

        return this;
    }

    @Override
    public MongoClient removeDocument(final String collection, final JsonObject query,
            final Handler<AsyncResult<MongoClientDeleteResult>> resultHandler) {
        final Promise<List<JsonObject>> documentFound = Promise.promise();
        this.find(collection, query, documentFound);
        documentFound.future()
                .compose(docs -> {
                    if (docs.isEmpty()) {
                        return Future.succeededFuture(new MongoClientDeleteResult(0));
                    } else {
                        docs.forEach(d -> this.mongoDbData.get(collection).remove(d));
                        return Future.succeededFuture(new MongoClientDeleteResult(docs.size()));
                    }

                }).setHandler(resultHandler);
        return this;
    }

    @Override
    public MongoClient findOneAndReplace(final String collection, final JsonObject query, final JsonObject replace,
            final Handler<AsyncResult<JsonObject>> resultHandler) {

        final Promise<MongoClientDeleteResult> documentRemoved = Promise.promise();
        this.removeDocument(collection, query, documentRemoved);
        documentRemoved.future()
                .compose(document -> {
                    if (document.getRemovedCount() == 0) {
                        return Future.succeededFuture();
                    }
                    final Promise<String> recreateDocument = Promise.promise();
                    this.insert(collection, replace, recreateDocument);
                    return recreateDocument.future()
                            .compose(createdDocumentId -> Future.succeededFuture(replace));
                })
                .setHandler(resultHandler);
        return this;
    }

    @Override
    public MongoClient findOneAndReplaceWithOptions(final String collection, final JsonObject query,
            final JsonObject replace, final FindOptions findOptions, final UpdateOptions updateOptions,
            final Handler<AsyncResult<JsonObject>> resultHandler) {
        if (updateOptions.isReturningNewDocument()) {
//            final Promise<JsonObject> updateDocument = Promise.promise();
            findOneAndReplace(collection, query, replace, resultHandler);
        } else {
            throw new UnsupportedOperationException("Mock: UpdateOptions only accepts \"isReturningNewDocument\": true");
        }
        return this;
    }

    @Override
    public MongoClient count(final String collection, final JsonObject query,
            final Handler<AsyncResult<Long>> resultHandler) {
        if (query.isEmpty()) {
            resultHandler.handle(Future.succeededFuture((long) this.mongoDbData.get(collection).size()));
            return this;
        }
        resultHandler.handle(Future.failedFuture("mongoClientMock: Not Implemented!"));
        return this;
    }

    @Override
    public MongoClient updateCollection(final String collection, final JsonObject query, final JsonObject update,
            final Handler<AsyncResult<MongoClientUpdateResult>> resultHandler) {
        if (!update.containsKey("$set")) {
            resultHandler.handle(Future.failedFuture("mongoClientMock: No valid update expression."));
            return this;
        }
        final JsonObject updateContent = update.getJsonObject("$set");
        final Promise<List<JsonObject>> documentsToUpdate = Promise.promise();
        this.find(collection, query, documentsToUpdate);
        documentsToUpdate.future()
                .compose(docs -> {
                    for (JsonObject doc : docs) {
                        doc.mergeIn(updateContent);
                    }
                    return Future.succeededFuture(
                            new MongoClientUpdateResult(
                                    new JsonObject().put(MongoClientUpdateResult.DOC_MATCHED, docs.size())));
                })
                .setHandler(resultHandler);
        return this;

    }

    @Override
    public MongoClient findOneAndDelete(final String collection, final JsonObject query,
            final Handler<AsyncResult<JsonObject>> resultHandler) {

        final Promise<JsonObject> foundAndDeletedDocument = Promise.promise();
        findOne(collection, query, new JsonObject(), foundAndDeletedDocument);
        foundAndDeletedDocument.future()
                .compose( found -> {
                    if (found == null) {
                        return Future.succeededFuture();
                    }
                    final Promise<MongoClientDeleteResult> documentDeleted = Promise.promise();
                    removeDocument(collection, query, documentDeleted);
                    return documentDeleted.future()
                                    .compose( ok -> Future.succeededFuture(found));

                })
                .setHandler(resultHandler);
                return this;
    }

    @Override
    public MongoClient findWithOptions(final String collection, final JsonObject query, final FindOptions options,
            final Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        final Promise<List<JsonObject>> findDocument = Promise.promise();
        find(collection, query, findDocument);
        findDocument.future()
                .compose(Future::succeededFuture)
                .setHandler(resultHandler);
        return this;
    }
    // Not implemented methods

    @Override
    public MongoClient save(final String collection, final JsonObject document,
            final Handler<AsyncResult<String>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient saveWithOptions(final String collection, final JsonObject document,
            @Nullable final WriteOption writeOption, final Handler<AsyncResult<String>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient insertWithOptions(final String collection, final JsonObject document,
            @Nullable final WriteOption writeOption, final Handler<AsyncResult<String>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient update(final String collection, final JsonObject query, final JsonObject update,
            final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient updateWithOptions(final String collection, final JsonObject query, final JsonObject update,
            final UpdateOptions options, final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient updateCollectionWithOptions(final String collection, final JsonObject query,
            final JsonObject update, final UpdateOptions options,
            final Handler<AsyncResult<MongoClientUpdateResult>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient replace(final String collection, final JsonObject query, final JsonObject replace,
            final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient replaceDocuments(final String collection, final JsonObject query, final JsonObject replace,
            final Handler<AsyncResult<MongoClientUpdateResult>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient replaceWithOptions(final String collection, final JsonObject query, final JsonObject replace,
            final UpdateOptions options, final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient replaceDocumentsWithOptions(final String collection, final JsonObject query,
            final JsonObject replace, final UpdateOptions options,
            final Handler<AsyncResult<MongoClientUpdateResult>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient bulkWrite(final String collection, final List<BulkOperation> operations,
            final Handler<AsyncResult<MongoClientBulkWriteResult>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient bulkWriteWithOptions(final String collection, final List<BulkOperation> operations,
            final BulkWriteOptions bulkWriteOptions,
            final Handler<AsyncResult<MongoClientBulkWriteResult>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public ReadStream<JsonObject> findBatch(final String collection, final JsonObject query) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public ReadStream<JsonObject> findBatchWithOptions(final String collection, final JsonObject query,
            final FindOptions options) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient findOneAndUpdate(final String collection, final JsonObject query, final JsonObject update,
            final Handler<AsyncResult<JsonObject>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient findOneAndUpdateWithOptions(final String collection, final JsonObject query,
            final JsonObject update, final FindOptions findOptions, final UpdateOptions updateOptions,
            final Handler<AsyncResult<JsonObject>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient findOneAndDeleteWithOptions(final String collection, final JsonObject query,
            final FindOptions findOptions, final Handler<AsyncResult<JsonObject>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient remove(final String collection, final JsonObject query,
            final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient removeDocuments(final String collection, final JsonObject query,
            final Handler<AsyncResult<MongoClientDeleteResult>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient removeWithOptions(final String collection, final JsonObject query,
            final WriteOption writeOption, final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient removeDocumentsWithOptions(final String collection, final JsonObject query,
            @Nullable final WriteOption writeOption,
            final Handler<AsyncResult<MongoClientDeleteResult>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient removeOne(final String collection, final JsonObject query,
            final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient removeOneWithOptions(final String collection, final JsonObject query,
            final WriteOption writeOption, final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient removeDocumentWithOptions(final String collection, final JsonObject query,
            @Nullable final WriteOption writeOption,
            final Handler<AsyncResult<MongoClientDeleteResult>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient createCollection(final String collectionName,
            final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient getCollections(final Handler<AsyncResult<List<String>>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(new ArrayList<>(this.mongoDbData.keySet())));
        return this;
    }

    @Override
    public MongoClient dropCollection(final String collection, final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient createIndex(final String collection, final JsonObject key,
            final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient listIndexes(final String collection, final Handler<AsyncResult<JsonArray>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient dropIndex(final String collection, final String indexName,
            final Handler<AsyncResult<Void>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient runCommand(final String commandName, final JsonObject command,
            final Handler<AsyncResult<JsonObject>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient distinct(final String collection, final String fieldName, final String resultClassname,
            final Handler<AsyncResult<JsonArray>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public MongoClient distinctWithQuery(final String collection, final String fieldName,
            final String resultClassname, final JsonObject query,
            final Handler<AsyncResult<JsonArray>> resultHandler) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public ReadStream<JsonObject> distinctBatch(final String collection, final String fieldName,
            final String resultClassname) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public ReadStream<JsonObject> distinctBatchWithQuery(final String collection, final String fieldName,
            final String resultClassname, final JsonObject query) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public ReadStream<JsonObject> distinctBatchWithQuery(final String collection, final String fieldName,
            final String resultClassname, final JsonObject query, final int batchSize) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public ReadStream<JsonObject> aggregate(final String collection, final JsonArray pipeline) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public ReadStream<JsonObject> aggregateWithOptions(final String collection, final JsonArray pipeline,
            final AggregateOptions options) {
        throw new UnsupportedOperationException("Mock: Not implemented");
    }

    @Override
    public void close() {

    }
}
