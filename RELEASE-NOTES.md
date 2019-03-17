<!--
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
-->

## Apache Karaf Decanter 2.2.0

### Overview

Apache Karaf Decanter 2.2.0 is a maintenance release on the 2.x series. It provides improvements and bug fixes.

Especially:

* New InfluxDB appender
* New TimescaleDB appender
* New websocket appender
* New SOAP collector
* File collector row parser support
* Appender property selector

Take a look on ChangeLog for details.

### ChangeLog

#### Bug
    * [KARAF-5998] - MongoDB appender component doesn't activate correctly
    * [KARAF-6047] - can not install feature elasticsearch
    * [KARAF-6146] - Decanter log alerter throws a NullPointerException

#### New Feature
    * [KARAF-6002] - Add websocket appender

#### Improvement
    * [KARAF-4777] - Add InfluxDB appender
    * [KARAF-5915] - Improve the API Parser adding key data as parameter
    * [KARAF-5972] - Add the row data collect type Numeric for the collector-jdbc
    * [KARAF-5976] - Add TimescaleDB appender
    * [KARAF-6009] - Be able to add property filters on appenders
    * [KARAF-6053] - Be able to define the marshaller in log appender
    * [KARAF-6054] - Reuse existing karafName, hostAddress, hostName if exist in "bridgeable" collectors
    * [KARAF-6055] - Kafka appender should perform send().get()
    * [KARAF-6117] - Provide split and regex parsers
    * [KARAF-6118] - Add SOAP collector
    * [KARAF-6161] - REST collector should populate the status code when connection doesn't work

#### Dependency upgrade
    * [KARAF-5905] - Update netty and netty handler versions in Karaf Decanter Cassandra
    * [KARAF-5924] - Update spring-boot starter to spring-boot 2.x
    * [KARAF-5936] - Upgrade to maven-scm-publish-plugin 3.0.0 and asciidoctor-maven-plugin 1.5.6
    * [KARAF-5960] - Upgrade to Apache POM 21

#### Documentation
    * [KARAF-5904] - Use the asciidoctor-maven-plugin and custom ASF theme for the manual generation

## Apache Karaf Decanter 2.1.0

### Overview

Apache Karaf Decanter 2.1.0 is a maintenance release on the 2.x series. It provides lot of improvements and bug fixes.

Especially:

* New JDBC collector
* Fix and improvements on the JMS collector and appender
* Upgrade Kafka collector and appender to support Kafka 1.x
* Improvements on the JMX collector to be able to execute MBean operations
* Add new raw and parser services

Take a look on ChangeLog for details.

### ChangeLog

#### Bug
    * [KARAF-5890] - Decanter JMS Collector/Appender features has a requirement on ConnectionFactory service
    * [KARAF-5891] - Decanter Log Collector creates a recursive log in Pax Logging

#### New Feature
    * [KARAF-5802] - Add JDBC collector

#### Improvement
    * [KARAF-5653] - Add a feature for the decanter manual
    * [KARAF-5747] - Add append file capability in decanter-appender-file
    * [KARAF-5754] - Make Decanter elasticsearch-jest appender support HTTPS/XPack enabled ES
    * [KARAF-5785] - Add JMXMP support in JMX collector
    * [KARAF-5792] - Support add, rename, remove to the custom fields of Decanter collectors
    * [KARAF-5793] - Provide an option replace the dot or not in json field in the JsonMarshaller
    * [KARAF-5799] - Be able to execute JMX operation in the collector
    * [KARAF-5801] - Add regex filtering on collector file
    * [KARAF-5864] - Add triming in system command output in the system collector to allow clean type conversion
    * [KARAF-5888] - Be able to define a line parser service in the file collector
    * [KARAF-5892] - Decanter JMS Collector should deal with "raw" message when no unmarshaller is provided
    * [KARAF-5894] - Introduce a raw marshaller/unmarshaller (identity)

#### Task
    * [KARAF-5889] - Enable RAT on Decanter and fix all missing ASF headers

#### Dependency upgrade
    * [KARAF-5659] - Upgrade to Kafka 1.1.x
    * [KARAF-5660] - Upgrade to elasticsearch 6.2.x (and corresponding bundle)
    * [KARAF-5744] - Upgrade to elasticsearch 6.2.4
    * [KARAF-5817] - Upgrade to maven-bundle-plugin 3.5.1
    * [KARAF-5884] - Upgrade Kafka Collector and Appender to support Kafka 1.x
    * [KARAF-5885] - Upgrade to commons-io 2.6 and improve Tailer configuration

