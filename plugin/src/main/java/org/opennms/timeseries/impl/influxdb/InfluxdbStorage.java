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

import static org.opennms.timeseries.impl.influxdb.TransformUtil.metricKeyToInflux;
import static org.opennms.timeseries.impl.influxdb.TransformUtil.tagValueFromInflux;
import static org.opennms.timeseries.impl.influxdb.TransformUtil.tagValueToInflux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.opennms.integration.api.v1.timeseries.Metric;
import org.opennms.integration.api.v1.timeseries.Sample;
import org.opennms.integration.api.v1.timeseries.StorageException;
import org.opennms.integration.api.v1.timeseries.Tag;
import org.opennms.integration.api.v1.timeseries.TimeSeriesFetchRequest;
import org.opennms.integration.api.v1.timeseries.TimeSeriesStorage;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableMetric;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableSample;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * Implementation of TimeSeriesStorage that uses Influxdb 1.x.
 *
 * Design choices:
 * - we fill the _measurement column with the Metrics key
 * - we prefix the tag key with the tag type ('intrinsic' or 'meta')
 */
public class InfluxdbStorage implements TimeSeriesStorage {
    private static final Logger LOG = LoggerFactory.getLogger(InfluxdbStorage.class);

    private final InfluxDB influxDb;

    private final LoadingCache<Collection<Tag>, List<Metric>> cache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build(
                    new CacheLoader<Collection<Tag>, List<Metric>>() {
                        public List<Metric> load(final Collection<Tag> tags) throws StorageException {
                            return loadMetrics(tags);
                        }
                    });

    /** Uses default values for database, bucket, org, url. */
    public InfluxdbStorage() {
        this("http://localhost:8086", "opennms", "root", "root");
    }

    public InfluxdbStorage(
            String url,
            String databaseName,
            String username,
            String password) {
        Objects.requireNonNull(username, "Parameter username cannot be null");
        Objects.requireNonNull(password, "Parameter password cannot be null");
        Objects.requireNonNull(url, "Parameter url cannot be null");
        Objects.requireNonNull(databaseName, "Parameter databaseName cannot be null");

        influxDb = InfluxDBFactory.connect(url, username, password);
        influxDb.enableBatch(BatchOptions.DEFAULTS);
        influxDb.setDatabase(databaseName);
        LOG.info("Successfully initialized InfluxDB client with url={} and databaseName={}.", url, databaseName);
    }

    public void destroy() {
        influxDb.close();
    }

    @Override
    public void store(List<Sample> samples) {
        for(Sample sample: samples) {
            Point.Builder point = Point
                    .measurement(metricKeyToInflux(sample.getMetric().getKey())) // make sure the measurement has only allowed characters
                    .addField("value", sample.getValue())
                    .time(sample.getTime().toEpochMilli(), TimeUnit.MILLISECONDS);
            addTags(point, ImmutableMetric.TagType.intrinsic, sample.getMetric().getIntrinsicTags());
            addTags(point, ImmutableMetric.TagType.meta, sample.getMetric().getMetaTags());
            influxDb.write(point.build());
        }
    }

    private void addTags(final Point.Builder point, final ImmutableMetric.TagType tagType, final Collection<Tag> tags) {
        for(final Tag tag : tags) {
            String value = tag.getValue();
            value = tagValueToInflux(value); // Influx has a problem with a colon in a tag value if we query for it
            point.tag(toClassifiedTagKey(tagType, tag), value);
        }
    }

    private String toClassifiedTagKey(final ImmutableMetric.TagType tagType, final Tag tag) {
         return tagType.name() + "_" + tag.getKey();
    }

    @Override
    public List<Metric> getMetrics(Collection<Tag> tags) throws StorageException {
        Objects.requireNonNull(tags, "tags cannot be null");
        try {
            return cache.get(tags);
        } catch (ExecutionException e) {
            throw new StorageException(e);
        }
    }

    private List<Metric> loadMetrics(Collection<Tag> tags) throws StorageException {
        String query = String.format("SHOW MEASUREMENTS WHERE %s", tagsToQuery(tags));
        List<String> seriesNames = extractResults(query);

        List<Metric> allMetrics = new ArrayList<>();
        for(String metricKey : seriesNames) {
            allMetrics.add(loadMetric(metricKey));
        }
        return allMetrics;
    }

