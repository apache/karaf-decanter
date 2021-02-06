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
package org.apache.karaf.decanter.appender.loki;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Dictionary;

@Component(
        name = "org.apache.karaf.decanter.appender.loki",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class LokiAppender implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(LokiAppender.class);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    public Marshaller marshaller;

    private String url;
    private String tenant = null;
    private String username = null;
    private String password = null;
    private Dictionary<String, Object> config;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> config) {
        this.config = config;
        url = (config.get("loki.url") != null) ? (String) config.get("loki.url") : "http://localhost:3100/loki/api/v1/push";
        tenant = (config.get("loki.tenant") != null) ? (String) config.get("loki.tenant") : null;
        username = (config.get("loki.username") != null) ? (String) config.get("loki.username") : null;
        password = (config.get("loki.password") != null) ? (String) config.get("loki.password") : null;
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            String log;
            if (marshaller != null) {
                log = marshaller.marshal(event);
            } else {
                StringBuilder builder = new StringBuilder();
                for (String innerKey : event.getPropertyNames()) {
                    builder.append(innerKey).append(":").append(toString(event.getProperty(innerKey))).append(" | ");
                }
                log = builder.toString();
            }
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                if (tenant != null) {
                    connection.setRequestProperty("X-Scope-OrgId", tenant);
                }
                if (username != null) {
                    String authentication = username + ":" + password;
                    byte[] encodedAuthentication = Base64.getEncoder().encode(authentication.getBytes(StandardCharsets.UTF_8));
                    String authenticationHeader = "Basic " + new String(encodedAuthentication);
                    connection.setRequestProperty("Authorization", authenticationHeader);
                }
                String jsonPush = "{\"streams\": [{ \"stream\": { \"job\": \"decanter\" }, \"values\": [ [ \"" + System.currentTimeMillis() * 1000L * 1000L + "\", \"" + log + "\" ] ] }]}";
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()))) {
                    writer.write(jsonPush);
                    writer.flush();
                }
                if (connection.getResponseCode() != 204) {
                    LOGGER.warn("Can't push to Loki ({}): {}", connection.getResponseCode(), connection.getResponseMessage());
                }
            } catch (Exception e) {
                LOGGER.warn("Error occurred while pushing to Loki", e);
            }
        }
    }

    private Object toString(Object value) {
        return value == null ? null : value.toString();
    }

}