## Apache Karaf Decanter 2.0.0

### Overview

Apache Karaf Decanter 2.0.0 is a new Decanter series, providing new features and bug fixes.

It's designed to work specifically on Apache Karaf 4.x.

Decanter is a completed monitoring and alerting solution for Apache Karaf container, and related applications
running on it.

You can use Decanter in Karaf version >= 4.

NB: Apache Karaf Decanter 2.0.0 still provides embedded backend instances, like Elasticsearch 6, Kibana 6, OrientDB
and much more. However, for production, we recommend to use a dedicated and isolatic instance of these backends.

### ChangeLog

#### Bug
    * [KARAF-5492] - Increase collectors scheduler period
    * [KARAF-5587] - Deal with client error in appenders

#### New Feature
    * [KARAF-4818] - Remove Jest client to use "native" REST elasticsearch client
    * [KARAF-5391] - Add decanter:alert* commands and MBeans
    * [KARAF-5462] - Provide Decanter DropWizard Metric integration
    * [KARAF-5479] - Provide a file appender
    * [KARAF-5501] - Upgrade to Kibana 6.x
    * [KARAF-5502] - Add OrientDB appender (with the corresponding inner feature)

#### Improvement
    * [KARAF-5454] - Collector socket - Add UDP protocol support
    * [KARAF-5510] - Add custom fields support in log collector and appenders
    * [KARAF-5557] - Support custom unmarshaller in socket collector
    * [KARAF-5575] - Upgrade file appender to use SCR config property
    * [KARAF-5577] - Define the marshaller in collector/appender configuration

#### Task
    * [KARAF-5576] - Move all backends in an unique module
    * [KARAF-5579] - Rename SLA bundles to Alerting
    * [KARAF-5580] - Rename tools-jar-wraper to tools-jar-wrapper

#### Dependency upgrade
    * [KARAF-5500] - Upgrade to Elasticsearch 5.x & 6.x

## Apache Karaf Decanter 1.4.0

### Overview

Apache Karaf Decanter 1.4.0 is a maintenance release, providing new features (Karaf scheduler, email alerter improvements, ...) and bug fixes.

Decanter is a completed monitoring and alerting solution for Apache Karaf container, and related applications
running on it.

You can use Decanter in any Karaf version.

### ChangeLog

#### Bug
    * [KARAF-4906] - Decanter e-mail alerter can't use javamail authentication
    * [KARAF-5141] - Decanter log collector seems to consume lot of resources on Karaf 4.1.x
    * [KARAF-5186] - [DECANTER] - Fails work in case the event topic contains arbitrary characters like %
    * [KARAF-5212] - Decanter Elasticsearch Appender feature should install com.fasterxml.jackson.core bundle
    * [KARAF-5238] - Embedded Kibana can block installation of decanter collectors
    * [KARAF-5239] - JMX collector doesn't full harvest metrics
    * [KARAF-5240] - Default Kibana dashboard doesn't render correctly

#### Dependency upgrade
    * [KARAF-4541] - Upgrade to Kafka 0.11.0.0

#### Improvement
    * [KARAF-3696] - Decanter: Switch to Karaf provided Scheduler (Cron-based)
    * [KARAF-4645] - Alerters should be throttable
    * [KARAF-4850] - Be able to specify several object names for the JMX collector
    * [KARAF-4929] - Email alerter should support list of destination (to) addresses
    * [KARAF-5060] - Be able to configure the index name in elasticsearch appenders
    * [KARAF-5192] - Decanter collector should be able to filter some loggers
    * [KARAF-5244] - Refactor kafka config handling

#### New Feature
    * [KARAF-3890] - Provide Decanter CXF interceptor collector
    * [KARAF-5033] - Provide a Camel EventNotifier collector

## Apache Karaf Decanter 1.3.0

### Overview

Apache Karaf Decanter 1.3.0 is a major new Decanter release, providing new features (new collectors, new
appenders) and bug fixes.

Decanter is a completed monitoring and alerting solution for Apache Karaf container, and related applications
running on it.

You can use Decanter in any Karaf version.

For details, see the ChangeLog:

### ChangeLog

#### Bug
    * [KARAF-4647] - Can't append into Elasticsearch with JMS
    * [KARAF-4757] - Decanter: elasticsearch-appender-rest shouldn't be dependent on http
    * [KARAF-4767] - Decanter: collectors and appenders can't be re-used without default configuration

