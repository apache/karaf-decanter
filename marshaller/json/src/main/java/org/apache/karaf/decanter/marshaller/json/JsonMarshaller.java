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
package org.apache.karaf.decanter.marshaller.json;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;

@Component(
    immediate = true,
    property = Marshaller.SERVICE_KEY_DATAFORMAT + "=json"
)
public class JsonMarshaller implements Marshaller {

    private SimpleDateFormat tsFormat;
    
    public JsonMarshaller() {
        tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSSX");
        tsFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void marshal(Object obj, OutputStream out) {
        JsonObject jsonObj = marshal((Event)obj);
        JsonWriter writer = Json.createWriter(out);
        writer.writeObject(jsonObj);
        writer.close();
    }
    
    @Override
    public String marshal(Object obj) {
        return marshal((Event)obj).toString();
    }

    private JsonObject marshal(Event event) {
        JsonObjectBuilder json = Json.createObjectBuilder();
        addTimestamp(event, json);
        for (String key : event.getPropertyNames()) {
            Object value = event.getProperty(key);
            marshalAttribute(json, key.replace('.','_'), value);
        }
        return json.build();
    }

    private void addTimestamp(Event event, JsonObjectBuilder json) {
        Long ts = (Long)event.getProperty(EventConstants.TIMESTAMP);
        Date date = ts != null ? new Date(ts) : new Date();
        json.add("@timestamp", tsFormat.format(date));
    }

    @SuppressWarnings("unchecked")
    private void marshalAttribute(JsonObjectBuilder jsonObjectBuilder, String key, Object value) {
        key = key.replace('.', '_');
        if (value instanceof Map) {
            jsonObjectBuilder.add(key, build((Map<String, Object>)value));
        } else if (value instanceof List) {
            jsonObjectBuilder.add(key, build((List<?>)value));
        } else if (value instanceof long[] || value instanceof Long[]) {
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            long[] array = (long[])value;
            for (long l : array) {
                arrayBuilder.add(l);
            }
            jsonObjectBuilder.add(key, arrayBuilder.build());
        } else if (value instanceof int[] || value instanceof Integer[]) {
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            int[] array = (int[])value;
            for (int i : array) {
                arrayBuilder.add(i);
            }
            jsonObjectBuilder.add(key, arrayBuilder.build());
        } else if (value instanceof String[]) {
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            String[] array = (String[])value;
            for (String s : array) {
                arrayBuilder.add(s);
            }
            jsonObjectBuilder.add(key, arrayBuilder.build());
        } else if (value instanceof Object[]) {
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            Object[] array = (Object[])value;
            for (Object o : array) {
                if (o != null) {
                    arrayBuilder.add(o.toString());
                }
            }
            jsonObjectBuilder.add(key, arrayBuilder.build());
        } else {
            addProperty(jsonObjectBuilder, key, value);
        }
    }

    private JsonObject build(Map<String, Object> value) {
        JsonObjectBuilder json = Json.createObjectBuilder();
        for (Entry<String, Object> entries : value.entrySet()) {
            addProperty(json, entries.getKey().replace('.','_'), entries.getValue());
        }
        return json.build();
    }

    private JsonArray build(List<?> values) {
        JsonArrayBuilder json = Json.createArrayBuilder();
        for (Object value : values) {
            addValue(json, value);
        }
        return json.build();
    }

    @SuppressWarnings("unchecked")
    private void addValue(JsonArrayBuilder json, Object value) {
        if (value instanceof Map) {
            json.add(build((Map<String, Object>)value));
        } else if (value instanceof BigDecimal) {
            json.add((BigDecimal)value);
        } else if (value instanceof BigInteger) {
            json.add((BigInteger)value);
        } else if (value instanceof String) {
            json.add((String)value);
        } else if (value instanceof Long) {
            json.add((Long)value);
        } else if (value instanceof Integer) {
            json.add((Integer)value);
        } else if (value instanceof Float) {
            json.add((Float)value);
        } else if (value instanceof Double) {
            json.add((Double)value);
        } else if (value instanceof Boolean) {
            json.add((Boolean)value);
        }
    }

    private void addProperty(JsonObjectBuilder json, String key, Object value) {
        key = key.replace('.','_');
        if (value instanceof BigDecimal) {
            json.add(key, (BigDecimal)value);
        } else if (value instanceof BigInteger) {
            json.add(key, (BigInteger)value);
        } else if (value instanceof String) {
            json.add(key, (String)value);
        } else if (value instanceof Long) {
            json.add(key, (Long)value);
        } else if (value instanceof Integer) {
            json.add(key, (Integer)value);
        } else if (value instanceof Float) {
            json.add(key, (Float)value);
        } else if (value instanceof Double) {
            if (Double.isInfinite((Double)value)) {
                json.add(key, "Infinity");
            } else if (Double.isNaN((Double)value)) {
                json.add(key, "NaN");
            } else {
                json.add(key, (Double)value);
            }
        } else if (value instanceof Boolean) {
            json.add(key, (Boolean)value);
        }
    }

}
