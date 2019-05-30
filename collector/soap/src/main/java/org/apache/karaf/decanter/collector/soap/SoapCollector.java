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
package org.apache.karaf.decanter.collector.soap;

import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component(
        service = Runnable.class,
        name = "org.apache.karaf.decanter.collector.soap",
        immediate = true,
        property = {
                "decanter.collector.name=soap",
                "scheduler.period:Long=60",
                "scheduler.concurrent:Boolean=false",
                "scheduler.name=decanter-collector-soap"
        }
)
public class SoapCollector implements Runnable {

    @Reference
    public EventAdmin dispatcher;

    private final static Logger LOGGER = LoggerFactory.getLogger(SoapCollector.class);

    private URL url;
    private String soapRequest;
    private String topic;
    private Dictionary<String, Object> config;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> config) throws MalformedURLException {
        this.config = config;
        if (config.get("url") == null) {
            throw new IllegalArgumentException("url property is mandatory");
        }
        this.topic = "decanter/collect/soap";
        if (config.get("topic") != null) {
            this.topic = (String) config.get("topic");
        }
        url = new URL((String) config.get("url"));
        if (config.get("soap.request") == null) {
            throw new IllegalStateException("soap.request property is mandatory");
        }
        soapRequest = (String) config.get("soap.request");
    }

    @Override
    public void run() {
        Map<String, Object> data = new HashMap<>();
        data.put("soap.request", soapRequest);
        data.put("url", url);
        data.put("type", "soap");

        // custom fields
        Enumeration<String> keys = config.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            data.put(key, config.get(key));
        }

        try {
            PropertiesPreparator.prepare(data, config);
        } catch (Exception e) {
            LOGGER.warn("Can't prepare properties", e);
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestProperty("Content-Type", "text/xml");
            connection.setRequestProperty("Accept", "text/xml");
            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                writer.write(soapRequest);
                writer.flush();
                data.put("http.response.code", connection.getResponseCode());
                data.put("http.response.message", connection.getResponseMessage());
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder buffer = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        buffer.append(line).append("\n");
                    }
                    data.put("soap.response", buffer.toString());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Can't request SOAP service", e);
            data.put("error", e.getClass().getName() + ": " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        dispatcher.postEvent(new Event(topic, data));
    }

}
