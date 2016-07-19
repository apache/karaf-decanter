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
import org.elasticsearch.common.settings.ImmutableSettings.Builder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.internal.InternalNode;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Dictionary;

/**
 * Start an Elasticsearch node internally to Karaf.
 */
@Component(
    name = "org.apache.karaf.decanter.elasticsearch",
    immediate = true
)
public class EmbeddedNode {

    private final static Logger LOGGER = LoggerFactory.getLogger(EmbeddedNode.class);
    
    private static Node node;
    
    public static String PLUGINS_DIRECTORY = "plugins.directory";
    public static String ELASTIC_YAML_FILE = "elasticsearch.yaml";

    public static String CLUSTER_NAME = "cluster.name";
    public static String HTTP_ENABLED = "http.enabled";
    public static String NODE_DATA = "node.data";
    public static String NODE_NAME = "node.name";
    public static String NODE_MASTER = "node.master";
    public static String PATH_DATA = "path.data";
    public static String NETWORK_HOST = "network.host";
    public static String PORT = "port";
    public static String CLUSTER_ROUTING_SCHEDULE = "cluster.routing.schedule";
    public static String PATH_PLUGINS = "path.plugins";
    public static String HTTP_CORS_ENABLED = "http.cors.enabled";
    public static String HTTP_CORS_ALLOW_ORIGIN = "http.cors.allow-origin";

    private static final boolean IS_WINDOWS = System.getProperty("os.name").contains("indow");

    @SuppressWarnings("unchecked")
    @Activate
    public void acticate(ComponentContext context) throws Exception {
        start(context.getProperties());
    }
    
    public void start(Dictionary<String, ?> config) throws Exception {
        LOGGER.info("Starting Elasticsearch node ...");

        LOGGER.debug("Creating elasticsearch settings");

        String karafHome = System.getProperty("karaf.home");
        
        //first some defaults
        String defaultPluginsPath = new File(new File(karafHome), "/elasticsearch/plugins").getAbsolutePath();
        File pluginsFile = new File((String)getConfig(config, null, PLUGINS_DIRECTORY, defaultPluginsPath));
        LOGGER.debug("Elasticsearch plugins folder: {}", pluginsFile.getAbsolutePath());
        
        String defaultConfigPath = new File(System.getProperty("karaf.etc"), "elasticsearch.yml").getAbsolutePath();
        File ymlFile = new File((String)getConfig(config, null, ELASTIC_YAML_FILE, defaultConfigPath));
        LOGGER.debug("Eleasticsearch yml configuration: {}", ymlFile.getAbsolutePath());

        Settings settings = null;
        if (ymlFile.exists()) {
            // elasticsearch.yml is provided
            LOGGER.debug("elasticsearch.yml found for settings");
            settings = ImmutableSettings.settingsBuilder().loadFromUrl(ymlFile.toURI().toURL()).build();
        } 

        // configuration will be overridden by special configurations
        LOGGER.debug("enhancing elasticsearch configuration with given configurations");
        
        Builder settingsBuilder = ImmutableSettings.settingsBuilder();
        if (settings != null) {
            settingsBuilder = ImmutableSettings.settingsBuilder().put(settings);
        }      

       	settingsBuilder.put(CLUSTER_NAME, getConfig(config, settings, CLUSTER_NAME, "elasticsearch"));
       	settingsBuilder.put(HTTP_ENABLED, getConfig(config, settings, HTTP_ENABLED, "true"));
       	settingsBuilder.put(PATH_DATA, getConfig(config, settings, PATH_DATA, "data"));
       	settingsBuilder.put(NODE_MASTER, getConfig(config, settings, NODE_MASTER, "true"));
       	settingsBuilder.put(NODE_DATA, getConfig(config, settings, NODE_DATA, "true"));
       	settingsBuilder.put(NODE_NAME, getConfig(config, settings, NODE_NAME, getNodeName()));
       	settingsBuilder.put(NETWORK_HOST, getConfig(config, settings, NETWORK_HOST, "127.0.0.1"));
       	settingsBuilder.put(PORT, getConfig(config, settings, PORT, 9300));
       	settingsBuilder.put(CLUSTER_ROUTING_SCHEDULE, getConfig(config, settings, CLUSTER_ROUTING_SCHEDULE, "50ms"));
        if (!IS_WINDOWS) {
       	    settingsBuilder.put(PATH_PLUGINS, getConfig(config, settings, PATH_PLUGINS, pluginsFile.getAbsolutePath()));
        }
       	settingsBuilder.put(HTTP_CORS_ENABLED, getConfig(config, settings, HTTP_CORS_ENABLED, "true"));
       	settingsBuilder.put(HTTP_CORS_ALLOW_ORIGIN, getConfig(config, settings, HTTP_CORS_ALLOW_ORIGIN, "/.*/"));
        
        LOGGER.debug("Creating the elasticsearch node");
        settingsBuilder.classLoader(Settings.class.getClassLoader());
        node = new InternalNode(settingsBuilder.build(), false);

        LOGGER.info("Elasticsearch node created");
        if (node != null) {
            node.start();
        }
    }

    private String getNodeName() {
        return System.getProperty("karaf.name") == null ? "decanter" : System.getProperty("karaf.name");
    }

    @Deactivate
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
    
    private Object getConfig(Dictionary<String, ?> config, Settings settings, String key,
                             Object defaultValue) {
        if (settings != null && settings.get(key) != null)
            defaultValue = settings.get(key);
        if (config == null)
            return defaultValue;
        Object value = config.get(key);
        return value != null ? value : defaultValue;
    }

}
