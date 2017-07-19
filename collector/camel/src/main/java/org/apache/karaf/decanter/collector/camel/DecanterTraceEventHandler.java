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

import java.net.InetAddress;
import java.util.HashMap;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.interceptor.TraceEventHandler;
import org.apache.camel.processor.interceptor.TraceInterceptor;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

public class DecanterTraceEventHandler implements TraceEventHandler {

    private EventAdmin eventAdmin;
    private DefaultExchangeExtender dextender = new DefaultExchangeExtender();
    private DecanterCamelEventExtender extender;

    @Override
    public void traceExchange(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
        HashMap<String, Object> data = new HashMap<>();
        data.put("type", "camelTracer");
        data.put("karafName", System.getProperty("karaf.name"));
        data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
        data.put("hostName", InetAddress.getLocalHost().getHostName());
        data.put("nodeId", node.getId());
        data.put("timestamp", System.currentTimeMillis());
        new DefaultExchangeExtender().extend(data, exchange);
        for (String property : exchange.getProperties().keySet()) {
            if (property.startsWith("decanter.")) {
                data.put(property.substring("decanter.".length()), exchange.getProperties().get(property));
            }
        }
        dextender.extend(data, exchange);
        if (extender != null)  {
            extender.extend(data, exchange);
        }
        for (String header : exchange.getIn().getHeaders().keySet()) {
            if (header.startsWith("decanter.")) {
                data.put(header.substring("decanter.".length()), exchange.getIn().getHeader(header));
            }
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
    
    public EventAdmin getEventAdmin() {
        return eventAdmin;
    }

    public void setExtender(DecanterCamelEventExtender extender) {
        this.extender = extender;
    }

    public void setEventAdmin(EventAdmin eventAdmin) {
        this.eventAdmin = eventAdmin;
    }
    
    public void setIncludeBody(boolean includeBody) {
        dextender.setIncludeBody(includeBody);
    }

    public void setIncludeHeaders(boolean includeHeaders) {
        dextender.setIncludeHeaders(includeHeaders);
    }

    public void setIncludeProperties(boolean includeProperties) {
        dextender.setIncludeProperties(includeProperties);
    }

}
