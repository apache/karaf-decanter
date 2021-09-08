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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.InterceptStrategy;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

public class DecanterInterceptStrategy implements InterceptStrategy {

    private EventAdmin dispatcher;
    private String topic = "decanter/collect/camel/tracer";
    private DefaultExchangeExtender dextender = new DefaultExchangeExtender();
    private DecanterCamelEventExtender extender;

    @Override
    public Processor wrapProcessorInInterceptors(CamelContext context, NamedNode definition, Processor target, Processor nextTarget) throws Exception {
        Processor answer = new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                send(exchange);
                target.process(exchange);
            }
        };
        return answer;
    }

    private void send(Exchange exchange) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("type", "camelTracer");
        data.put("karafName", System.getProperty("karaf.name"));
        try {
            data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
            data.put("hostName", InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            // no-op
        }
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
        Event event = new Event(topic, data);
        dispatcher.postEvent(event);
    }
    
    public EventAdmin getDispatcher() {
        return dispatcher;
    }

    public void setExtender(DecanterCamelEventExtender extender) {
        this.extender = extender;
    }

    public void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
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
