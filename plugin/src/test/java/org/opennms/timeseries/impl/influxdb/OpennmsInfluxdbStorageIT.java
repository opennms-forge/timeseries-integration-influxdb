package org.opennms.timeseries.impl.influxdb;

public class OpennmsInfluxdbStorageIT extends AbstractInfluxdbStorageIT {

    @Override
    protected InfluxdbConfig.WriteStrategy getWriteStrategy() {
        return InfluxdbConfig.WriteStrategy.opennms;
    }
}
