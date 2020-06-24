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

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.opennms.integration.api.v1.timeseries.TimeSeriesStorage;
import org.opennms.timeseries.impl.influxdb.shell.InitInfluxdb;
import org.opennms.timeseries.plugintest.AbstractStorageIntegrationTest;

public class InfluxdbStorageIT extends AbstractStorageIntegrationTest {

    protected static InfluxdbStorage storage;
    protected static Process influxdb;
    protected static ExecutorService pool = Executors.newSingleThreadExecutor();

    @BeforeClass
    public static void setUpOnce() throws IOException, InterruptedException {
        influxdb = new ProcessBuilder()
                .command("/usr/bin/bash", "-c", "docker run -p 9999:9999 quay.io/influxdb/influxdb:2.0.0-beta")
                .redirectErrorStream(true)
                .inheritIO()
                .start();
        Thread.sleep(5000); // wait for docker to start
        String accessToken = new InitInfluxdb().setupInflux();
        storage = new InfluxdbStorage(accessToken);
    }

    @AfterClass
    public static void tearDown() {
        if (influxdb != null && influxdb.isAlive()) {
            influxdb.destroy();
        }
        pool.shutdown();
    }


    @Override
    protected TimeSeriesStorage createStorage() {
        return storage;
    }

    @Override
    protected void waitForPersistingChanges(){
        try {
            // we wait to make sure influxdb has the changes persisted
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // do nothing
        }
    }
}
