/**
 * Copyright (c) 2014-2015 Bosch Software Innovations GmbH, Germany. All rights reserved.
 */
package org.eclipse.hono.adapter.lwm2m;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.model.ResourceModel.Type;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Provider for LwM2M models.
 */
@Component
@Profile("!standalone")
public class CustomModelProvider implements LwM2mModelProvider {

    private static final Logger LOG = LoggerFactory.getLogger(CustomModelProvider.class);
    private final LwM2mModel model;
    private final Map<Integer, ObjectModel> objectModelMap = new HashMap<>();
    private final Map<String, Integer> objectIdMap = new HashMap<>();

    /**
     * Create new CustomModelProvider Object and reading standard lwm2m objects
     * to create new LwM2mModel Object.
     */
    public CustomModelProvider() {
        initStandardObjects();
        model = new LwM2mModel(objectModelMap.values());
    }

    /**
     * Create new CustomModelProvider Object and reading xml objects to create
     * new LwM2mModel Object.
     * 
     * @param objectDir
     */
    @Autowired
    public CustomModelProvider(final LeshanConfigProperties config) {

        if (config.getObjectDir() == null || config.getObjectDir().isEmpty()) {
            LOG.info("no leshan.server.objectdir configured, no additional LWM2M object definitions will be loaded");
        } else {
            LOG.debug("loading additional LWM2M object definitions from xml files in directory {}", config.getObjectDir());
            initLwM2mModel(new File(config.getObjectDir()));
        }
        initStandardObjects();
        model = new LwM2mModel(objectModelMap.values());
    }

    @Override
    public LwM2mModel getObjectModel(final Client client) {
        // same model for all clients
        return model;
    }

    /**
     * Returns feature ID for the given objectId and objectInstanceId.
     * 
     * @param objectId
     * @param objectInstanceId
     * @return
     */
    public String getFeatureId(final int objectId, final int objectInstanceId) {
        return objectModelMap.get(objectId).name + "." + objectInstanceId;
    }

    /**
     * Returns the numeric ObjectId of the given FeatureId.
     *
     * @param featureId
     *            as String
     * @return the numeric ObjectId as Integer, null if no ObjectId is found
     */
    public Integer getObjectIdFromFeatureId(final String featureId) {
        final String id = featureId.substring(0, featureId.indexOf('.'));
        return objectIdMap.get(id);
    }

    /**
     * Returns the numeric ObjectInstanceId from the featureId.
     *
     * @param featureId
     *            as String
     * @return numeric ObjectInstanceId
     */
    public Integer getObjectInstanceIdFromFeatureId(final String featureId) {
        final String[] id = featureId.split("\\.");
        return Integer.valueOf(id[1]);
    }

    /**
     * returns the {@link Type} of the given resourceId.
     *
     * @param objectId
     *            objectId of the LwM2M Object to which the resource belongs to.
     * @param resourceId
     *            id of the resource
     * @param objectModel
     *            {@link LwM2mModel}
     * @return {@link Type}
     */
    public Type getDatatypeFromId(final Integer objectId, final Integer resourceId, final LwM2mModel objectModel) {
        final ObjectModel obm = objectModel.getObjectModel(objectId);

        for (final Entry<Integer, ResourceModel> entry : obm.resources.entrySet()) {
            if (resourceId.equals(entry.getValue().id)) {
                return entry.getValue().type;
            }
        }
        throw new IllegalArgumentException("No value found for given resourceId:" + resourceId);
    }

    /**
     * Load LwM2M Object Models from internal jar and from external directory
     * /objects.
     * 
     * @param objectIdMap
     */
    private void initLwM2mModel(final File dir) {
        if (!dir.exists() || !dir.canRead()) {
            LOG.warn(
                    "given Custom Model directory {} does not exist or is not readable. No customer object models loaded",
                    dir.getPath());
            return;
        }
        Arrays.stream(dir.listFiles((f, n) -> n.endsWith(".xml")))
                .forEach(modelProviderConsumer(customObjectFile -> initCustomerObjects(customObjectFile)));
    }

    /**
     * load all lwm2m customer object models.
     * 
     * @param file
     */
    private void initCustomerObjects(final File file) {

        try {
            final FileInputStream openStream = new FileInputStream(file);
            final ObjectModel objectModel = ObjectLoader.loadDdfFile(openStream, file.getName());
            if (objectModel != null) {
                objectModelMap.put(objectModel.id, objectModel);
                objectIdMap.put(objectModel.name, objectModel.id);
            }
        } catch (final FileNotFoundException e) {
            LOG.error("error while Load object definition from XML file.", e);
        }
    }

    /**
     * Load all standard lwm2m Objectmodels from internal directory /objects.
     */

    private void initStandardObjects() {
        try {
            final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            final Resource[] objects = resolver.getResources("classpath*:objects/*.xml");
            Arrays.stream(objects).forEach(modelProviderConsumer(object -> extractFile(object)));
        } catch (final IOException e) {
            LOG.error("error while reading lwm2m objectModels from.", e);
        }
    }

    private void extractFile(final Resource k) {
        try {
            final InputStream openStream = k.getURL().openStream();
            final ObjectModel objectModel = ObjectLoader.loadDdfFile(openStream, k.getURL().toString());
            if (objectModel != null) {
                objectModelMap.put(objectModel.id, objectModel);
                objectIdMap.put(objectModel.name, objectModel.id);
            }
        } catch (final IOException e) {
            LOG.error("error while reading lwm2m objectModels.", e);
        }
    }

    private <T> Consumer<T> modelProviderConsumer(final Consumer<T> consumer) {
        return t -> consumer.accept(t);
    }
}
