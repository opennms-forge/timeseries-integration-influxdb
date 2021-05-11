package org.opennms.timeseries.impl.influxdb;

public class NonblockingInfluxdbStorageIntegrationTest extends AbstractInfluxdbStorageIntegrationTest {

    @Override
    protected InfluxdbConfig.WriteStrategy getWriteStrategy() {
        return InfluxdbConfig.WriteStrategy.nonblocking;
    }
}
