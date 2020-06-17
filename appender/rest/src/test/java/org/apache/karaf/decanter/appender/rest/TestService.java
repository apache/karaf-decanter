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
package org.apache.karaf.decanter.appender.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import java.util.ArrayList;
import java.util.List;

@Path("/")
public class TestService {

    public List<String> postMessages = new ArrayList<>();
    public List<String> putMessages = new ArrayList<>();

    @POST
    @Consumes("application/json")
    @Path("/echo")
    public String echoPost(String message) {
        postMessages.add(message);
        return message;
    }

    @PUT
    @Consumes("application/json")
    @Path("/echo")
    public String echoPut(String message) {
        putMessages.add(message);
        return message;
    }

}
