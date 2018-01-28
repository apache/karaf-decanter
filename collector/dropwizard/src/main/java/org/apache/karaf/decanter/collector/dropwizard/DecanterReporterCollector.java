/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.karaf.decanter.collector.dropwizard;

import com.codahale.metrics.*;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.collector.dropwizard",
        immediate = true,
        property = { "decanter.collector.name=dropwizard",
                "scheduler.period:Long=60",
                "scheduler.concurrent:Boolean=false",
                "scheduler.name=decanter-collector-dropwizard"}
)
public class DecanterReporterCollector implements Runnable {

    @Reference
    public EventAdmin dispatcher;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    public MetricSet metricRegistry;

    @Override
    public void run() {
        Map<String, Metric> metrics = metricRegistry.getMetrics();
        for (String metricName : metrics.keySet()) {
            Metric metric = metrics.get(metricName);
            Map<String, Object> data = new HashMap<>();
            data.put("type", "dropwizard");
            data.put("karafName", System.getProperty("karaf.name"));
            try {
                data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
                data.put("hostName", InetAddress.getLocalHost().getHostName());
            } catch (Exception e) {
                // nothing to do
            }
            if (metric instanceof Gauge) {
                Gauge gauge = (Gauge) metric;
                Object value = gauge.getValue();
                data.put("metric", "gauge");
                data.put("value", value);
            }
            if (metric instanceof Counter) {
                Counter counter = (Counter) metric;
                data.put("metric", "counter");
                data.put("count", counter.getCount());
            }
            if (metric instanceof Histogram) {
                Histogram histogram = (Histogram) metric;
                data.put("metric", "histogram");
                data.put("count", histogram.getCount());
                populateSnapshot(histogram.getSnapshot(), data);
            }
            if (metric instanceof Meter) {
                Meter meter = (Meter) metric;
                data.put("metric", "meter");
                data.put("count", meter.getCount());
                data.put("15 Minute Rate", meter.getFifteenMinuteRate());
                data.put("5 Minute Rate", meter.getFiveMinuteRate());
                data.put("1 Minute Rate", meter.getOneMinuteRate());
                data.put("Mean Rate", meter.getMeanRate());
            }
            if (metric instanceof Timer) {
                Timer timer = (Timer) metric;
                data.put("metric", "timer");
                data.put("count", timer.getCount());
                data.put("15 Minute Rate", timer.getFifteenMinuteRate());
                data.put("5 Minute Rate", timer.getFiveMinuteRate());
                data.put("1 Minute Rate", timer.getOneMinuteRate());
                data.put("Mean Rate", timer.getMeanRate());
                populateSnapshot(timer.getSnapshot(), data);
            }
            Event event = new Event("decanter/collect/dropwizard", data);
            dispatcher.postEvent(event);
        }
    }

    private void populateSnapshot(Snapshot snapshot, Map<String, Object> data) {
        data.put("75th Percentile", snapshot.get75thPercentile());
        data.put("95th Percentile", snapshot.get95thPercentile());
        data.put("98th Përcentile", snapshot.get98thPercentile());
        data.put("99th Percentile", snapshot.get99thPercentile());
        data.put("999th Percentile", snapshot.get999thPercentile());
        data.put("max", snapshot.getMax());
        data.put("mean", snapshot.getMean());
        data.put("median", snapshot.getMedian());
        data.put("min", snapshot.getMin());
        data.put("stddev", snapshot.getStdDev());
    }

}