#### Improvement
    * [KARAF-4749] - Support range, equal, and notequal for BigDecimal in org/apache/karaf/decanter/sla/checker/Checker.java
    * [KARAF-4791] - Decanter: elasticsearch-rest appender should support multipe addresses
    * [KARAF-4794] - Decanter: make sure the discovery is disabled if only one address is configured
    * [KARAF-4799] - JMS collector should support TextMessage

#### New Feature
    * [KARAF-4706] - Create MQTT collector
    * [KARAF-4742] - Decanter: Add Java Process JMX Collector

## Apache Karaf Decanter 1.2.0

### Overview

Apache Karaf Decanter 1.2.0 is a major new Decanter release, providing lot of new features (new collectors, new
appenders) and bug fixes.

Decanter is a completed monitoring and alerting solution for Apache Karaf container, and related applications
running on it.

You can use Decanter in any Karaf version.

###  ChangeLog

#### Bug
    * [KARAF-4516] - Extend Karaf feature import version range in Decanter Kibana bundle
    * [KARAF-4525] - Decanter log collector should not ignore the log alerter category
    * [KARAF-4532] - SystemCollector throws IOException
    * [KARAF-4574] - EventAdmin not injected in file collector
    * [KARAF-4580] - Installing elasticsearch feature fails on Windows
    * [KARAF-4594] - Log4J socket collector error handling mutiple clients
    * [KARAF-4604] - JsonUnMarshaller doesn't handle Map and Lists
    * [KARAF-4605] - Kafka Collector uses the same topic for eventadmin and kafka
    * [KARAF-4625] - JMS appender should use MapMessage property instead of JMS properties
    * [KARAF-4629] - Can't append into Elasticsearch

#### Dependency upgrade
    * [KARAF-4558] - Upgrade to Elasticsearch 1.7.4
    * [KARAF-4560] - Upgrade to ActiveMQ 5.13.3
    * [KARAF-4561] - Upgrade to Johnzon 0.9.3-incubating

#### Improvement
    * [KARAF-4565] - Set ConfigurationPolicy.REQUIRE for decanter appenders and collectors
    * [KARAF-4610] - Improve Decanter scheduler to reduce latency

#### New Feature
    * [KARAF-4298] - Add MongoDB appender
    * [KARAF-4320] - Create socket collector and appender
    * [KARAF-4530] - Create Kafka collector
    * [KARAF-4531] - Create JMS collector
    * [KARAF-4546] - Support embedding decanter into spring boot apps

## Apache Karaf Decanter 1.1.0

### Overview

Apache Karaf Decanter 1.1.0 is the first release on the Decanter 1.1.x serie. It's a complete new version of Decanter
including a lot of new features and bug fixes.

Decanter is a completed monitoring and alerting solution for Apache Karaf container, and related applications
running on it.

You can use Decanter in any Karaf version.

### ChangeLog

#### Bug
    * [KARAF-4059] - Exception thrown during elasticsearch restart
    * [KARAF-4121] - Escape characters in the log collection
    * [KARAF-4125] - Elasticsearch clusterName and nodeName can't be changed
    * [KARAF-4305] - Logs and JMX stats do not show in kibana with default settings
    * [KARAF-4312] - Have to provide a decanter src distribution
    * [KARAF-4341] - Add REST collector to pull metrics from jolokia
    * [KARAF-4375] - IllegalArgumentException: invalid topic exception
    * [KARAF-4432] - Marshaller is not able to deal with some event properties
    * [KARAF-4440] - Decanter eventadmin collector should cast type property as String
    * [KARAF-4448] - Type property is not correctly populated by the eventadmin collector
    * [KARAF-4453] - Marshaller doesn't include subject property
    * [KARAF-4480] - LogAppender should use timestamp instead of timeStamp

#### Dependency upgrade
    * [KARAF-3624] - Provide Kibana 4.1 feature
    * [KARAF-4331] - Upgrade to elasticsearch 2.2.0
    * [KARAF-4431] - Upgrade to kafka 0.9.0.0

