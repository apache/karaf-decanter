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
package org.apache.karaf.decanter.alerting.service;

import org.apache.karaf.decanter.alerting.service.model.Rule;
import org.osgi.service.event.Event;

import java.util.List;

public interface Store {

    String store(Event event) throws Exception;

    void cleanup() throws Exception;

    List<Alert> list() throws Exception;

    List<Alert> query(String queryString) throws Exception;

    void flag(String queryString, String ruleName) throws Exception;

    void delete(String queryString) throws Exception;

    void eviction() throws Exception;

}
