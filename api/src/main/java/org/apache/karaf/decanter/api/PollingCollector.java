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
package org.apache.karaf.decanter.api;

import java.util.Map;

/**
 * Interface describing a Decanter polling collector service (for instance log messages, JMX metrics, etc)
 */
public interface PollingCollector extends Collector {

    /**
     * Collect data to send to the appender.
     *
     * @return the list of collected data.
     * @throws Exception in case of collection failure.
     */
    public Map<Long, Map<String, Object>> collect() throws Exception;

}
