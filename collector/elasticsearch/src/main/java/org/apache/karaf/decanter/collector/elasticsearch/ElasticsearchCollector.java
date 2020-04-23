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

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
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

    private Dictionary<String, Object> configuration;
    private RestHighLevelClient restClient;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> configuration) {
        this.configuration = configuration;
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

        restClient = new RestHighLevelClient(restClientBuilder);
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
        SearchRequest searchRequest = new SearchRequest();

        String index = (configuration.get("index") != null) ? configuration.get("index").toString() : "decanter";
        searchRequest.indices(index);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        String query = (configuration.get("query") != null) ? configuration.get("query").toString() : null;
        QueryBuilder queryBuilder;
        if (query == null) {
            queryBuilder = QueryBuilders.matchAllQuery();
        } else {
            queryBuilder = QueryBuilders.queryStringQuery(query);
        }
        searchSourceBuilder.query(queryBuilder);
        String fromString = (configuration.get("from") != null) ? configuration.get("from").toString() : null;
        if (fromString != null) {
            int from = Integer.parseInt(fromString);
            searchSourceBuilder.from(from);
        }
        String sizeString = (configuration.get("size") != null) ? configuration.get("size").toString() : null;
        if (sizeString != null) {
            int size = Integer.parseInt(sizeString);
            searchSourceBuilder.size(size);
        }
        String timeoutString = (configuration.get("timeout") != null) ? configuration.get("timeout").toString() : null;
        if (timeoutString != null) {
            int timeout = Integer.parseInt(timeoutString);
            searchSourceBuilder.timeout(new TimeValue(timeout, TimeUnit.SECONDS));
        }
        searchRequest.source(searchSourceBuilder);

        Map<String, Object> data = new HashMap<>();
        data.put("type", "elasticsearch");
        try {
            SearchResponse searchResponse = restClient.search(searchRequest, RequestOptions.DEFAULT);
            RestStatus status = searchResponse.status();
            TimeValue took = searchResponse.getTook();
            Boolean terminatedEarly = searchResponse.isTerminatedEarly();
            boolean timedOut = searchResponse.isTimedOut();
            int totalShards = searchResponse.getTotalShards();
            int successfulShards = searchResponse.getSuccessfulShards();
            int failedShards = searchResponse.getFailedShards();
            SearchHits hits = searchResponse.getHits();
            for (SearchHit hit : hits) {
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                data.putAll(sourceAsMap);
            }
        } catch (Exception e) {
            LOGGER.error("Can't query elasticsearch", e);
        }
        try {
            PropertiesPreparator.prepare(data, configuration);
        } catch (Exception e) {
            LOGGER.warn("Can't prepare event", e);
        }

        dispatcher.postEvent(new Event("decanter/collect/elasticsearch", data));
    }

    /**
     * Visible for testing
     */
    public void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }

}
