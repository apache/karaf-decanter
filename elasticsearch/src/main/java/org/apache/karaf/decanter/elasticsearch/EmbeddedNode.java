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
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
	public static String NODE_DATA = "node.data";
	public static String NODE_NAME = "node.name";
	public static String NODE_MASTER = "node.master";
	public static String PATH_DATA = "path.data";
	public static String NETWORK_HOST = "network.host";
	public static String CLUSTER_ROUTING_SCHEDULE = "cluster.routing.schedule";
	public static String PATH_PLUGINS = "path.plugins";
	public static String HTTP_CORS_ENABLED = "http.cors.enabled";
	public static String HTTP_CORS_ALLOW_ORIGIN = "http.cors.allow-origin";

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
            settings = ImmutableSettings.settingsBuilder().loadFromUrl(ymlFile.toURL()).build();
        } 

        // configuration will be overridden by special configurations
        LOGGER.debug("enhancing elasticsearch configuration with given configurations");
        
        Builder settingsBuilder;
        if (settings == null) {
        	settingsBuilder = ImmutableSettings.settingsBuilder();
        } else {
        	settingsBuilder = ImmutableSettings.settingsBuilder().put(settings);
        }      

       	settingsBuilder.put(CLUSTER_NAME, getConfig(config, settings, CLUSTER_NAME, "elasticsearch"));
       	settingsBuilder.put(HTTP_ENABLED, getConfig(config, settings, HTTP_ENABLED, "true"));
       	settingsBuilder.put(PATH_DATA, getConfig(config, settings, PATH_DATA, "data"));
       	settingsBuilder.put(NODE_MASTER, getConfig(config, settings, NODE_MASTER, "true"));
       	settingsBuilder.put(NODE_DATA, getConfig(config, settings, NODE_DATA, "true"));
       	settingsBuilder.put(NODE_NAME, getConfig(config, settings, NODE_NAME, System.getProperty("karaf.name") == null ? "decanter" : System.getProperty("karaf.name")));
       	settingsBuilder.put(NETWORK_HOST, getConfig(config, settings, NETWORK_HOST, "127.0.0.1"));
       	settingsBuilder.put(CLUSTER_ROUTING_SCHEDULE, getConfig(config, settings, CLUSTER_ROUTING_SCHEDULE, "50ms"));
       	settingsBuilder.put(PATH_PLUGINS, getConfig(config, settings, PATH_PLUGINS, pluginsFile.getAbsolutePath()));
       	settingsBuilder.put(HTTP_CORS_ENABLED, getConfig(config, settings, HTTP_CORS_ENABLED, "true"));
       	settingsBuilder.put(HTTP_CORS_ALLOW_ORIGIN, getConfig(config, settings, HTTP_CORS_ALLOW_ORIGIN, "/.*/"));
        
        LOGGER.debug("Creating the elasticsearch node");
        settingsBuilder.classLoader(Settings.class.getClassLoader());
        node = new InternalNode(settingsBuilder.build(), false);

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
    
    private String getConfig(Dictionary<String, ?> config, Settings settings, String key, String defaultValue) {
    	if (config == null)
    		return defaultValue;
    	if (settings != null && settings.get(key) != null)
    		defaultValue = settings.get(key);
		String value = (String) config.get(key);
		return value != null ? value : defaultValue;
	}

}
