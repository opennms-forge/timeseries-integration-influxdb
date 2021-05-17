package org.opennms.timeseries.impl.influxdb;

public class OpennmsInfluxdbStorageIntegrationTest extends AbstractInfluxdbStorageIntegrationTest {

    @Override
    protected InfluxdbConfig.WriteStrategy getWriteStrategy() {
        return InfluxdbConfig.WriteStrategy.opennms;
    }
}
