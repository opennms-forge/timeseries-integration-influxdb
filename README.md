# Timeseries Integration InfluxDB Plugin

This plugin exposes an implementation of the TimeSeriesStorage interface.
It stores the data in a InfluxDB database.
It can be used in OpenNMS to store and retrieve timeseries data.

## Prerequisite
* A running instance of influxdb must be available.
* For testing purposes you can run: ``sudo docker run -p 9999:9999 quay.io/influxdb/influxdb:2.0.0-beta --reporting-disabled``

## Usage
* compile: ``mvn install``
* activation: Enable the timeseries integration layer: TODO: Patrick add link once the documentation is online
* activate in Karaf shell: ``bundle:install -s mvn:org.opennms.plugins.timeseries.influxdb/timeseries-influxdb-plugin/1.0.0-SNAPSHOT``
* run command to init influxdb with an organisation and bucket: ``timescale:init``

## Links:
* InfluxDB 2.0: https://www.influxdata.com/products/influxdb-overview/

## Open TODOs:
* dependencies are not correct yet, doesn't find influxdb




