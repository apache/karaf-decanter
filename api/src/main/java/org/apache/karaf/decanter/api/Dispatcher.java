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
 * Describe the dispatcher service responsible to calling all appender services available.
 */
public interface Dispatcher {

    /**
     * Call all appender services available to dispatch collected data.
     *
     * @param data the collected data to dispatch.
     * @throws Exception in case of appending failure.
     */
    public void dispatch(Map<Long, Map<String, Object>> data) throws Exception;

}
