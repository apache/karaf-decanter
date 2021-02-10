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
package org.apache.karaf.decanter.appender.s3;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.UUID;

@Component(
        name = "org.apache.karaf.decanter.appender.s3",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class S3Appender implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(S3Appender.class);

    private Regions regions;
    private String accessKeyId;
    private String secretKeyId;
    private String bucket;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> config) {
        if (config.get("accessKeyId") == null) {
            throw new IllegalStateException("accessKeyId is not set");
        }
        accessKeyId = (String) config.get("accessKeyId");
        if (config.get("secretKeyId") == null) {
            throw new IllegalStateException("secretKeyId is not set");
        }
        secretKeyId = (String) config.get("secretKeyId");
        if (config.get("bucket") == null) {
            throw new IllegalStateException("bucket is not set");
        }
        bucket = (String) config.get("bucket");
        regions = (config.get("region") != null) ? Regions.fromName((String) config.get("region")) : Regions.DEFAULT_REGION;
    }

    @Override
    public void handleEvent(Event event) {
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKeyId, secretKeyId);
        AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withRegion(regions)
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .build();
        String key = "decanter-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString();
        s3.putObject(bucket, key, marshaller.marshal(event));
    }

    @Reference
    public Marshaller marshaller;

}
