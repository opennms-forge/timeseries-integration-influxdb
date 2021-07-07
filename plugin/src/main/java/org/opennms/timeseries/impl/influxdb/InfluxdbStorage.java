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

import static org.opennms.integration.api.v1.timeseries.TagMatcher.Type.NOT_EQUALS;
import static org.opennms.integration.api.v1.timeseries.TagMatcher.Type.NOT_EQUALS_REGEX;
import static org.opennms.timeseries.impl.influxdb.TransformUtil.metricKeyToInflux;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.opennms.integration.api.v1.timeseries.IntrinsicTagNames;
import org.opennms.integration.api.v1.timeseries.Metric;
import org.opennms.integration.api.v1.timeseries.Sample;
import org.opennms.integration.api.v1.timeseries.Tag;
import org.opennms.integration.api.v1.timeseries.TagMatcher;
import org.opennms.integration.api.v1.timeseries.TimeSeriesFetchRequest;
import org.opennms.integration.api.v1.timeseries.TimeSeriesStorage;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableMetric;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableSample;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.influxdb.client.DeleteApi;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.InfluxDBClientOptions;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.DeletePredicateRequest;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;

/**
 * Implementation of TimeSeriesStorage that uses InfluxdbStorage.
 * <p>
 * Design choices:
 * - we fill the _measurement column with the Metrics key
 * - we prefix the tag key with the tag type ('intrinsic' or 'meta')
 */
