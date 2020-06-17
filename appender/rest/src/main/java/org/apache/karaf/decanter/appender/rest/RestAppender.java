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
package org.apache.karaf.decanter.appender.rest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Dictionary;
import java.util.Enumeration;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    name = "org.apache.karaf.decanter.appender.rest",
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class RestAppender implements EventHandler {

    public static final String URI_PROPERTY = "uri";

    @Reference
    public Marshaller marshaller;

    private final static Logger LOGGER = LoggerFactory.getLogger(RestAppender.class);

    private URI uri;

    private Dictionary<String, Object> config;

    @Activate
    @SuppressWarnings("unchecked")
    public void activate(ComponentContext context) throws URISyntaxException {
        Dictionary<String, Object> config = context.getProperties();
        activate(config);
    }

    void activate(Dictionary<String, Object> config) throws URISyntaxException {
        this.config = config;
        uri = new URI(getMandatoryValue(config, URI_PROPERTY));
    }

    private String getMandatoryValue(Dictionary<String, Object> config, String key) {
        String value = (String)config.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing mandatory configuration " + key);
        } else {
            return value;
        }
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            try {
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setDoOutput(true);
                connection.setInstanceFollowRedirects(false);
                String user = config.get("user") != null ? (String) config.get("user") : null;
                String password = config.get("password") != null ? (String) config.get("password") : null;
                if (user != null) {
                    String authentication = user + ":" + password;
                    byte[] encodedAuthentication = Base64.getEncoder().encode(authentication.getBytes(StandardCharsets.UTF_8));
                    String authenticationHeader = "Basic " + new String(encodedAuthentication);
                    connection.setRequestProperty("Authorization", authenticationHeader);
                }
                String requestMethod = config.get("request.method") != null ? (String) config.get("request.method") : "POST";
                connection.setRequestMethod(requestMethod);
                String contentType = config.get("content.type") != null ? (String) config.get("content.type") : "application/json";
                connection.setRequestProperty("Content-Type",  contentType);
                String charset = config.get("charset") != null ? (String) config.get("charset") : "utf-8";
                connection.setRequestProperty("charset", charset);
                Enumeration<String> keys = config.keys();
                while (keys.hasMoreElements()) {
                    String key = keys.nextElement();
                    if (key.startsWith("header.")) {
                        connection.setRequestProperty(key.substring("header.".length()), (String) config.get(key));
                    }
                }
                String payloadHeader = config.get("payload.header") != null ? (String) config.get("payload.header") : null;
                if (payloadHeader != null) {
                    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        marshaller.marshal(event, out);
                        connection.setRequestProperty(payloadHeader, out.toString());
                    }
                } else {
                    try (OutputStream out = connection.getOutputStream()) {
                        marshaller.marshal(event, out);
                    }
                }
                InputStream is = connection.getInputStream();
                is.read();
                is.close();
            } catch (Exception e) {
                LOGGER.warn("Error sending event to rest service", e);
            }
        }
    }
    
    @Deactivate
    public void close() {
    }

}
