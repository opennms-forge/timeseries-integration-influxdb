package org.opennms.timeseries.impl.influxdb;

import org.junit.Ignore;

@Ignore
public class BlockingInfluxdbStorageIT extends AbstractInfluxdbStorageIT {

    @Override
    protected InfluxdbConfig.WriteStrategy getWriteStrategy() {
        return InfluxdbConfig.WriteStrategy.blocking;
    }
}
