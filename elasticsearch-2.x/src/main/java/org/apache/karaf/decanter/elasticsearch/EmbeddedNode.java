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

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.Dictionary;

/**
 * Start an Elasticsearch node internally to Karaf.
 */
public class EmbeddedNode {

    private final static Logger LOGGER = LoggerFactory.getLogger(EmbeddedNode.class);
    private static Node node;
    
    public static String PLUGINS_DIRECTORY = "plugins.directory";
    public static String ELASTIC_YAML_FILE = "elasticsearch.yaml";

    public static String CLUSTER_NAME = "cluster.name";
    public static String HTTP_ENABLED = "http.enabled";
    public static String NODE_NAME = "node.name";
    public static String PATH_DATA = "path.data";
    public static String PATH_HOME = "path.home";
    public static String NETWORK_HOST = "network.host";
    public static String CLUSTER_ROUTING_SCHEDULE = "cluster.routing.schedule";
    public static String PATH_PLUGINS = "path.plugins";
    public static String HTTP_CORS_ENABLED = "http.cors.enabled";
    public static String HTTP_CORS_ALLOW_ORIGIN = "http.cors.allow-origin";
    public static String INDEX_MAX_RESULT_WINDOW = "index.max_result_window";

    public EmbeddedNode(Dictionary<String, ?> config) throws Exception {
        LOGGER.info("Starting Elasticsearch node ...");

        LOGGER.debug("Creating elasticsearch settings");

        String karafHome = System.getProperty("karaf.home");
        
        //first some defaults
        File pluginsFile = new File(getConfig(config, null, PLUGINS_DIRECTORY, new File(new File(karafHome), "/elasticsearch/plugins").getAbsolutePath()));
        LOGGER.debug("Elasticsearch plugins folder: {}", pluginsFile.getAbsolutePath());
        
        File ymlFile = new File(getConfig(config, null, ELASTIC_YAML_FILE, new File(System.getProperty("karaf.etc"), "elasticsearch.yml").getAbsolutePath()));
        LOGGER.debug("Eleasticsearch yml configuration: {}", ymlFile.getAbsolutePath());

        Settings settings = null;
        if (ymlFile.exists()) {
            // elasticsearch.yml is provided
            LOGGER.debug("elasticsearch.yml found for settings");
            settings = Settings.settingsBuilder().loadFromPath(Paths.get(ymlFile.toURI())).build();
        } 

        // configuration will be overridden by special configurations
        LOGGER.debug("enhancing elasticsearch configuration with given configurations");
        
        Settings.Builder settingsBuilder = Settings.settingsBuilder();
        if (settings != null) {
            settingsBuilder = Settings.settingsBuilder().put(settings);
        }      

       	settingsBuilder.put(CLUSTER_NAME, getConfig(config, settings, CLUSTER_NAME, "elasticsearch"));
       	settingsBuilder.put(HTTP_ENABLED, getConfig(config, settings, HTTP_ENABLED, "true"));
       	settingsBuilder.put(PATH_DATA, getConfig(config, settings, PATH_DATA, "data"));
        settingsBuilder.put(PATH_HOME, getConfig(config, settings, PATH_HOME, "data"));
       	settingsBuilder.put(NODE_NAME, getConfig(config, settings, NODE_NAME, getNodeName()));
       	settingsBuilder.put(NETWORK_HOST, getConfig(config, settings, NETWORK_HOST, "127.0.0.1"));
       	settingsBuilder.put(CLUSTER_ROUTING_SCHEDULE, getConfig(config, settings, CLUSTER_ROUTING_SCHEDULE, "50ms"));
       	settingsBuilder.put(PATH_PLUGINS, getConfig(config, settings, PATH_PLUGINS, pluginsFile.getAbsolutePath()));
       	settingsBuilder.put(HTTP_CORS_ENABLED, getConfig(config, settings, HTTP_CORS_ENABLED, "true"));
       	settingsBuilder.put(HTTP_CORS_ALLOW_ORIGIN, getConfig(config, settings, HTTP_CORS_ALLOW_ORIGIN, "/.*/"));
        settingsBuilder.put(INDEX_MAX_RESULT_WINDOW, getConfig(config, settings, INDEX_MAX_RESULT_WINDOW, "2147483647"));
        
        LOGGER.debug("Creating the elasticsearch node");
        node = NodeBuilder.nodeBuilder().settings(settingsBuilder).build();

        LOGGER.info("Elasticsearch node created");
    }

    private String getNodeName() {
        return System.getProperty("karaf.name") == null ? "decanter" : System.getProperty("karaf.name");
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
    
    private String getConfig(Dictionary<String, ?> config, Settings settings, String key,
                             String defaultValue) {
        if (settings != null && settings.get(key) != null)
            defaultValue = settings.get(key);
        if (config == null)
            return defaultValue;
        String value = (String)config.get(key);
        return value != null ? value : defaultValue;
    }

}
