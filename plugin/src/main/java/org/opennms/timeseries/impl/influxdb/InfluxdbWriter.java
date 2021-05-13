package org.opennms.timeseries.impl.influxdb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.opennms.integration.api.v1.timeseries.StorageException;
import org.opennms.timeseries.influxdb.shaded.resilience4j.bulkhead.Bulkhead;
import org.opennms.timeseries.influxdb.shaded.resilience4j.bulkhead.BulkheadConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.influxdb.client.write.Point;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * We implement our own write logic with OKClient in order to let our caller handle backpressure.
 */
public class InfluxdbWriter {

    private static final Logger LOG = LoggerFactory.getLogger(InfluxdbWriter.class);

    final InfluxdbConfig config;
    private final OkHttpClient client;

    private final MetricRegistry metrics = new MetricRegistry();
    private final Meter samplesWritten = metrics.meter("samplesWritten");
    private final Meter samplesLost = metrics.meter("samplesLost");

    private final Bulkhead asyncHttpCallsBulkhead;
    private final HttpUrl url;

    public InfluxdbWriter(final InfluxdbConfig config) {
        Objects.requireNonNull(config);
        this.config = config;
        this.client = createClient();
        this.url = createUrl();
        this.asyncHttpCallsBulkhead = createBulkhead();
        exposeHttpClientStatistics();
    }

    private OkHttpClient createClient() {
        ConnectionPool connectionPool = new ConnectionPool(config.getMaxConcurrentHttpConnections(), 5, TimeUnit.MINUTES);
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(config.getMaxConcurrentHttpConnections());
        dispatcher.setMaxRequestsPerHost(config.getMaxConcurrentHttpConnections());
        return new OkHttpClient.Builder()
                .readTimeout(config.getReadTimeoutInMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getWriteTimeoutInMs(), TimeUnit.MILLISECONDS)
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .build();
    }

    private HttpUrl createUrl() {
        Objects.requireNonNull(config.getUrl());
        final HttpUrl base = HttpUrl.parse(config.getUrl());
        HttpUrl.Builder b = new HttpUrl.Builder()
                .scheme(base.scheme())
                .host(base.host())
                .port(base.port());
        for (String segment : base.pathSegments()) {
            b.addPathSegment(segment);
        }
        return b.addPathSegments("api/v2/write")
                .addQueryParameter("org", config.getOrg())
                .addQueryParameter("bucket", config.getBucket())
                .addQueryParameter("precision", "ms")
                .build();
    }

    private Bulkhead createBulkhead() {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(config.getMaxConcurrentHttpConnections() * 6)
                .maxWaitDuration(Duration.ofMillis(config.getBulkheadMaxWaitDurationInMs()))
                .fairCallHandlingStrategyEnabled(true)
                .build();
        return Bulkhead.of("asyncHttpCalls", bulkheadConfig);
    }

    private void exposeHttpClientStatistics() {
        // Expose HTTP client statistics
        metrics.register("connectionCount", (Gauge<Integer>) () -> client.connectionPool().connectionCount());
        metrics.register("idleConnectionCount", (Gauge<Integer>) () -> client.connectionPool().idleConnectionCount());
        metrics.register("queuedCallsCount", (Gauge<Integer>) () -> client.dispatcher().queuedCallsCount());
        metrics.register("runningCallsCount", (Gauge<Integer>) () -> client.dispatcher().runningCallsCount());
        metrics.register("availableConcurrentCalls", (Gauge<Integer>) () -> asyncHttpCallsBulkhead.getMetrics().getAvailableConcurrentCalls());
        metrics.register("maxAllowedConcurrentCalls", (Gauge<Integer>) () -> asyncHttpCallsBulkhead.getMetrics().getMaxAllowedConcurrentCalls());
    }

    public void writePoints(final List<Point> points) {
        String bodyContent = points
                .stream()
                .map(Point::toLineProtocol)
                .collect(Collectors.joining("\n"));

        final byte[] compressed;
        try {
            compressed = compress(bodyContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final RequestBody body = RequestBody.create(compressed);

        final Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Encoding", "gzip")
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("User-Agent", InfluxdbWriter.class.getCanonicalName())
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Token " + config.getToken())
                .post(body)
                .build();

        asyncHttpCallsBulkhead.executeCompletionStage(() -> executeAsync(request)).whenComplete((r, ex) -> {
            if (ex == null) {
                samplesWritten.mark(points.size());
            } else {
                // FIXME: Data loss
                samplesLost.mark(points.size());
                LOG.error("Error occurred while storing samples, sample will be lost.", ex);
            }
        });
    }

    public CompletableFuture<Void> executeAsync(Request request) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    String bodyAsString;
                    try(ResponseBody body = response.body()) {
                        bodyAsString = body.string();
                    } catch (IOException e) {
                        bodyAsString = "(error reading body)";
                    }

                    future.completeExceptionally(new StorageException(String.format("Writing to Influxdb failed: %s - %s: %s",
                            response.code(),
                            response.message(),
                            bodyAsString)));
                } else {
                    future.complete(null);
                }
            }
        });
        return future;
    }

    byte[] compress(final String stringToCompress) throws IOException {
        Objects.requireNonNull(stringToCompress);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(outputStream);
        gzip.write(stringToCompress.getBytes(StandardCharsets.UTF_8));
        gzip.close();
        return outputStream.toByteArray();
    }
}
