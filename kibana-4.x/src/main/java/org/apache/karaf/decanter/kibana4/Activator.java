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
package org.apache.karaf.decanter.kibana4;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.indices.Analyze;
import io.searchbox.indices.mapping.GetMapping;
import org.apache.karaf.features.FeatureEvent;
import org.apache.karaf.features.FeaturesListener;
import org.apache.karaf.features.RepositoryEvent;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Activator implements BundleActivator {

    private final static Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    private final static String KIBANA_INDEX = ".kibana";

    @Override
    public void start(BundleContext bundleContext) {
        LOGGER.debug("Starting Kibana 4 console ...");

        CollectorListener listener = new CollectorListener();
        bundleContext.registerService(FeaturesListener.class, listener, null);
    }

    @Override
    public void stop(BundleContext bundleContext) {
        LOGGER.debug("Stopping Kibana 4 console ...");
    }

    private void traverseJson(String parent, JsonObject jsonObject, Map<String, String> map) {
        if (jsonObject.entrySet().size() > 0) {
            for (Map.Entry<String, JsonElement> element : jsonObject.entrySet()) {
                if (element.getKey().equalsIgnoreCase("type") && element.getValue().isJsonPrimitive()) {
                    if (element.getValue().getAsString().equalsIgnoreCase("long") || element.getValue().getAsString().equalsIgnoreCase("double"))
                        map.put(parent, "number");
                    else if (element.getValue().getAsString().equalsIgnoreCase("object")) {
                        // bypass object
                    } else map.put(parent, element.getValue().getAsString());
                } else if (element.getValue().isJsonObject())
                    traverseJson(element.getKey(), element.getValue().getAsJsonObject(), map);
            }
        }
    }

    private JestClient createClient() {
        // TODO load the config from resources to get the Kibana index name
        // and location of the elasticsearch instance
        String address = "http://localhost:9200/";

        HttpClientConfig.Builder builder = new HttpClientConfig.Builder(address)
                .discoveryEnabled(true)
                .discoveryFrequency(1l, TimeUnit.MINUTES)
                .multiThreaded(true);
        JestClientFactory factory = new JestClientFactory();
        factory.setHttpClientConfig(builder.build());

        return factory.getObject();
    }

    private void updateIndex() {
        LOGGER.debug("Updating kibana index");

        JestClient client = createClient();

        try {
            GetMapping getMapping = new GetMapping.Builder().build();
            JestResult result = client.execute(getMapping);
            if (!result.isSucceeded()) {
                throw new IllegalStateException("Can't retrieve mappings");
            }
            JsonObject resultJson = result.getJsonObject();
            JsonObject karafIndex = resultJson.getAsJsonObject();

            Map<String, String> fields = new HashMap<>();

            for (Map.Entry<String, JsonElement> index : karafIndex.entrySet()) {
                if (index.getKey().startsWith("karaf")) {
                    traverseJson(index.getKey(), index.getValue().getAsJsonObject(), fields);
                }
            }

            LOGGER.debug("Updating .kibana index");
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("{ \"title\": \"*\",");
            stringBuilder.append("\"timeFieldName\": \"@timestamp\",");
            stringBuilder.append("\"fields\": \"[");

            for (String field : fields.keySet()) {
                stringBuilder.append("{");
                stringBuilder.append("\\\"name\\\":\\\"").append(field).append("\\\",");
                stringBuilder.append("\\\"type\\\":\\\"").append(fields.get(field)).append("\\\",");
                stringBuilder.append("\\\"count\\\":0,");
                stringBuilder.append("\\\"scripted\\\":false,");
                if (field.startsWith("_"))
                    stringBuilder.append("\\\"indexed\\\":false,");
                else stringBuilder.append("\\\"indexed\\\":true,");
                if (fields.get(field).equalsIgnoreCase("string"))
                    stringBuilder.append("\\\"analyzed\\\":false,");
                else stringBuilder.append("\\\"analyzed\\\":true,");
                stringBuilder.append("\\\"doc_values\\\":false");
                stringBuilder.append("},");
            }
            if (stringBuilder.charAt(stringBuilder.length() - 1) == ',')
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);

            stringBuilder.append("]\" }");

            result = client.execute(new Index.Builder(stringBuilder.toString()).index(KIBANA_INDEX).type("index-pattern").id("*").build());
            if (!result.isSucceeded()) {
                throw new IllegalStateException(result.getErrorMessage());
            }
            result = client.execute(new Index.Builder("{ \"buildNum\" : 7562, \"defaultIndex\": \"*\" }").index(KIBANA_INDEX).type("config").id("4.1.2-es-2.0").build());
            if (!result.isSucceeded()) {
                throw new IllegalStateException(result.getErrorMessage());
            }
            Analyze analyze = new Analyze.Builder().analyzer("standard").build();
            client.execute(analyze);
        } catch (Exception e) {
            LOGGER.warn("Can't update .kibana index", e);
        }
    }

    private boolean checkCollectedDataType(String type) {
        JestClient client = createClient();

        String query = "{ \"query\": { \"bool\" : { \"must\" : { \"query_string\" : { \"query\" : \"type:" + type + "\"}}}}}";

        Search search = new Search.Builder(query).build();
        try {
            SearchResult result = client.execute(search);
            if (result.getTotal() > 1) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            LOGGER.warn("Can't check data", e);
            return false;
        }
    }

    private void createLogDashboard() {
        JestClient client = createClient();

        try {
            String logLevels = "{\n" +
                    "      \"title\": \"Log Levels\",\n" +
                    "      \"visState\": \"{\\\"type\\\":\\\"pie\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"isDonut\\\":false},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"count\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"terms\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"level\\\",\\\"size\\\":5,\\\"order\\\":\\\"desc\\\",\\\"orderBy\\\":\\\"1\\\"}}],\\\"listeners\\\":{}}\",\n" +
                    "      \"description\": \"\",\n" +
                    "      \"kibanaSavedObjectMeta\": {\n" +
                    "        \"searchSourceJSON\": \"{\\\"index\\\":\\\"*\\\",\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"*\\\",\\\"analyze_wildcard\\\":true}},\\\"filter\\\":[]}\"\n" +
                    "      }\n" +
                    "    }\n";
            String loggers = "{\n" +
                    "      \"title\": \"Loggers\",\n" +
                    "      \"visState\": \"{\\\"type\\\":\\\"pie\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"isDonut\\\":false},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"count\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"terms\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"loggerName\\\",\\\"size\\\":5,\\\"order\\\":\\\"desc\\\",\\\"orderBy\\\":\\\"1\\\"}}],\\\"listeners\\\":{}}\",\n" +
                    "      \"description\": \"\",\n" +
                    "      \"kibanaSavedObjectMeta\": {\n" +
                    "        \"searchSourceJSON\": \"{\\\"index\\\":\\\"*\\\",\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"*\\\",\\\"analyze_wildcard\\\":true}},\\\"filter\\\":[]}\"\n" +
                    "      }\n" +
                    "    }\n";
            String dashboard = "{\n" +
                    "      \"title\": \"Log\",\n" +
                    "      \"description\": \"\",\n" +
                    "      \"panelsJSON\": \"[{\\\"id\\\":\\\"Loggers\\\",\\\"type\\\":\\\"visualization\\\",\\\"size_x\\\":6,\\\"size_y\\\":5,\\\"col\\\":1,\\\"row\\\":1},{\\\"id\\\":\\\"Log-Levels\\\",\\\"type\\\":\\\"visualization\\\",\\\"size_x\\\":6,\\\"size_y\\\":5,\\\"col\\\":7,\\\"row\\\":1}]\",\n" +
                    "      \"timeRestore\": false,\n" +
                    "      \"kibanaSavedObjectMeta\": {\n" +
                    "        \"searchSourceJSON\": \"{\\\"filter\\\":[{\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"*\\\",\\\"analyze_wildcard\\\":true}}}]}\"\n" +
                    "      }\n" +
                    "    }\n";

            client.execute(new Index.Builder(logLevels).index(KIBANA_INDEX).type("visualization").id("Log-Levels").build());
            client.execute(new Index.Builder(loggers).index(KIBANA_INDEX).type("visualization").id("Loggers").build());
            client.execute(new Index.Builder(dashboard).index(KIBANA_INDEX).type("dashboard").id("Log").build());
        } catch (Exception e) {
            LOGGER.warn("Can't create log dashboard", e);
        }
    }

    private void createSystemDashboard() {
        JestClient client = createClient();

        try {
            String memory = "{\n" +
                    "      \"title\": \"Memory\",\n" +
                    "      \"visState\": \"{\\\"type\\\":\\\"line\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"showCircles\\\":true,\\\"smoothLines\\\":false,\\\"interpolate\\\":\\\"linear\\\",\\\"scale\\\":\\\"linear\\\",\\\"drawLinesBetweenPoints\\\":true,\\\"radiusRatio\\\":9,\\\"times\\\":[],\\\"addTimeMarker\\\":false,\\\"defaultYExtents\\\":false,\\\"setYExtents\\\":false,\\\"yAxis\\\":{}},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"used\\\"}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"date_histogram\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"@timestamp\\\",\\\"interval\\\":\\\"auto\\\",\\\"customInterval\\\":\\\"2h\\\",\\\"min_doc_count\\\":1,\\\"extended_bounds\\\":{}}},{\\\"id\\\":\\\"3\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"max\\\"}},{\\\"id\\\":\\\"4\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"committed\\\"}}],\\\"listeners\\\":{}}\",\n" +
                    "      \"description\": \"\",\n" +
                    "      \"kibanaSavedObjectMeta\": {\n" +
                    "        \"searchSourceJSON\": \"{\\\"index\\\":\\\"*\\\",\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"*\\\",\\\"analyze_wildcard\\\":true}},\\\"filter\\\":[]}\"\n" +
                    "      }\n" +
                    "    }";

            String gc = "{\n" +
                    "      \"title\": \"Garbage Collector\",\n" +
                    "      \"visState\": \"{\\\"type\\\":\\\"line\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"showCircles\\\":true,\\\"smoothLines\\\":false,\\\"interpolate\\\":\\\"linear\\\",\\\"scale\\\":\\\"linear\\\",\\\"drawLinesBetweenPoints\\\":true,\\\"radiusRatio\\\":9,\\\"times\\\":[],\\\"addTimeMarker\\\":false,\\\"defaultYExtents\\\":false,\\\"setYExtents\\\":false,\\\"yAxis\\\":{}},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"CollectionCount\\\"}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"date_histogram\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"@timestamp\\\",\\\"interval\\\":\\\"auto\\\",\\\"customInterval\\\":\\\"2h\\\",\\\"min_doc_count\\\":1,\\\"extended_bounds\\\":{}}},{\\\"id\\\":\\\"3\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"CollectionTime\\\"}},{\\\"id\\\":\\\"4\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"duration\\\"}}],\\\"listeners\\\":{}}\",\n" +
                    "      \"description\": \"\",\n" +
                    "      \"kibanaSavedObjectMeta\": {\n" +
                    "        \"searchSourceJSON\": \"{\\\"index\\\":\\\"*\\\",\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"*\\\",\\\"analyze_wildcard\\\":true}},\\\"filter\\\":[]}\"\n" +
                    "      }\n" +
                    "    }\n";

            String classLoading = "{\n" +
                    "      \"title\": \"Class Loading\",\n" +
                    "      \"visState\": \"{\\\"type\\\":\\\"line\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"showCircles\\\":true,\\\"smoothLines\\\":false,\\\"interpolate\\\":\\\"linear\\\",\\\"scale\\\":\\\"linear\\\",\\\"drawLinesBetweenPoints\\\":true,\\\"radiusRatio\\\":9,\\\"times\\\":[],\\\"addTimeMarker\\\":false,\\\"defaultYExtents\\\":false,\\\"setYExtents\\\":false,\\\"yAxis\\\":{}},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"LoadedClassCount\\\"}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"date_histogram\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"@timestamp\\\",\\\"interval\\\":\\\"auto\\\",\\\"customInterval\\\":\\\"2h\\\",\\\"min_doc_count\\\":1,\\\"extended_bounds\\\":{}}},{\\\"id\\\":\\\"3\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"TotalLoadedClassCount\\\"}},{\\\"id\\\":\\\"4\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"UnloadedClassCount\\\"}}],\\\"listeners\\\":{}}\",\n" +
                    "      \"description\": \"\",\n" +
                    "      \"kibanaSavedObjectMeta\": {\n" +
                    "        \"searchSourceJSON\": \"{\\\"index\\\":\\\"*\\\",\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"*\\\",\\\"analyze_wildcard\\\":true}},\\\"filter\\\":[]}\"\n" +
                    "      }\n" +
                    "    }\n";

            String os = "{\n" +
                    "      \"title\": \"Operating System\",\n" +
                    "      \"visState\": \"{\\\"type\\\":\\\"line\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"showCircles\\\":true,\\\"smoothLines\\\":false,\\\"interpolate\\\":\\\"linear\\\",\\\"scale\\\":\\\"linear\\\",\\\"drawLinesBetweenPoints\\\":true,\\\"radiusRatio\\\":9,\\\"times\\\":[],\\\"addTimeMarker\\\":false,\\\"defaultYExtents\\\":false,\\\"setYExtents\\\":false,\\\"yAxis\\\":{}},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"FreePhysicalMemorySize\\\"}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"date_histogram\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"@timestamp\\\",\\\"interval\\\":\\\"auto\\\",\\\"customInterval\\\":\\\"2h\\\",\\\"min_doc_count\\\":1,\\\"extended_bounds\\\":{}}},{\\\"id\\\":\\\"3\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"FreeSwapSpaceSize\\\"}},{\\\"id\\\":\\\"4\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"OpenFileDescriptorCount\\\"}},{\\\"id\\\":\\\"5\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"ProcessCpuLoad\\\"}},{\\\"id\\\":\\\"6\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"SystemCpuLoad\\\"}}],\\\"listeners\\\":{}}\",\n" +
                    "      \"description\": \"\",\n" +
                    "      \"kibanaSavedObjectMeta\": {\n" +
                    "        \"searchSourceJSON\": \"{\\\"index\\\":\\\"*\\\",\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"*\\\",\\\"analyze_wildcard\\\":true}},\\\"filter\\\":[]}\"\n" +
                    "      }\n" +
                    "    }\n";

            String threads = "{\n" +
                    "      \"title\": \"Threads\",\n" +
                    "      \"visState\": \"{\\\"type\\\":\\\"line\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"showCircles\\\":true,\\\"smoothLines\\\":false,\\\"interpolate\\\":\\\"linear\\\",\\\"scale\\\":\\\"linear\\\",\\\"drawLinesBetweenPoints\\\":true,\\\"radiusRatio\\\":9,\\\"times\\\":[],\\\"addTimeMarker\\\":false,\\\"defaultYExtents\\\":false,\\\"setYExtents\\\":false,\\\"yAxis\\\":{}},\\\"aggs\\\":[{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"date_histogram\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"@timestamp\\\",\\\"interval\\\":\\\"auto\\\",\\\"customInterval\\\":\\\"2h\\\",\\\"min_doc_count\\\":1,\\\"extended_bounds\\\":{}}},{\\\"id\\\":\\\"3\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"ThreadCount\\\"}},{\\\"id\\\":\\\"4\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"DaemonThreadCount\\\"}},{\\\"id\\\":\\\"5\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"PeakThreadCount\\\"}}],\\\"listeners\\\":{}}\",\n" +
                    "      \"description\": \"\",\n" +
                    "      \"kibanaSavedObjectMeta\": {\n" +
                    "        \"searchSourceJSON\": \"{\\\"index\\\":\\\"*\\\",\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"*\\\",\\\"analyze_wildcard\\\":true}},\\\"filter\\\":[]}\"\n" +
                    "      }\n" +
                    "    }";

            String dashboard = "{\n" +
                    "      \"title\": \"System\",\n" +
                    "      \"description\": \"\",\n" +
                    "      \"panelsJSON\": \"[{\\\"id\\\":\\\"Class-Loading\\\",\\\"type\\\":\\\"visualization\\\",\\\"size_x\\\":4,\\\"size_y\\\":2,\\\"col\\\":6,\\\"row\\\":4},{\\\"id\\\":\\\"Garbage-Collector\\\",\\\"type\\\":\\\"visualization\\\",\\\"size_x\\\":6,\\\"size_y\\\":3,\\\"col\\\":1,\\\"row\\\":1},{\\\"id\\\":\\\"Memory\\\",\\\"type\\\":\\\"visualization\\\",\\\"size_x\\\":3,\\\"size_y\\\":2,\\\"col\\\":10,\\\"row\\\":4},{\\\"id\\\":\\\"Operating-System\\\",\\\"type\\\":\\\"visualization\\\",\\\"size_x\\\":6,\\\"size_y\\\":3,\\\"col\\\":7,\\\"row\\\":1},{\\\"id\\\":\\\"Threads\\\",\\\"type\\\":\\\"visualization\\\",\\\"size_x\\\":5,\\\"size_y\\\":2,\\\"col\\\":1,\\\"row\\\":4}]\",\n" +
                    "      \"timeRestore\": false,\n" +
                    "      \"kibanaSavedObjectMeta\": {\n" +
                    "        \"searchSourceJSON\": \"{\\\"filter\\\":[{\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"*\\\",\\\"analyze_wildcard\\\":true}}}]}\"\n" +
                    "      }\n" +
                    "    }\n";

            client.execute(new Index.Builder(memory).index(KIBANA_INDEX).type("visualization").id("Memory").build());
            client.execute(new Index.Builder(gc).index(KIBANA_INDEX).type("visualization").id("Garbage-Collector").build());
            client.execute(new Index.Builder(classLoading).index(KIBANA_INDEX).type("visualization").id("Class-Loading").build());
            client.execute(new Index.Builder(os).index(KIBANA_INDEX).type("visualization").id("Operating-System").build());
            client.execute(new Index.Builder(threads).index(KIBANA_INDEX).type("visualization").id("Threads").build());
            client.execute(new Index.Builder(dashboard).index(KIBANA_INDEX).type("dashboard").id("System").build());
        } catch (Exception e) {
            LOGGER.warn("Can't create system dashboard", e);
        }
    }

    class CollectorListener implements FeaturesListener {

        @Override
        public void featureEvent(FeatureEvent event) {
            // add a timeout to let some data to be populated
            if (event.getType().equals(FeatureEvent.EventType.FeatureInstalled)) {
                if (event.getFeature().getName().equalsIgnoreCase("decanter-collector-log")) {
                    LOGGER.info("Decanter Kibana detected installation of the decanter-collector-log feature");
                    // check if data has been appended
                    int i = 0;
                    while (!checkCollectedDataType("log*") && i < 10) {
                        try {
                            LOGGER.info("Checking log collected data");
                            Thread.sleep(500);
                        } catch (Exception e) {
                            // nothing to do
                        }
                        i++;
                    }
                    updateIndex();
                    createLogDashboard();
                }
                if (event.getFeature().getName().equalsIgnoreCase("decanter-collector-jmx")) {
                    LOGGER.info("Decanter Kibana detected installation of the decanter-collector-log feature");
                    // check if data has been appended
                    int i = 0;
                    while (!checkCollectedDataType("jmx*") && i < 10) {
                        try {
                            LOGGER.info("Checking jmx collected data");
                            Thread.sleep(500);
                        } catch (Exception e) {
                            // nothing to do
                        }
                        i++;
                    }
                    updateIndex();
                    createSystemDashboard();
                }
            }
        }

        @Override
        public void repositoryEvent(RepositoryEvent event) {
            // nothing to do
        }

    }

}
