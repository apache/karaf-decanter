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

import java.util.List;
import java.util.Map;

import org.apache.camel.*;
import org.apache.camel.util.ObjectHelper;

/**
 * Adds the default data from the Exchange to the data map
 */
public class DefaultExchangeExtender implements DecanterCamelEventExtender {
    private boolean includeProperties = true;
    private boolean includeHeaders = true;
    private boolean includeBody = true;

    @Override
    public void extend(Map<String, Object> data, Exchange exchange) {
        data.put("fromEndpointUri", exchange.getFromEndpoint() != null ? exchange.getFromEndpoint().getEndpointUri() : null);
        setHistory(data, exchange);
        data.put("exchangeId", exchange.getExchangeId());
        data.put("routeId", exchange.getFromRouteId());
        data.put("camelContextName", exchange.getContext().getName());
        data.put("shortExchangeId", extractShortExchangeId(exchange));
        data.put("exchangePattern", exchange.getPattern().toString());
        if (includeProperties) {
            data.put("properties", exchange.getProperties());
        }
        if (includeHeaders) {
            data.put("inHeaders", exchange.getIn().getHeaders());
        }
        if (includeBody) {
            data.put("inBody", extractBodyAsString(exchange.getIn()));
        }
        data.put("inBodyType", getBodyTypeName(exchange.getIn()));
        if (exchange.hasOut()) {
            if (includeHeaders) {
                data.put("outHeaders", exchange.getOut().getHeaders());
            }
            if (includeBody) {
                data.put("outBody", extractBodyAsString(exchange.getOut()));
            }
            data.put("outBodyType", getBodyTypeName(exchange.getOut()));
        }
        data.put("causedByException", extractCausedByException(exchange));
    }
    
    private static String extractShortExchangeId(Exchange exchange) {
        return exchange.getExchangeId().substring(exchange.getExchangeId().indexOf("/") + 1);
    }

    private static void setHistory(Map<String, Object> data, Exchange exchange) {
        List<MessageHistory> messageHistory = exchange.getProperty(Exchange.MESSAGE_HISTORY, List.class);
        if (messageHistory != null) {
            for (MessageHistory history : messageHistory) {
                String nodeId = history.getNode().getId();
                data.put(nodeId + ".route", history.getRouteId());
                data.put(nodeId + ".time", history.getTime());
                data.put(nodeId + ".elapsed", history.getElapsed());
                data.put(nodeId + ".label", history.getNode().getLabel());
                data.put(nodeId + ".shortName", history.getNode().getShortName());
            }
        }
    }

    private static String extractCausedByException(Exchange exchange) {
        Throwable cause = exchange.getException();
        if (cause == null) {
            cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        }
        return (cause != null) ? cause.toString() : null;
    }

    private static String extractBodyAsString(Message message) {
        if (message == null) {
            return null;
        } else {
            Object body = message.getBody();
            if (body instanceof String) {
                return (String)body;
            } else {
                StreamCache newBody = (StreamCache)message.getBody(StreamCache.class);
                if (newBody != null) {
                    message.setBody(newBody);
                }

                Object answer = message.getBody(String.class);
                if (answer == null) {
                    answer = message.getBody();
                }

                if (newBody != null) {
                    newBody.reset();
                }

                return answer != null ? answer.toString() : null;
            }
        }
    }

    private static String getBodyTypeName(Message message) {
        if (message == null) {
            return null;
        } else {
            String answer = ObjectHelper.classCanonicalName(message.getBody());
            return answer != null && answer.startsWith("java.lang.") ? answer.substring(10) : answer;
        }
    }

    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }
    
    public void setIncludeHeaders(boolean includeHeaders) {
        this.includeHeaders = includeHeaders;
    }
    
    public void setIncludeProperties(boolean includeProperties) {
        this.includeProperties = includeProperties;
    }
}
