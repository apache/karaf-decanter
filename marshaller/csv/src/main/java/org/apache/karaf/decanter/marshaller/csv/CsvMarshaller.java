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
package org.apache.karaf.decanter.marshaller.csv;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Dictionary;

@Component(
        name = "org.apache.karaf.decanter.marshaller.csv",
        immediate = true,
        property = Marshaller.SERVICE_KEY_DATAFORMAT + "=csv"
)
public class CsvMarshaller implements Marshaller {

    private final static Logger LOGGER = LoggerFactory.getLogger(CsvMarshaller.class);

    private String separator = ",";

    @Activate
    public void activate(ComponentContext componentContext) {
        Dictionary<String, Object> config = componentContext.getProperties();
        separator = (config.get("separator") != null) ? (String) config.get("separator") : ",";
    }

    @Override
    public void marshal(Object obj, OutputStream out) {
        String result = marshal(obj);
        OutputStreamWriter writer = new OutputStreamWriter(out);
        try {
            writer.write(result);
        } catch (Exception e) {
            LOGGER.warn("Can't marshal on the output stream", e);
        }
    }

    @Override
    public String marshal(Object obj) {
        return marshal((Event) obj);
    }

    private String marshal(Event event) {
        StringBuilder builder = new StringBuilder();
        for (String propertyName : event.getPropertyNames()) {
            Object propertyValue = event.getProperty(propertyName);
            builder.append(propertyName).append("=").append(propertyValue.toString()).append(separator);
        }
        String result = builder.toString();
        result = result.substring(0, result.length() - 1);
        return result;
    }

}
