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
package org.apache.karaf.decanter.collector.jmx;

import java.lang.management.ManagementFactory;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decanter JMX Pooling Collector
 */
@Component(
    name = "org.apache.karaf.decanter.collector.jmx",
    immediate = true,
    property = { "decanter.collector.name=jmx",
            "scheduler.period:Long=60",
            "scheduler.concurrent:Boolean=false",
            "scheduler.name=decanter-collector-jmx"}
)
public class JmxCollector implements Runnable {

    @Reference
    public EventAdmin dispatcher;

    private final static Logger LOGGER = LoggerFactory.getLogger(JmxCollector.class);

    private String type;
    private String url;
    private String username;
    private String password;
    private String remoteProtocolPkgs;

    private Set<String> objectNames;
    private Map<String, String> operations;
    private Dictionary<String, Object> properties;

    @SuppressWarnings("unchecked")
    public void activate(ComponentContext context) {
        this.properties = context.getProperties();
        String type = getProperty(this.properties, "type", "jmx-local");
        String url = getProperty(this.properties, "url", "local");
        String username = getProperty(this.properties, "username", null);
        String password = getProperty(this.properties, "password", null);
        String remoteProtocolPkgs = getProperty(this.properties, "jmx.remote.protocol.provider.pkgs", null);
        Dictionary<String, String> serviceProperties = new Hashtable<> ();
        serviceProperties.put("decanter.collector.name", type);

        this.type = type;
        this.url = url;
        this.username = username;
        this.password = password;
        this.remoteProtocolPkgs = remoteProtocolPkgs;

        this.objectNames = new HashSet<> ();
        this.operations = new HashMap<>();
        for (Enumeration<String> e = properties.keys(); e.hasMoreElements(); ) {
        	String key = e.nextElement();
        	if( "object.name".equals( key ) || key.startsWith( "object.name." )) {
        		Object value = this.properties.get( key );
        		if (value != null)
        			this.objectNames.add( value.toString());
        	}
        	if (key.startsWith("operation.name.")) {
        	    operations.put(key.substring("operation.name.".length()), (String) properties.get(key));
            }
        }
    }

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

    @Override
    public void run() {
        LOGGER.debug("Karaf Decanter JMX Collector starts harvesting {}...", this.type);

        JMXConnector connector = null;
        MBeanServerConnection connection = null;

        String host = null;
        if (this.url == null || this.url.equalsIgnoreCase("local")) {
            LOGGER.debug("Harvesting local MBeanServer ({})...", this.type);
            connection = ManagementFactory.getPlatformMBeanServer();
        } else {
            try {
                JMXServiceURL jmxServiceURL = new JMXServiceURL(this.url);
                connector = JMXConnectorFactory.connect(jmxServiceURL, getEnv());
                connection = connector.getMBeanServerConnection();
                host = jmxServiceURL.toString();
            } catch (Exception e) {
                LOGGER.error("Can't connect to MBeanServer {} ({})", this.url, this.type, e);
            }
        }

        if (connection == null) {
            LOGGER.debug("MBean connection is null, nothing to do");
            return;
        }

        String currentObjectName = null;
        try {
            LOGGER.debug("Creating harvester");
            BeanHarvester harvester = new BeanHarvester(connection, this.type);

            LOGGER.debug("Populating names ({})", this.objectNames);
            Set<ObjectName> names = new HashSet<> ();
            if (objectNames.size() > 0) {
                for (String objectName : this.objectNames) {
                    LOGGER.debug("Query {}", objectName);
                    currentObjectName = objectName;
                    names.addAll(connection.queryNames(getObjectName(objectName), null));
                }
            } else {
                names.addAll(connection.queryNames(getObjectName(null), null));
            }

            String topic = (properties.get(EventConstants.EVENT_TOPIC) != null) ? (String) properties.get(EventConstants.EVENT_TOPIC) : "decanter/collect/jmx/";

            for (ObjectName name : names) {
                LOGGER.debug("Harvesting {}", name);
                try {
                    Map<String, Object> data = harvester.harvestBean(name);
                    PropertiesPreparator.prepare(data, properties);
                    data.put("host", host);
                    Event event = new Event(topic + this.type + "/" + getTopic(name), data);
                    LOGGER.debug("Posting for {}", name);
                    dispatcher.postEvent(event);
                } catch (Exception e) {
                    LOGGER.warn("Can't read MBean {} ({})", name, this.type, e);
                }
            }

            for (String operation : operations.keySet()) {
                String raw = operations.get(operation);
                String[] split = raw.split("\\|");
                if (split.length == 4) {
                    ObjectName objectName = new ObjectName(split[0]);
                    String operationName = split[1];
                    String[] arguments = split[2].split(",");
                    String[] signatures = split[3].split(",");
                    Map<String, Object> data = harvester.executeOperation(operation, objectName, operationName, arguments, signatures);
                    PropertiesPreparator.prepare(data, properties);
                    data.put("host", host);
                    Event event = new Event(topic + this.type + "/" + getTopic(objectName), data);
                    dispatcher.postEvent(event);
                } else {
                    LOGGER.warn("{} is not well configured ({})", operation, raw);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Can't query object name from {} ({}) {}", this.url, this.type, currentObjectName);
        }

        try {
            connector.close();
        } catch (Exception e) {
            LOGGER.trace("Can't close JMX connector", e);
        }

        LOGGER.debug("Karaf Decanter JMX Collector harvesting {} done", this.type);
    }

    private Map<String, Object> getEnv() {
        Map<String,Object> env = new HashMap<> ();
        if (this.username != null && this.password != null) {
            env.put(JMXConnector.CREDENTIALS, new String[] { this.username, this.password });
        }
        if (this.remoteProtocolPkgs != null) {
            env.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, this.remoteProtocolPkgs);
        }
        return env;
    }

    private ObjectName getObjectName(String objectName) throws MalformedObjectNameException {
        return objectName == null ? null : new ObjectName(objectName);
    }

    private String getTopic(ObjectName name) {
        return name.getDomain().replace(".", "/").replace(" ", "_");
    }

	Set<String> getObjectNames() {
		return this.objectNames;
	}
}