    private Metric loadMetric(final String metricKey) throws StorageException {
        final ImmutableMetric.MetricBuilder metric = ImmutableMetric.builder();

        // Extract tags
        final String query = String.format("SHOW TAG KEYS FROM \"%s\" ", metricKey);

        List<String> keys = extractResults(query);

        for(String rawTagKey : keys) {
            List<String> values = loadTagValueByTagKey(metricKey, rawTagKey);
            for(final String rawTagValue : values) {
                getIfMatching(ImmutableMetric.TagType.intrinsic, rawTagKey, rawTagValue).ifPresent(metric::intrinsicTag);
                getIfMatching(ImmutableMetric.TagType.meta, rawTagKey, rawTagValue).ifPresent(metric::metaTag);
            }
        }
        return metric.build();
    }

    private List<String> loadTagValueByTagKey(final String metricKey,final String tagKey) throws StorageException {
        final String query = String.format("SHOW TAG VALUES FROM \"%s\" WITH KEY = \"%s\"", metricKey, tagKey);
        return extractResults(query, 1);
    }

    /** Extracts the first column as a list of strings */
    private List<String> extractResults(String query) throws StorageException {
        return extractResults(query, 0);
    }

    /** Extracts the column with the given index as a list of strings */
    private List<String> extractResults(String query, int index) throws StorageException {
        QueryResult result = this.influxDb.query(new Query(query));

        if(result.hasError()) { // TODO: Patrick remove new Exception once we have the constructor StorageException(Exception) available
            throw new StorageException(new Exception(String.format("An error occurred for query '%s': %s", query, result.getError())));
        }
        List<QueryResult.Result> results = result.getResults();
        if(results.isEmpty()) {
            return Collections.emptyList();
        }
        if(results.get(0).getSeries() == null) {
            return Collections.emptyList();
        }

        return results.get(0)
                .getSeries()
                .stream()
                .map(QueryResult.Series::getValues)
                .flatMap(Collection::stream)
                .map(l -> l.get(index))
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    private String tagsToQuery(final Collection<Tag> tags) {
        return tags
                .stream()
                .sorted()
                .map(this::tagToQuery)
                .collect(Collectors.joining(" AND "));
    }

    private String tagToQuery(final Tag tag) {
        return String.format(" ( %s = '%s' OR %s='%s') ",
                toClassifiedTagKey(Metric.TagType.meta, tag),
                tagValueToInflux(tag.getValue()),
                toClassifiedTagKey(Metric.TagType.intrinsic, tag),
                tagValueToInflux(tag.getValue()));
    }

    private Optional<Tag> getIfMatching(final ImmutableMetric.TagType tagType, final String rawTagKey, final String rawTagValue) {
        // Check if the key starts with the prefix. If so it is an opennms key, if not something InfluxDb specific and
        // we can ignore it.
        final String prefix = tagType.name() + '_';

        if(rawTagKey.startsWith(prefix)) {
            String key = rawTagKey.substring(prefix.length());
            String value = tagValueFromInflux(rawTagValue); // convert
            return Optional.of(new ImmutableTag(key, value));
        }
        return Optional.empty();
    }

    @Override
    public List<Sample> getTimeseries(TimeSeriesFetchRequest request) throws StorageException {

        String query = String.format("SELECT time, value FROM \"%s\"", metricKeyToInflux(request.getMetric().getKey()));
        QueryResult result = this.influxDb.query(new Query(query));

        if(result.hasError()) { // TODO: Patrick remove new Exception once we have the constructor StorageException(Exception) available
            throw new StorageException(new Exception(String.format("An error occurred for query '%s': %s", query, result.getError())));
        }
        List<QueryResult.Result> results = result.getResults();
        if(results.isEmpty()) {
            return Collections.emptyList();
        }
        if(results.get(0).getSeries() == null) {
            return Collections.emptyList();
        }

        final List<Sample> samples = new ArrayList<>();
        for(List<Object> values : results.get(0).getSeries().get(0).getValues()) {
            String timeString = (String)values.get(0);
            Instant time = Instant.parse(timeString);
            Double value =  (Double)values.get(1);
            Sample sample = ImmutableSample.builder()
                        .metric(request.getMetric())
                        .time(time)
                        .value(value)
                        .build();
            samples.add(sample);
        }
        return samples;
    }

    @Override
    public void delete(Metric metric) {
        Query query = new Query(String.format("DROP SERIES FROM \"%s\"",  metricKeyToInflux(metric.getKey())));
        QueryResult result = this.influxDb.query(query);
        if(result.hasError()){
            LOG.warn("An error occurred while deleting metric {}: {}", metric, result.getError());
        }
    }
}
