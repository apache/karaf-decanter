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
package org.apache.karaf.decanter.collector.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RouteNode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.interceptor.TraceEventHandler;
import org.apache.camel.processor.interceptor.TraceInterceptor;
import org.apache.camel.spi.TracedRouteNodes;
import org.apache.camel.util.MessageHelper;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.net.InetAddress;
import java.util.HashMap;

public class DecanterTraceEventHandler implements TraceEventHandler {

    private EventAdmin eventAdmin;
    private DecanterCamelEventExtender extender = null;

    public DecanterTraceEventHandler() {
    }

    public EventAdmin getEventAdmin() {
        return eventAdmin;
    }

    public void setExtender(DecanterCamelEventExtender extender) {
        this.extender = extender;
    }

    public void setEventAdmin(EventAdmin eventAdmin) {
        this.eventAdmin = eventAdmin;
    }

    @Override
    public void traceExchange(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
        HashMap<String, Object> data = new HashMap<>();
        data.put("type", "camelTracer");
        data.put("karafName", System.getProperty("karaf.name"));
        data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
        data.put("hostName", InetAddress.getLocalHost().getHostName());
        data.put("nodeId", node.getId());
        data.put("timestamp", System.currentTimeMillis());
        data.put("fromEndpointUri", exchange.getFromEndpoint() != null ? exchange.getFromEndpoint().getEndpointUri() : null);
        data.put("previousNode", extractFromNode(exchange));
        data.put("toNode", extractToNode(exchange));
        data.put("exchangeId", exchange.getExchangeId());
        data.put("routeId", exchange.getFromRouteId());
        data.put("camelContextName", exchange.getContext().getName());
        data.put("shortExchangeId", extractShortExchangeId(exchange));
        data.put("exchangePattern", exchange.getPattern().toString());
        for (String property : exchange.getProperties().keySet()) {
            if (property.startsWith("decanter.")) {
                data.put(property.substring("decanter.".length()), exchange.getProperties().get(property));
            }
        }
        data.put("properties", exchange.getProperties().isEmpty() ? null : exchange.getProperties());
        for (String header : exchange.getIn().getHeaders().keySet()) {
            if (header.startsWith("decanter.")) {
                data.put(header.substring("decanter.".length()), exchange.getIn().getHeader(header));
            }
        }
        data.put("inHeaders", exchange.getIn().getHeaders().isEmpty() ? null : exchange.getIn().getHeaders());
        data.put("inBody", MessageHelper.extractBodyAsString(exchange.getIn()));
        data.put("inBodyType", MessageHelper.getBodyTypeName(exchange.getIn()));
        if (exchange.hasOut()) {
            data.put("outHeaders", exchange.getOut().getHeaders().isEmpty() ? null : exchange.getOut().getHeaders());
            data.put("outBody", MessageHelper.extractBodyAsString(exchange.getOut()));
            data.put("outBodyType", MessageHelper.getBodyTypeName(exchange.getOut()));
        }
        data.put("causedByException", extractCausedByException(exchange));
        if (extender != null) {
            extender.extend(data, exchange);
        }
        Event event = new Event("decanter/collect/camel/tracer", data);
        eventAdmin.postEvent(event);
    }

    @Override
    public Object traceExchangeIn(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
        traceExchange(node, target, traceInterceptor, exchange);
        return null;
    }

    @Override
    public void traceExchangeOut(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange, Object traceState) throws Exception {
        traceExchange(node, target, traceInterceptor, exchange);
    }

    private static String extractShortExchangeId(Exchange exchange) {
        return exchange.getExchangeId().substring(exchange.getExchangeId().indexOf("/") + 1);
    }

    private static String extractFromNode(Exchange exchange) {
        if (exchange.getUnitOfWork() == null) {
            return null;
        }
        TracedRouteNodes traced = exchange.getUnitOfWork().getTracedRouteNodes();
        RouteNode last = traced.getSecondLastNode();
        return last != null ? last.getLabel(exchange) : null;
    }

    private static String extractToNode(Exchange exchange) {
        if (exchange.getUnitOfWork() == null) {
            return null;
        }
        TracedRouteNodes traced = exchange.getUnitOfWork().getTracedRouteNodes();
        RouteNode last = traced.getLastNode();
        return last != null ? last.getLabel(exchange) : null;
    }

    private static String extractCausedByException(Exchange exchange) {
        Throwable cause = exchange.getException();
        if (cause == null) {
            cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        }
        return (cause != null) ? cause.toString() : null;
    }

}
