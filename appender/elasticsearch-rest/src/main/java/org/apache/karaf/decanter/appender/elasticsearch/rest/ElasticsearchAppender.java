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

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
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
    name ="org.apache.karaf.decanter.appender.elasticsearch.rest",
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    property=EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class ElasticsearchAppender implements EventHandler {

    @Reference
    public Marshaller marshaller;

    private RestClient client;
    private String indexPrefix;
    private boolean indexTimestamped;
    private String indexType;
    private String namesIncludes;
    private String namesExludes;
    private String valuesIncludes;
    private String valuesExcludes;

    private final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'");
    private final SimpleDateFormat indexDateFormat = new SimpleDateFormat("yyyy.MM.dd");

    private final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchAppender.class);

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        open(context.getProperties());
    }
    
    public void open(Dictionary<String, Object> config) {
        String addressesString = getValue(config, "addresses", "http://localhost:9200");
        String username = getValue(config, "username", null);
        String password = getValue(config, "password", null);

        Set<String> addresses = new HashSet<String>(Arrays.asList(addressesString.split(",")));

        HttpHost[] hosts = new HttpHost[addresses.size()];
        int i = 0;
        for (String address : addresses) {
            try {
                URL url = new URL(address);
                hosts[i] = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
                i++;
            } catch (Exception e) {
                LOGGER.warn("Bad elasticsearch address {}", address, e);
            }
        }
        RestClientBuilder restClientBuilder = RestClient.builder(hosts);

        restClientBuilder.setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
            @Override
            public RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder) {
                return requestConfigBuilder.setConnectTimeout(1000)
                        .setSocketTimeout(10000);
            }
        }).setMaxRetryTimeoutMillis(20000);

        if (username != null) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            restClientBuilder.setHttpClientConfigCallback(
                    new RestClientBuilder.HttpClientConfigCallback() {
                        @Override
                        public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpAsyncClientBuilder) {
                            return httpAsyncClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                        }
                    }
            );
        }

        client = restClientBuilder.build();

        TimeZone tz = TimeZone.getTimeZone( "UTC" );
        tsFormat.setTimeZone(tz);
        indexDateFormat.setTimeZone(tz);

        indexPrefix = getValue(config, "index.prefix", "karaf");
        indexTimestamped = Boolean.parseBoolean(getValue(config, "index.event.timestamped", "true"));
        indexType = getValue(config, "index.type", "decanter");
        namesExludes = getValue(config, "properties.names.excludes", null);
        namesIncludes = getValue(config, "properties.names.includes", null);
        valuesExcludes = getValue(config, "properties.values.excludes", null);
        valuesIncludes = getValue(config, "properties.values.includes", null);
    }
    
    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

    private String[] getValues(Dictionary<String, Object> config, String key, String separator, String[] defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value.split(separator) :  defaultValue;
    }

    @Deactivate
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            LOGGER.warn("Warning when closing elasticsearch client", e);
        }
    }

    @Override
    public void handleEvent(Event event) {
        try {
            for (String name : event.getPropertyNames()) {
                // create include names pattern, exclude names pattern, include values pattern, exclude values pattern
                if (namesExludes != null && name.matches(namesExludes)) {
                    return;
                }
                if (namesIncludes != null && name.matches(namesIncludes)) {
                    send(event);
                    return;
                }
                if (event.getProperty(name) != null && event.getProperty(name) instanceof String) {
                    if (valuesExcludes != null && ((String) event.getProperty(name)).matches(valuesExcludes)) {
                        return;
                    }
                    if (valuesIncludes != null && ((String) event.getProperty(name)).matches(valuesIncludes)) {
                        send(event);
                        return;
                    }
                }
            }
            send(event);
        } catch (Exception e) {
            LOGGER.warn("Can't append into Elasticsearch", e);
        }
    }

    private void send(Event event) throws Exception {
        String indexName = getIndexName(indexPrefix, getDate(event));
        String jsonSt = marshaller.marshal(event);

        // elasticsearch 6.x only allows one type per index mapping, the _type is part of the document
        String endpoint = String.format("/%s/%s", indexName, indexType);
        HttpEntity request = new NStringEntity(jsonSt, ContentType.APPLICATION_JSON);

        client.performRequest("POST", endpoint, Collections.singletonMap("refresh", "true"), request);
    }

    private Date getDate(Event event) {
        Long ts = (Long)event.getProperty("timestamp");
        Date date = ts != null ? new Date(ts) : new Date();
        return date;
    }

    private String getIndexName(String prefix, Date date) {
        if (indexTimestamped) {
            return prefix + "-" + indexDateFormat.format(date);
        } else {
            return prefix;
        }
    }

}
