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

import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.marshaller.json.JsonMarshaller;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.Test;
import org.osgi.service.event.Event;

import java.util.*;


public class TestElasticsearchAppender {

    private static final String CLUSTER_NAME = "elasticsearch-test";
    private static final String HOST = "127.0.0.1";
    private static final int HTTP_PORT = 9201;

    @Test
    public void testAppender() throws Exception {

        Settings settings = Settings.builder()
                .put("cluster.name", CLUSTER_NAME)
                .put("node.name", "test")
                .put("http.enabled", "true")
                .put("http.type", "netty4")
                .put("path.home", "target/data")
                .put("path.data", "target/data")
                .put("network.host", HOST)
                .put("http.port", HTTP_PORT)
                .build();

        Collection plugins = Arrays.asList(Netty4Plugin.class);
        Node node = new PluginConfigurableNode(settings, plugins);

        node.start();

        Marshaller marshaller = new JsonMarshaller();
        ElasticsearchAppender appender = new ElasticsearchAppender();
        appender.setMarshaller(marshaller);
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("addresses", "http://" + HOST + ":" + HTTP_PORT);
        appender.open(config );
        appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
        appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
        appender.handleEvent(new Event("testTopic", MapBuilder.<String, String>newMapBuilder().put("a", "b").put("c", "d").map()));
        appender.close();

        HttpHost host = new HttpHost(HOST, HTTP_PORT, "http");
        RestClient client = RestClient.builder(new HttpHost[]{ host }).build();
        Response response = client.performRequest("GET", "/_count", Collections.EMPTY_MAP);

        System.out.println(EntityUtils.toString(response.getEntity()));

        String requestBody =
            "{ \"query\" : { \"match\" : { \"a\" : \"b\" }}}";
        NStringEntity request = new NStringEntity(requestBody, ContentType.APPLICATION_JSON);
        response = client.performRequest("GET", "/_search", Collections.EMPTY_MAP, request);
        System.out.println(EntityUtils.toString(response.getEntity()));

        /*
        int maxTryCount = 10;

        for(int i=0; node.client().count(Requests.countRequest()).actionGet().getCount() == 0 && i< maxTryCount; i++) {
            Thread.sleep(500);
        }

        Assert.assertEquals(3L, node.client().count(Requests.countRequest()).actionGet().getCount());
        node.close();
        */
    }

    private static class PluginConfigurableNode extends Node {
        public PluginConfigurableNode(Settings settings, Collection<Class<? extends Plugin>> classpathPlugins) {
            super(InternalSettingsPreparer.prepareEnvironment(settings, null), classpathPlugins);
        }
    }

}
