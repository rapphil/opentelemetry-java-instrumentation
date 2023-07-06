/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of {@link InstrumentationTestRunner} that initializes OpenTelemetry SDK and
 * uses in-memory exporter to collect traces and metrics.
 */
public final class LibraryTestRunner extends InstrumentationTestRunner {

  private static final OpenTelemetrySdk openTelemetry;
  private static final InMemorySpanExporter testSpanExporter;
  private static final InMemoryMetricExporter testMetricExporter;
  private static final MetricReader metricReader;
  private static boolean forceFlushCalled;

  static {
    GlobalOpenTelemetry.resetForTest();

    testSpanExporter = InMemorySpanExporter.create();
    testMetricExporter = InMemoryMetricExporter.create(AggregationTemporality.DELTA);

    metricReader =
        PeriodicMetricReader.builder(testMetricExporter)
            // Set really long interval. We'll call forceFlush when we need the metrics
            // instead of collecting them periodically.
            .setInterval(Duration.ofNanos(Long.MAX_VALUE))
            .build();

    openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(new FlushTrackingSpanProcessor())
                    .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                    .addSpanProcessor(SimpleSpanProcessor.create(testSpanExporter))
                    .build())
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            // TODO: Provide a way to customize the options.
            .setPropagators(
                ContextPropagators.create(
                    TextMapPropagator.composite(
                        W3CTraceContextPropagator.getInstance(), AwsXrayPropagator.getInstance())))
            .buildAndRegisterGlobal();
  }

  private static final LibraryTestRunner INSTANCE = new LibraryTestRunner();

  public static LibraryTestRunner instance() {
    return INSTANCE;
  }

  private LibraryTestRunner() {
    super(openTelemetry);
  }

  @Override
  public void beforeTestClass() {
    // just in case: if there was any test that modified the global instance, reset it
    if (GlobalOpenTelemetry.get() != openTelemetry) {
      GlobalOpenTelemetry.resetForTest();
      GlobalOpenTelemetry.set(openTelemetry);
    }
  }

  @Override
  public void afterTestClass() {}

  @Override
  public void clearAllExportedData() {
    // Flush meter provider to remove any lingering measurements
    openTelemetry.getSdkMeterProvider().forceFlush().join(10, TimeUnit.SECONDS);
    testSpanExporter.reset();
    testMetricExporter.reset();
    forceFlushCalled = false;
  }

  @Override
  public OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  public OpenTelemetrySdk getOpenTelemetrySdk() {
    return openTelemetry;
  }

  @Override
  public List<SpanData> getExportedSpans() {
    return testSpanExporter.getFinishedSpanItems();
  }

  @Override
  public List<MetricData> getExportedMetrics() {
    metricReader.forceFlush().join(10, TimeUnit.SECONDS);
    return testMetricExporter.getFinishedMetricItems();
  }

  @Override
  public List<LogRecordData> getExportedLogRecords() {
    // no logs support yet
    return Collections.emptyList();
  }

  @Override
  public boolean forceFlushCalled() {
    return forceFlushCalled;
  }

  private static class FlushTrackingSpanProcessor implements SpanProcessor {
    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {}

    @Override
    public boolean isStartRequired() {
      return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {}

    @Override
    public boolean isEndRequired() {
      return false;
    }

    @Override
    public CompletableResultCode forceFlush() {
      forceFlushCalled = true;
      return CompletableResultCode.ofSuccess();
    }
  }
}
