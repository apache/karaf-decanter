# Decanter event format

The internal decanter event bus is implemented using EventAdmin topics.
By default decanter collectors should send to a (sub) topic `/decanter/collect`.

Event data is represented as event properties. For properties only the following data types are allowed:

* String
* Integer
* Long
* Double

## Timestamp

The event timestamp is stored with key "timestamp" and represents the creation time of the event in the first system collecting the event. It is represented as a Long like defined in Date. A milliseconds value represents the number of milliseconds that have passed since January 1, 1970 00:00:00.000 GMT. 

Every event must have a timestamp property.

## Type

The type of a message is stored in the property `type`  currently can be `log`  or `jmx`. 
