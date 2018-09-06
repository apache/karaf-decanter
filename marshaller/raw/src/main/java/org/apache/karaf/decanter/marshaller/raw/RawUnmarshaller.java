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
package org.apache.karaf.decanter.marshaller.raw;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Component(immediate = true, property = Marshaller.SERVICE_KEY_DATAFORMAT + "=raw")
public class RawUnmarshaller implements Unmarshaller {

    private final static Logger LOGGER = LoggerFactory.getLogger(RawUnmarshaller.class);

    @Override
    public Map<String, Object> unmarshal(InputStream in) {
        Map<String, Object> data = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        try {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            data.put("payload", builder.toString());
        } catch (Exception e) {
            LOGGER.warn("Can't unmarshal", e);
        } finally {
            try {
                reader.close();
            } catch (Exception e) {
                // nothing to do
            }
        }
        return data;
    }

}
