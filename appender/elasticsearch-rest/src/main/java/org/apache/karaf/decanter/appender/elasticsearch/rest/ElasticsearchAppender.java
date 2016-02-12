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
package org.apache.karaf.decanter.appender.elasticsearch.rest;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Index;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class ElasticsearchAppender implements EventHandler {

    private JestClient client;
    private Marshaller marshaller;

    private final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'");
    private final SimpleDateFormat indexDateFormat = new SimpleDateFormat("yyyy.MM.dd");

    private final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchAppender.class);

    public ElasticsearchAppender(Marshaller marshaller, String address, String username, String password) throws Exception {
        JestClientFactory factory = new JestClientFactory();
        if (username != null) {
            factory.setHttpClientConfig(new HttpClientConfig
                    .Builder(address)
                    .defaultCredentials(username, password)
                    .discoveryEnabled(true)
                    .discoveryFrequency(1l, TimeUnit.MINUTES)
                    .multiThreaded(true)
                    .build());
        } else {
            factory.setHttpClientConfig(new HttpClientConfig
                    .Builder(address)
                    .discoveryEnabled(true)
                    .discoveryFrequency(1l, TimeUnit.MINUTES)
                    .multiThreaded(true)
                    .build());
        }
        client = factory.getObject();
        this.marshaller = marshaller;
        TimeZone tz = TimeZone.getTimeZone( "UTC" );
        tsFormat.setTimeZone(tz);
        indexDateFormat.setTimeZone(tz);
    }

    public void close() {
        client.shutdownClient();
    }

    @Override
    public void handleEvent(Event event) {
        try {
            send(event);
        } catch (Exception e) {
            LOGGER.warn("Can't append into Elasticsearch", e);
        }
    }

    private void send(Event event) throws Exception {
        String indexName = getIndexName("karaf", getDate(event));
        String jsonSt = marshaller.marshal(event);

        JestResult result = client.execute(new Index.Builder(jsonSt).index(indexName).type(getType(event)).build());

        if (!result.isSucceeded()) {
            throw new IllegalStateException(result.getErrorMessage());
        }
    }

    private Date getDate(Event event) {
        Long ts = (Long)event.getProperty("timestamp");
        Date date = ts != null ? new Date(ts) : new Date();
        return date;
    }

    private String getType(Event event) {
        String type = (String)event.getProperty("type");
        return type != null ? type : "karaf_event";
    }

    private String getIndexName(String prefix, Date date) {
        return prefix + "-" + indexDateFormat.format(date);
    }

}
