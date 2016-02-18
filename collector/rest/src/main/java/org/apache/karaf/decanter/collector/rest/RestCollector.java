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
package org.apache.karaf.decanter.collector.rest;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;

import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decanter REST Collector
 */
@Component(
    service = Runnable.class,
    name = "org.apache.karaf.decanter.collector.rest",
    immediate = true,
    property = "decanter.collector.name=rest"
)
public class RestCollector implements Runnable {
    private final static Logger LOGGER = LoggerFactory.getLogger(RestCollector.class);

    private String type;
    private URL url;
    private String[] paths;
    private Dictionary<String, Object> properties;

    private EventAdmin eventAdmin;
    private Unmarshaller unmarshaller;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) throws MalformedURLException {
        Dictionary<String, Object> props = context.getProperties();
        this.url = new URL(getProperty(props, "url", null));
        getProperty(props, "username", null);
        getProperty(props, "password", null);
        this.paths = getProperty(props, "paths", "").split(",");
        //props.remove("password");
        //props.remove("username");
        this.properties = props;
    }
    
    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

    @Override
    public void run() {
        LOGGER.debug("Karaf Decanter REST Collector starts harvesting from {} ...", url);
        for (String path : paths) {
            try {
                URL complete = new URL(url, path);
                URLConnection connection = complete.openConnection();
                Map<String, Object> data = unmarshaller.unmarshal(connection.getInputStream());
                data.put("type", "rest");
                data.put("hostName", url.getHost());
                data.put("remote.url", complete);
                addUserProperties(data);
                eventAdmin.postEvent(new Event(toTopic(complete), data));
            } catch (Exception e) {
                LOGGER.warn("Error fetching", e);
            }
        }
        LOGGER.debug("Karaf Decanter JMX Collector harvesting {} done", type);
    }

    private String toTopic(URL url) {
        return "decanter/collect/rest/" + url.getHost() + url.getPath();
    }

    private void addUserProperties(Map<String, Object> data) {
        if (properties != null) {
            Enumeration<String> keys = properties.keys();
            while (keys.hasMoreElements()) {
                String property = keys.nextElement();
                data.put(property, properties.get(property));
            }
        }
    }
    
    @Reference
    public void setEventAdmin(EventAdmin eventAdmin) {
        this.eventAdmin = eventAdmin;
    }

    @Reference
    public void setUnmarshaller(Unmarshaller unmarshaller) {
        this.unmarshaller = unmarshaller;
    }
}