public class InfluxdbStorage implements TimeSeriesStorage {
    private static final Logger LOG = LoggerFactory.getLogger(InfluxdbStorage.class);
    private final static DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));
    private final static String TAG_RESOURCE_ID = Metric.TagType.intrinsic.name() + "_" + IntrinsicTagNames.resourceId;
    private final static String TAG_NAME = Metric.TagType.intrinsic.name() + "_" + IntrinsicTagNames.name;

    private final InfluxdbConfig config;
    private final InfluxDBClient influxDBClient;
    private final WriterWrapper writeApi;
    private final QueryApi queryApi;
    private final DeleteApi deleteApi;

    public InfluxdbStorage(final InfluxdbConfig config) {
        this.config = config;

        InfluxDBClientOptions options = InfluxDBClientOptions.builder()
                .bucket(config.getBucket())
                .org(config.getOrg())
                .url(config.getUrl())
                .authenticateToken(config.getToken().toCharArray())
                .build();
        influxDBClient = InfluxDBClientFactory.create(options);

        // Fetch the APIs once during init, some of these require to be closed
        queryApi = influxDBClient.getQueryApi();
        deleteApi = influxDBClient.getDeleteApi();

        this.writeApi = new WriterWrapper();
        if (InfluxdbConfig.WriteStrategy.blocking == config.getWriteStrategy()) {
            WriteApiBlocking w = influxDBClient.getWriteApiBlocking();
            this.writeApi.setWriter(w::writePoints);
            this.writeApi.setCloser(() -> {}); // do nothing
        } else if (InfluxdbConfig.WriteStrategy.opennms == config.getWriteStrategy()) {
            InfluxdbWriter w = new InfluxdbWriter(config);
            this.writeApi.setWriter(w::writePoints);
            this.writeApi.setCloser(() -> {}); // do nothing
        } else {
            WriteApi w = influxDBClient.getWriteApi();
            this.writeApi.setWriter(w::writePoints);
            this.writeApi.setCloser(w::close);
        }

        LOG.info("Successfully initialized InfluxDB client.");
    }

    public void destroy() {
        writeApi.close();
        influxDBClient.close();
    }

    @Override
    public void store(List<Sample> samples) {
        List<Point> points = new ArrayList<>(samples.size());
        for (Sample sample : samples) {
            Point point = Point
                    .measurement(metricKeyToInflux(sample.getMetric().getFirstTagByKey(IntrinsicTagNames.name).getValue()))
                    .addField("value", sample.getValue())
                    .time(sample.getTime().toEpochMilli(), WritePrecision.MS);
            storeTags(point, ImmutableMetric.TagType.intrinsic, sample.getMetric().getIntrinsicTags());
            storeTags(point, ImmutableMetric.TagType.meta, sample.getMetric().getMetaTags());
            storeTags(point, ImmutableMetric.TagType.external, sample.getMetric().getExternalTags());
            points.add(point);
        }
        writeApi.writePoints(points);
    }

    private void storeTags(final Point point, final ImmutableMetric.TagType tagType, final Collection<Tag> tags) {
        for (final Tag tag : tags) {
            String value = tag.getValue();
            point.addTag(toClassifiedTagKey(tagType, tag), value);
        }
    }

    private String toClassifiedTagKey(final ImmutableMetric.TagType tagType, final TagMatcher tag) {
        return tagType.name() + "_" + tag.getKey();
    }

    private String toClassifiedTagKey(final ImmutableMetric.TagType tagType, final Tag tag) {
        return tagType.name() + "_" + tag.getKey();
    }

    @Override
    public List<Metric> findMetrics(Collection<TagMatcher> matchers) {
        if (matchers.isEmpty()) {
            throw new IllegalArgumentException("Collection<TagMatcher> can not be empty");
        }
        final String tagRestriction = matchers
                .stream()
                .map(m -> "(r[\"" + toClassifiedTagKey(Metric.TagType.intrinsic, m) + "\"]" + tagMatcherToComp(m)
                        + ((NOT_EQUALS == m.getType() || NOT_EQUALS_REGEX == m.getType()) ? "and" : "or")
                        + " r[\"" + toClassifiedTagKey(Metric.TagType.meta, m) + "\"]" + tagMatcherToComp(m) + ")")
                        // external tags are not searchable
                .collect(Collectors.joining(" and "));

        final String query = "from(bucket:\"opennms\")\n" +
                "  |> range(start:-5y)\n" +
                "  |> filter(fn: (r) => " + tagRestriction + ")\n" +
                "  |> distinct(column: \"_measurement\")\n";

        return queryApi
                .query(query)
                .stream()
                .map(FluxTable::getRecords)
                .flatMap(Collection::stream)
                .map(FluxRecord::getValues)
                .filter(m -> m.containsKey(TAG_RESOURCE_ID)) // one of "ours"
                .map(this::createMetricFromMap)
                .distinct() // shouldn't be necessary but just in case
                .collect(Collectors.toList());
    }

    private String tagMatcherToComp(final TagMatcher matcher) {
        // see https://docs.influxdata.com/influxdb/cloud/query-data/flux/regular-expressions/
        Objects.requireNonNull(matcher);
        switch (matcher.getType()) {
            case EQUALS:
                return "==\""+matcher.getValue()+"\"";
            case NOT_EQUALS:
                return "!=\""+matcher.getValue()+"\"";
            case EQUALS_REGEX:
                return "=~/"+matcher.getValue()+"/";
            case NOT_EQUALS_REGEX:
                return "!~/"+matcher.getValue()+"/";
            default:
                throw new IllegalArgumentException("Unknown TagMatcher.Type " + matcher.getType().name());
        }
    }

    @Override
    public List<Sample> getTimeseries(TimeSeriesFetchRequest request) {

        String query = "from(bucket:\"" + this.config.getBucket() + "\")\n" +
                " |> range(start:" + DATE_TIME_FORMAT.format(request.getStart()) + ", stop:" + DATE_TIME_FORMAT.format(request.getEnd()) + ")\n" +
                " |> filter(fn:(r) => r[\"intrinsic_name\"]==\"" + request.getMetric().getFirstTagByKey(IntrinsicTagNames.name).getValue() + "\" and\n " +
                "                     r[\"intrinsic_resourceId\"]==\"" + request.getMetric().getFirstTagByKey(IntrinsicTagNames.resourceId).getValue() + "\")\n" +
                " |> filter(fn:(r) => r._field == \"value\")";
        List<FluxTable> tables = queryApi.query(query);

        final List<Sample> samples = new ArrayList<>();
        for (FluxTable fluxTable : tables) {
            List<FluxRecord> records = fluxTable.getRecords();
            Metric metric = null;
            for (FluxRecord record : records) {
                if (metric == null) {
                    // we assume here that the metric is always the same. Therefore we create it only once and not for every record
                    metric = createMetricFromMap(record.getValues());
                }
                Sample sample = ImmutableSample.builder()
                        .metric(metric)
                        .time(record.getTime())
                        .value((Double) record.getValue())
                        .build();
                samples.add(sample);
            }
        }
        return samples;
    }

    @Override
    public void delete(Metric metric) {
        // this doesnt seem to work, see https://github.com/influxdata/influxdb/issues/20399
        DeletePredicateRequest predicate = new DeletePredicateRequest()
                .start(OffsetDateTime.now().minusYears(50))
                .stop(OffsetDateTime.now().plusYears(50))
                .predicate(TAG_RESOURCE_ID + "=\"" + metric.getFirstTagByKey(IntrinsicTagNames.resourceId).getValue() + "\"")
                .predicate(TAG_NAME + "=\"" + metric.getFirstTagByKey(IntrinsicTagNames.resourceId).getValue() + "\"");
        deleteApi.delete(predicate, config.getBucket(), config.getOrg());
    }

    /**
     * Restore the metric from the tags we get out of InfluxDb.
     */
    private Metric createMetricFromMap(final Map<String, Object> map) {
        ImmutableMetric.MetricBuilder metric = ImmutableMetric.builder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            getIfMatching(ImmutableMetric.TagType.intrinsic, entry).ifPresent(metric::intrinsicTag);
            getIfMatching(ImmutableMetric.TagType.meta, entry).ifPresent(metric::metaTag);
            getIfMatching(ImmutableMetric.TagType.external, entry).ifPresent(metric::externalTag);
        }
        return metric.build();
    }

    private Optional<Tag> getIfMatching(final ImmutableMetric.TagType tagType, final Map.Entry<String, Object> entry) {
        // Check if the key starts with the prefix. If so it is an opennms key, if not something InfluxDb specific and
        // we can ignore it.
        final String prefix = tagType.name() + '_';
        String key = entry.getKey();

        if (key.startsWith(prefix)) {
            key = key.substring(prefix.length());
            String value = entry.getValue().toString(); // tagValueFromInflux(entry.getValue().toString()); // convert
            return Optional.of(new ImmutableTag(key, value));
        }
        return Optional.empty();
    }

    /** We need to wrap the different write apis since they don't share a common interface.  */
    static class WriterWrapper {

        private Consumer<List<Point>> writer;
        private Runnable closer;

        public void setWriter(Consumer<List<Point>> writer) {
            this.writer = writer;
        }

        public void setCloser(Runnable closer) {
            this.closer = closer;
        }

        public void writePoints(List<Point> points) {
            this.writer.accept(points);
        }

        public void close() {
            this.closer.run();
        }
    }
}
