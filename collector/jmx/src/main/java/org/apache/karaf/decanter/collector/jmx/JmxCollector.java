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

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decanter JMX Pooling Collector
 */
@Component(
    name = "org.apache.karaf.decanter.collector.jmx",
    immediate = true,
    property = "decanter.collector.name=jmx"
)
public class JmxCollector implements Runnable {
    private final static Logger LOGGER = LoggerFactory.getLogger(JmxCollector.class);

    private String type;
    private String url;
    private String username;
    private String password;
    private Set<String> objectNames;
    private EventAdmin eventAdmin;
    private Dictionary<String, Object> properties;

    @SuppressWarnings("unchecked")
    public void activate(ComponentContext context) {
        this.properties = context.getProperties();
        String type = getProperty(this.properties, "type", "jmx-local");
        String url = getProperty(this.properties, "url", "local");
        String username = getProperty(this.properties, "username", null);
        String password = getProperty(this.properties, "password", null);
        Dictionary<String, String> serviceProperties = new Hashtable<> ();
        serviceProperties.put("decanter.collector.name", type);

        this.type = type;
        this.url = url;
        this.username = username;
        this.password = password;

        this.objectNames = new HashSet<> ();
        for (Enumeration<String> e = this.properties.keys(); e.hasMoreElements(); ) {
        	String key = e.nextElement();
        	if( "object.name".equals( key ) || key.startsWith( "object.name." )) {
        		Object value = this.properties.get( key );
        		if (value != null)
        			this.objectNames.add( value.toString());
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
            String karafName = System.getProperty("karaf.name");
            BeanHarvester harvester = new BeanHarvester(connection, this.type, host, karafName);
            Set<ObjectName> names = new HashSet<> ();
            for (String objectName : this.objectNames) {
            	currentObjectName = objectName;
            	names.addAll( connection.queryNames(getObjectName(objectName), null));
            }

            for (ObjectName name : names) {
                try {
                    Map<String, Object> data = harvester.harvestBean(name);
                    addUserProperties(data);
                    Event event = new Event("decanter/collect/jmx/" + this.type + "/" + getTopic(name), data);
                    this.eventAdmin.postEvent(event);
                } catch (Exception e) {
                    LOGGER.warn("Can't read MBean {} ({})", name, this.type, e);
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
        return env;
    }

    private void addUserProperties(Map<String, Object> data) {
        if (this.properties != null) {
            Enumeration<String> keys = this.properties.keys();
            while (keys.hasMoreElements()) {
                String property = keys.nextElement();
                data.put(property, this.properties.get(property));
            }
        }
    }

    private ObjectName getObjectName(String objectName) throws MalformedObjectNameException {
        return objectName == null ? null : new ObjectName(objectName);
    }

    private String getTopic(ObjectName name) {
        return name.getDomain().replace(".", "/").replace(" ", "_");
    }

    @Reference
    public void setEventAdmin(EventAdmin eventAdmin) {
        this.eventAdmin = eventAdmin;
    }

	Set<String> getObjectNames() {
		return this.objectNames;
	}
}
