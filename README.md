# Timeseries Integration InfluxDB 1.x Plugin

This plugin exposes an implementation of the TimeSeriesStorage interface.
It stores the data in a InfluxDB 1.x database.
It can be used in OpenNMS to store and retrieve timeseries data.

For the InfluxDB 2.0 plugin, see here: https://github.com/opennms-forge/timeseries-integration-influxdb/ 

## Prerequisite
* A running instance of InfluxDB 1.x must be available.
* For testing purposes you can run: ``docker run -p 8086:8086 influxdb``

## Usage

Build and install the plugin into your local Maven repository using:
```
mvn clean install
```

From the OpenNMS Karaf shell:
```
feature:repo-add mvn:org.opennms.plugins.timeseries/influxdb-karaf-features/1.0.0-SNAPSHOT/xml
feature:install opennms-plugins-influxdb
```

Initialize InfluxDB with default values:
```
opennms-influxdb:init
```

Use the create org and bucket:
```
config:edit org.opennms.plugins.influxdb
property-set url http://localhost:8086
property-set database opennms
property-set username root
property-set password password
config:update
```

Update automatically:
```
bundle:watch *
```

## Links:
* InfluxDB 1.x Java client: https://github.com/influxdata/influxdb-java
* InfluxDB 1.x: https://www.influxdata.com/time-series-platform/

