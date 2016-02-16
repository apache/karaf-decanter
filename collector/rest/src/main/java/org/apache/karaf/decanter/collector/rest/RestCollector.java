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
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decanter REST Collector
 */
public class RestCollector implements Runnable {
    private final static Logger LOGGER = LoggerFactory.getLogger(RestCollector.class);

    private String type;
    private URL url;
    private String[] paths;
    private EventAdmin eventAdmin;
    private Dictionary<String, String> properties;
    private Unmarshaller marshaller;

    public RestCollector(String url, 
                         String username, 
                         String password, 
                         String[] paths,
                         EventAdmin eventAdmin,
                         Unmarshaller marshaller,
                         Dictionary<String, String> properties)
                         throws MalformedURLException {
        this.marshaller = marshaller;
        this.url = new URL(url);
        this.eventAdmin = eventAdmin;
        this.paths = paths;
        this.properties = properties;
    }

    @Override
    public void run() {
        LOGGER.debug("Karaf Decanter REST Collector starts harvesting from {} ...", url);
        for (String path : paths) {
            try {
                URL complete = new URL(url, path);
                URLConnection connection = complete.openConnection();
                Map<String, Object> data = marshaller.unmarshal(connection.getInputStream());
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

}
