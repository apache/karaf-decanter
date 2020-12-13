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

# Apache Karaf Decanter

Apache Karaf Decanter is a complete monitoring platform for Apache Karaf.
It is very extensible, flexible, easy to install and use.

## Overview

* **Collectors**: The collectors are responsible of harvesting monitoring data.
    Two kinds of collectors are available:
      * the polling collectors are invoked periodically by a scheduler
      * the event driven collectors react to some events.
    It's very dynamic (thanks to the OSGi services), so it's possible to add
    a new custom collector (user/custom implementations).
* **Dispatcher**: The dispatcher is called by the scheduler or the event driven collectors
    to dispatch the collected data to the appenders.
* **Appenders**: The appenders are responsible to send/store the collected data to target
    backend systems.
* **Processor**: The processors can manipulate the internal Decanter events between the 
    collectors and the appenders. Decanter provides ready to use processors (aggregate,
    groupBy, camel, ...).
* **Alerting**: the alerting layer provides a checker, responsible of testing values of
      harvested data (coming from the collectors) and send alerts when the data
      is not in the expected state.

## Getting Started

Apache Karaf Decanter is available as a Karaf features. The following command registers
the Karaf Decanter features repository.

```
karaf@root()> feature:repo-add decanter
```

Depending of what you want to monitor and collect, you have to install the corresponding features
using `feature:install` command.

## Features

See user guide for the details of Decanter features.

### Collectors

* `decanter-collector-camel`
* `decanter-collector-configadmin
* `decanter-collector-dropwizard`
* `decanter-collector-eventadmin`
* `decanter-collector-file`
* `decanter-collector-jdbc`
* `decanter-collector-jetty`
* `decanter-collector-jms`
* `decanter-collector-jmx`
* `decanter-collector-kafka`
* `decanter-collector-log`
* `decanter-collector-log4j-socket`
* `decanter-collector-mqtt`
* `decanter-collector-oshi`
* `decanter-collector-process`
* `decanter-collector-prometheus`
* `decanter-collector-redis`
* `decanter-collector-rest`
* `decanter-collector-rest-servlet`
* `decanter-collector-snmp`
* `decanter-collector-soap`
* `decanter-collector-socket`
* `decanter-collector-system`

### Appenders

* `decanter-appender-camel`
* `decanter-appender-cassandra`
* `decanter-appender-dropwizard`
* `decanter-appender-elasticsearch`
* `decanter-appender-file`
* `decanter-appender-influxdb`
* `decanter-appender-jdbc`
* `decanter-appender-jms`
* `decanter-appender-kafka`
* `decanter-appender-log`
* `decanter-appender-mongodb`
* `decanter-appender-mqtt`
* `decanter-appender-orientdb`
* `decanter-appender-prometheus`
* `decanter-appender-redis`
* `decanter-appender-rest`
* `decanter-appender-socket`
* `decanter-appender-timescaledb`
* `decanter-appender-websocket`

### Processors

* `decanter-processor-passthrough`
* `decanter-processor-aggregate`
* `decanter-processor-groupby`
* `decanter-processor-camel`

### Alerters

* `decanter-alerting-log`
* `decanter-alerting-email`
* `decanter-alerting-camel`

Thanks for using Apache Karaf Decanter !


**The Apache Karaf Team**