#### Improvement
    * [KARAF-4113] - Be able to use a remote elasticsearch instance in Decanter Kibana
    * [KARAF-4170] - Authentication to access Kibana dashboard on karaf-decanter
    * [KARAF-4171] - Authentication to access elasticsearch-head on karaf-decanter
    * [KARAF-4295] - Use of Elasticsearch REST API in addition of the TransportClient (by configuration)
    * [KARAF-4300] - Centralize maven bundle plugin config
    * [KARAF-4304] - Cleanup elasticsearch code and build
    * [KARAF-4344] - Migrate decanter to DS
    * [KARAF-4369] - Collect bundle and other OSGi events in decanter
    * [KARAF-4430] - Kafka appender should deal with the ConnectException when the Kafka broker is not available
    * [KARAF-4438] - Define the max.request.size on the kafka appender configuration, and increase the default value
    * [KARAF-4463] - Secure Kibana 4
    * [KARAF-4466] - Add a note about elasticsearch & kibana best practice for large installation
    * [KARAF-4467] - decanter-appender-elasticsearch feature should be an alias to decanter-appender-elasticsearch-native-2.x
    * [KARAF-4481] - LogAppender should check if the event is ignored earlier to avoid useless processing
    * [KARAF-4495] - Add custom fields support in all collectors

#### New Feature
    * [KARAF-3698] - Add decanter cassandra appender
    * [KARAF-3773] - Add decanter redis appender
    * [KARAF-4120] - Provide elasticsearch 2.x feature
    * [KARAF-4291] - Add kafka appender
    * [KARAF-4296] - Add mqtt appender
    * [KARAF-4303] - Create decanter marshalling services
    * [KARAF-4321] - Add log4j socket collector
    * [KARAF-4368] - Support SSL for kafka appender
    * [KARAF-4404] - Create eventadmin collector
    * [KARAF-4443] - Create default dahboards in Decanter Kibana 4.x

## Apache Karaf Decanter 1.0.1

### Overview

Apache Karaf Decanter 1.0.1 is a fix version on the decanter-1.x serie. It also brings new features like the file collector.

Decanter is a completed monitoring and alerting solution for Apache Karaf container, and related applications
running on it.

You can use Decanter in any Karaf version.

### ChangeLog

#### Bug
    * [KARAF-4061] - Decanter ElasticSearchAppender - NumberFormatException

#### Dependency upgrade
    * [KARAF-4063] - Upgrade to elasticsearch 1.7.1
    * [KARAF-4101] - Upgrade to elasticsearch 1.7.3

#### Improvement
    * [KARAF-3979] - Be able to define a SLA check for a given type
    * [KARAF-4008] - Add custom fields support in JMX collector
    * [KARAF-4014] - Add a warning in Decanter System dashboard

#### New Feature
    * [KARAF-3904] - Add elasticsearch eshead plugin in embedded instance
    * [KARAF-3905] - Provide file collector (as in logstash)

## Apache Karaf Decanter 1.0.0

### Overview

Apache Karaf Decanter 1.0.0 is the first release of Decanter.

Decanter is a completed monitoring and alerting solution for Apache Karaf container, and related applications
running on it.

You can use Decanter in any Karaf version.

### ChangeLog

#### Bug
    * [KARAF-3815] - Decanter can't retrieve metrics for ObjectName containing white spaces
    * [KARAF-3855] - Decanter JMX collector create bunch of threads
    * [KARAF-3884] - ElasticsearchAppender sometimes throws NullPointer Exception when adding o.toString() to arrayBuilder
    * [KARAF-3889] - The clusterName for the elasticsearch appender isn't an optional configuration as it should be. 

#### Dependency upgrade
    * [KARAF-3845] - Upgrade to elasticsearch 1.6.0
    * [KARAF-3847] - Upgrade to kibana 3.1.2

#### Improvement
    * [KARAF-3777] - Add Karaf source IP address or hostname in the collected data
    * [KARAF-3836] - Add a configuration allowing to define the period of the simple scheduler
    * [KARAF-3848] - Be able to define the embedded elasticsearch node by configuration
    * [KARAF-3849] - Provide "key turn" kibana dashboards
    * [KARAF-3851] - Be able to poll remote MBeanServer
    * [KARAF-3883] - The elasticsearch appender uses the default clustername of Elasticsearch client, this needs to be configurable

#### New Feature
    * [KARAF-3637] - Decanter: JDBC appender
    * [KARAF-3771] - Add decanter system collector
    * [KARAF-3772] - Add Decanter SLA support
    * [KARAF-3861] - Add CamelAppender
    * [KARAF-3862] - Add CamelSLA
    * [KARAF-3863] - Add CamelTracer collector
    * [KARAF-3865] - Add decanter documentation
    * [KARAF-3866] - Add decanter JMS appender
    * [KARAF-3870] - Create SLA email alerting
    * [KARAF-3881] - The embedded ElasticSearch Node should be configurable through config-admin

#### Wish
    * [KARAF-3675] - Check if recent Johnzon snapshot is OSGi ready
