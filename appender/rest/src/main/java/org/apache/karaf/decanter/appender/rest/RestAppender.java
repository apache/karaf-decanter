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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Dictionary;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
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

    private final static Logger LOGGER = LoggerFactory.getLogger(RestAppender.class);
    private Marshaller marshaller;
    private URI uri;

    @Activate
    @SuppressWarnings("unchecked")
    public void activate(ComponentContext context) throws URISyntaxException {
        Dictionary<String, Object> config = context.getProperties();
        activate(config);
    }

    void activate(Dictionary<String, Object> config) throws URISyntaxException {
        uri = new URI(getMandatoryValue(config, "uri"));
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
        try {
            HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
            connection.setDoOutput(true); 
            connection.setInstanceFollowRedirects(false); 
            connection.setRequestMethod("POST"); 
            connection.setRequestProperty("Content-Type", "application/json"); 
            connection.setRequestProperty("charset", "utf-8");
            OutputStream out = connection.getOutputStream();
            marshaller.marshal(event, out);
            out.close();
            InputStream is = connection.getInputStream();
            is.read();
            is.close();
        } catch (Exception e) {
            LOGGER.warn("Error sending event to rest service", e);
        }
    }
    
    @Deactivate
    public void close() {
    }

    @Reference
    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }
}
