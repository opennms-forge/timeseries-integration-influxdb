/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2020 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2020 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.timeseries.impl.influxdb;

import java.io.File;
import java.time.Duration;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opennms.integration.api.v1.timeseries.AbstractStorageIntegrationTest;
import org.opennms.integration.api.v1.timeseries.StorageException;
import org.opennms.integration.api.v1.timeseries.TimeSeriesStorage;
import org.opennms.timeseries.impl.influxdb.shell.InitInfluxdb;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public abstract class AbstractInfluxdbStorageIT extends AbstractStorageIntegrationTest {

    protected final static int PORT = 8086;

    protected InfluxdbStorage storage;
    protected String accessToken;

    public DockerComposeContainer<?> influxdbDocker;

    @Before
    public void setUp() throws Exception {
        influxdbDocker = new DockerComposeContainer<>(new File("src/test/resources/org/opennms/timeseries/impl/influxdb/docker-compose.yaml"))
                .withExposedService("influxdb", PORT, Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(20)));
        influxdbDocker.start();
        accessToken = new InitInfluxdb()
                .setupInflux();
        super.setUp();
    }

    @After
    public void tearDown() {
        if (storage != null ) {
            storage.destroy();
        }
        if(influxdbDocker != null) {
            influxdbDocker.stop();
        }
    }

    @Override
    protected TimeSeriesStorage createStorage() {
        InfluxdbConfig config = InfluxdbConfig.builder()
                .token(accessToken)
                .writeStrategy(getWriteStrategy())
                .build();
        storage = new InfluxdbStorage(config);
        return storage;
    }

    protected abstract InfluxdbConfig.WriteStrategy getWriteStrategy();

    @Override
    protected void waitForPersistingChanges(){
        try {
            // we wait to make sure influxdb has the changes persisted
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    @Test
    @Override
    @Ignore // delete doesn't seem to work, see https://github.com/influxdata/influxdb/issues/20399
    public void shouldDeleteMetrics() throws StorageException {}
}
