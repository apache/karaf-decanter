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
    property = { "decanter.collector.name=rest",
            "scheduler.period:Long=60",
            "scheduler.concurrent:Boolean=false",
            "scheduler.name=decanter-collector-rest" }
)
public class RestCollector implements Runnable {

    @Reference
    public EventAdmin dispatcher;

    @Reference
    public Unmarshaller unmarshaller;

    private final static Logger LOGGER = LoggerFactory.getLogger(RestCollector.class);

    private URL url;
    private String[] paths;
    private String baseTopic;
    private Dictionary<String, Object> properties;

    private boolean repeatedError;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) throws MalformedURLException {
        Dictionary<String, Object> props = context.getProperties();
        this.url = new URL(getProperty(props, "url", null));
        getProperty(props, "username", null);
        getProperty(props, "password", null);
        this.paths = getProperty(props, "paths", "").split(",");
        this.baseTopic = getProperty(props, "topic", "decanter/collect");
        //props.remove("password");
        //props.remove("username");
        this.properties = props;
        this.repeatedError = false;
    }
    
    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

    @Override
    public void run() {
        LOGGER.debug("Karaf Decanter REST Collector starts harvesting from {} ...", url);
        for (String path : paths) {
            URL complete;
            try {
                complete = new URL(url, path);
            } catch (MalformedURLException e) {
                LOGGER.warn("Invalid URL. Stopping collector", e);
                throw new IllegalArgumentException(e);
            }
            try {
                URLConnection connection = complete.openConnection();
                Map<String, Object> data = unmarshaller.unmarshal(connection.getInputStream());
                data.put("type", "rest");
                data.put("hostName", url.getHost());
                data.put("remote.url", complete);

                // custom fields
                Enumeration<String> keys = properties.keys();
                while (keys.hasMoreElements()) {
                    String key = keys.nextElement();
                    data.put(key, properties.get(key));
                }

                addUserProperties(data);
                dispatcher.postEvent(new Event(toTopic(complete), data));
                repeatedError = false;
            } catch (Exception e) {
                if (repeatedError) {
                    LOGGER.warn("Error fetching " + complete, e);
                    repeatedError = true;
                } else {
                    LOGGER.debug("Repeated Error fetching " + complete, e);
                }
                
            }
        }
        LOGGER.debug("Karaf Decanter REST Collector harvesting done");
    }

    private String toTopic(URL url) {
        return baseTopic + "/" + url.getHost() + url.getPath();
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

}
