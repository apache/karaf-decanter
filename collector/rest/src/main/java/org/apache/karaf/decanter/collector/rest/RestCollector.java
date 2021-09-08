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

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
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
    private String topic;
    private String requestMethod;
    private String request;
    private String user;
    private String password;
    private boolean exceptionAsHttpResponse;
    private Integer exceptionHttpResponseCode = null;
    private Dictionary<String, Object> config;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> config) throws MalformedURLException {
        this.config = config;
        this.url = new URL(getProperty(config, "url", "http://localhost:8181"));
        this.paths = getProperty(config, "paths", "").split(",");
        this.topic = getProperty(config, EventConstants.EVENT_TOPIC, "decanter/collect/rest");
        this.requestMethod = getProperty(config, "request.method", "GET");
        this.user = getProperty(config, "user", null);
        this.password = getProperty(config, "password", null);
        this.request = getProperty(config, "request", null);
        this.exceptionAsHttpResponse = Boolean.parseBoolean(getProperty(config, "exception.as.http.response", "false"));
        if (config.get("exception.http.response.code") != null) {
            this.exceptionHttpResponseCode = Integer.parseInt((String) config.get("exception.http.response.code"));
        }
    }
    
    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

    @Override
    public void run() {
        LOGGER.debug("Karaf Decanter REST Collector starts harvesting from {} ...", url);
        for (String path : paths) {
            URL urlWithPath;
            try {
                urlWithPath = new URL(url, path);
            } catch (MalformedURLException e) {
                LOGGER.warn("Invalid URL. Stopping collector", e);
                throw new IllegalArgumentException(e);
            }
            LOGGER.debug("\tpath {}", urlWithPath);
            HttpURLConnection connection = null;
            Map<String, Object> data = new HashMap<>();
            try {
                connection = (HttpURLConnection) urlWithPath.openConnection();
                if (user != null) {
                    String authentication = user + ":" + password;
                    byte[] encodedAuthentication = Base64.getEncoder().encode(authentication.getBytes(StandardCharsets.UTF_8));
                    String authenticationHeader = "Basic " + new String(encodedAuthentication);
                    connection.setRequestProperty("Authorization", authenticationHeader);
                }
                connection.setRequestMethod(requestMethod);
                if ((requestMethod.equalsIgnoreCase("POST") || requestMethod.equalsIgnoreCase("PUT")) && request != null) {
                    connection.setDoInput(true);
                    connection.setDoOutput(true);
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()))) {
                        writer.write(request);
                    }
                }
                Enumeration<String> keys = config.keys();
                while (keys.hasMoreElements()) {
                    String key = keys.nextElement();
                    if (key.startsWith("header.")) {
                        connection.setRequestProperty(key.substring("header.".length()), (String) config.get(key));
                    }
                }
                data.putAll(unmarshaller.unmarshal(connection.getInputStream()));
                data.put("http.response.code", connection.getResponseCode());
                data.put("http.response.message", connection.getResponseMessage());
                data.put("type", "rest");
                data.put("url", urlWithPath);

                PropertiesPreparator.prepare(data, config);

                data.put("service.hostName", url.getHost());
            } catch (Exception e) {
                LOGGER.warn("Can't request REST service", e);
                if (exceptionAsHttpResponse) {
                    if (exceptionHttpResponseCode != null) {
                        data.put("http.response.code", exceptionHttpResponseCode);
                    } else {
                        try {
                            data.put("http.response.code", connection.getResponseCode());
                        } catch (Exception ie) {
                            // no-op
                        }
                    }
                    data.put("http.exception", e.getClass().getName() + ": " + e.getMessage());
                    data.put("type", "rest");
                    data.put("url", urlWithPath);
                } else {
                    data.put("type", "rest");
                    data.put("url", urlWithPath);
                    data.put("error", e.getClass().getName() + ": " + e.getMessage());
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            dispatcher.postEvent(new Event(topic, data));
        }
        LOGGER.debug("Karaf Decanter REST Collector harvesting done");
    }

}
