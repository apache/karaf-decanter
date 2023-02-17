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
package org.apache.karaf.decanter.collector.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBase;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.SimpleQueryStringQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component(
        name = "org.apache.karaf.decanter.collector.elasticsearch",
        immediate = true,
        property = { "decanter.collector.name=elasticsearch",
                "scheduler.period:Long=60",
                "scheduler.concurrent:Boolean=false",
                "scheduler.name=decanter-collector-elasticsearch"}
)
public class ElasticsearchCollector implements Runnable {

    private final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchCollector.class);

    @Reference
    private EventAdmin dispatcher;

    private Dictionary<String, Object> config;
    private RestClient restClient;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> configuration) {
        this.config = configuration;
        String addressesString = (configuration.get("addresses") != null) ? configuration.get("addresses").toString() : "http://localhost:9200";
        String username = (configuration.get("username") != null) ? configuration.get("username").toString() : null;
        String password = (configuration.get("password") != null) ? configuration.get("password").toString() : null;

        Set<String> addresses = new HashSet<>(Arrays.asList(addressesString.split(",")));

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

        restClientBuilder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder.setConnectTimeout(1000)
                .setSocketTimeout(10000));

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

        restClient = restClientBuilder.build();
    }

    @Deactivate
    public void deactivate() {
        try {
            restClient.close();
        } catch (Exception e) {
            LOGGER.warn("Warning when closing elasticsearch client", e);
        }
    }

    @Override
    public void run() {
        SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();

        String index = (config.get("index") != null) ? config.get("index").toString() : "decanter";
        searchRequestBuilder.index(index);

        String query = (config.get("query") != null) ? config.get("query").toString() : null;
        QueryBase queryBase;

        if (query == null) {
            searchRequestBuilder.q(QueryBuilders.matchAll().build().toString());
        } else {
            searchRequestBuilder.q(query);
        }

        String fromString = (config.get("from") != null) ? config.get("from").toString() : null;
        if (fromString != null) {
            int from = Integer.parseInt(fromString);
            searchRequestBuilder.from(from);
        }
        String sizeString = (config.get("size") != null) ? config.get("size").toString() : null;
        if (sizeString != null) {
            int size = Integer.parseInt(sizeString);
            searchRequestBuilder.size(size);
        }
        String timeoutString = (config.get("timeout") != null) ? config.get("timeout").toString() : null;
        if (timeoutString != null) {
            searchRequestBuilder.timeout(timeoutString);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("type", "elasticsearch");
        try {
            ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            new ElasticsearchAsyncClient(transport).search(searchRequestBuilder.build(), Map.class)
                    .thenApply((response) -> {
                        data.put("timeout", response.timedOut());
                        data.put("totalShards", response.shards().total().intValue());
                        data.put("successfulShards", response.shards().successful().intValue());
                        data.put("failedShards", response.shards().failed().intValue());
                        for (Hit hit : response.hits().hits()) {
                            data.putAll(hit.fields());
                        }
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Can't query elasticsearch", e);
        }
        try {
            PropertiesPreparator.prepare(data, config);
        } catch (Exception e) {
            LOGGER.warn("Can't prepare event", e);
        }

        String topic = (config.get(EventConstants.EVENT_TOPIC) != null) ? (String) config.get(EventConstants.EVENT_TOPIC) : "decanter/collect/elasticsearch";

        dispatcher.postEvent(new Event(topic, data));
    }

    /**
     * Visible for testing
     */
    public void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }

}
