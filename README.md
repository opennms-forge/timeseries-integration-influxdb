# Timeseries Integration InfluxDB Plugin

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

Initialize InfluxDB with an organization and bucket:
```
opennms-influxdb:init --link http://localhost:9999
```

Use the create org and bucket:
```
config:edit org.opennms.plugins.influxdb
property-set bucket opennms
property-set org opennms
property-set token "FSuqxbAXTxgiI-6-KtYFZJZwJDfYpOOOEpgpBdOaX5zLo4MiFvWN4hWFu0kSOtQO-XnyNUWQrsqTrrdHl1BYBg=="
property-set url http://localhost:9999
config:update
```

Update automatically:
```
bundle:watch *
```

## Links:
* InfluxDB 1.x client: https://github.com/influxdata/influxdb-java
* InfluxDB 1.x: https://www.influxdata.com/time-series-platform/

