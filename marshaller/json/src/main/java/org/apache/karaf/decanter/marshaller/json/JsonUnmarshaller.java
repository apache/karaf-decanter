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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.osgi.service.component.annotations.Component;

@Component(
        property = { Marshaller.SERVICE_KEY_DATAFORMAT + "=json" }
)
public class JsonUnmarshaller implements Unmarshaller {

    @Override
    public Map<String, Object> unmarshal(InputStream in) {
        JsonReader reader = Json.createReader(in);
        JsonObject jsonO = reader.readObject();
        HashMap<String, Object> map = new HashMap<>();
        for (String key : jsonO.keySet()) {
           map.put(key, unmarshalAttribute(jsonO.get(key))); 
        }
        reader.close();
        return map;
    }
    
    private Object unmarshalAttribute(JsonValue value) {
        if (value instanceof JsonNumber) {
            JsonNumber num = (JsonNumber)value;
            return num.isIntegral() ? num.longValue() : num.bigDecimalValue();
        } else if (value instanceof JsonString) {
            return ((JsonString)value).getString();
        } else {
            return null;
        }
    }

}
