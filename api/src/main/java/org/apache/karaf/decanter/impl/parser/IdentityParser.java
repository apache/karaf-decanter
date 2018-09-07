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
package org.apache.karaf.decanter.impl.parser;

import org.apache.karaf.decanter.api.parser.Parser;
import org.osgi.service.component.annotations.Component;

import java.util.HashMap;
import java.util.Map;

@Component(immediate = true, property = Parser.SERVICE_KEY_ID + "=identify")
public class IdentityParser implements Parser {

    @Override
    public Map<String, Object> parse(String line) {
        Map<String, Object> data = new HashMap<>();

        try {
            data.put("line", Integer.parseInt(line));
            return data;
        } catch (Exception e) {
            // nothing to do
        }

        try {
            data.put("line", Long.parseLong(line));
            return data;
        } catch (Exception e) {
            // nothing to do
        }

        data.put("line", line);
        return data;
    }
}
