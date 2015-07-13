/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.decanter.elasticsearch;

import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.internal.InternalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Start an Elasticsearch node internally to Karaf.
 */
public class EmbeddedNode {

    private final static Logger LOGGER = LoggerFactory.getLogger(EmbeddedNode.class);
    private static Node node;

    public EmbeddedNode() throws Exception {
        LOGGER.info("Starting Elasticsearch node ...");

        LOGGER.debug("Creating elasticsearch settings");

        String karafHome = System.getProperty("karaf.home");
        File pluginsFile = new File(new File(karafHome), "/elasticsearch/plugins");
        LOGGER.debug("Elasticsearch plugins folder: " + pluginsFile.getAbsolutePath());

        Settings settings;
        File ymlFile = new File(System.getProperty("karaf.etc"), "elasticsearch.yml");
        if (ymlFile.exists()) {
            // etc/elasticsearch.yml is provided
            LOGGER.debug("elasticsearch.yml found for settings");
            settings = ImmutableSettings.settingsBuilder().loadFromUrl(ymlFile.toURL()).build();
        } else {
            // using default
            LOGGER.debug("Using default elasticsearch configuration");
            settings = ImmutableSettings.settingsBuilder().put("cluster.name", "elasticsearch")
                    .put("http.enabled", "true")
                    .put("node.data", true)
                    .put("path.data", "data")
                    .put("node.name", System.getProperty("karaf.name") == null ? "decanter" : System.getProperty("karaf.name"))
                    .put("network.host", "127.0.0.1")
                    .put("cluster.routing.schedule", "50ms")
                    .put("path.plugins", pluginsFile.getAbsolutePath())
                    .put("http.cors.enabled", true)
                    .put("http.cors.allow-origin", "/.*/")
                    .build();
        }

        LOGGER.debug("Creating the elasticsearch node");
        ImmutableSettings.Builder builder = ImmutableSettings.settingsBuilder();
        builder.put(settings);
        builder.classLoader(Settings.class.getClassLoader());
        node = new InternalNode(builder.build(), false);

        LOGGER.info("Elasticsearch node created");
    }

    public void start() throws Exception {
        if (node != null) {
            node.start();
        }
    }

    public void stop() throws Exception {
        if (node != null) {
            node.close();
        }
    }

    public boolean isStarted() throws Exception {
        if (node != null) {
            return !node.isClosed();
        }
        return false;
    }

    public Node getNode() {
        return node;
    }

}
