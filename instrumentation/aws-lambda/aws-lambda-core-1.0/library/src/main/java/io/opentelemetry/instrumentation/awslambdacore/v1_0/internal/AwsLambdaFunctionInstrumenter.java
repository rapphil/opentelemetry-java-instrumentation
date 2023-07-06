/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class AwsLambdaFunctionInstrumenter {

  private final OpenTelemetry openTelemetry;
  final Instrumenter<AwsLambdaRequest, Object> instrumenter;
  private static final Map<String, EventToCarrier> eventToCarriersMap = new HashMap<>();

  static {
    eventToCarriersMap.put("lambda_runtime", new LambdaRuntimeXRayEventToCarrier());
    eventToCarriersMap.put("http_headers", new HeaderEventToCarrier());
  }

  private final CompositeEventToCarrier compositeEventToCarrier;

  public AwsLambdaFunctionInstrumenter(
      OpenTelemetry openTelemetry, Instrumenter<AwsLambdaRequest, Object> instrumenter) {
    this.openTelemetry = openTelemetry;
    this.instrumenter = instrumenter;

    // TODO: can we use the autoconfigure sdk Configuration instead?
    List<EventToCarrier> carriers =
        Arrays.stream(
                System.getenv()
                    .getOrDefault("OTEL_AWS_LAMBDA_EVENT_TO_CARRIERS", "http_headers")
                    .split(","))
            .map(x -> eventToCarriersMap.get(x))
            .collect(Collectors.toList());
    this.compositeEventToCarrier = new CompositeEventToCarrier(carriers);
  }

  public boolean shouldStart(Context parentContext, AwsLambdaRequest input) {
    return instrumenter.shouldStart(parentContext, input);
  }

  public Context start(Context parentContext, AwsLambdaRequest input) {
    return instrumenter.start(parentContext, input);
  }

  public void end(
      Context context,
      AwsLambdaRequest input,
      @Nullable Object response,
      @Nullable Throwable error) {
    instrumenter.end(context, input, response, error);
  }

  public Context extract(AwsLambdaRequest input) {
    ContextPropagationDebug.debugContextLeakIfEnabled();
    Map<String, String> carrier = new HashMap<>();
    carrier = compositeEventToCarrier.convert(carrier, input);
    return openTelemetry
        .getPropagators()
        .getTextMapPropagator()
        .extract(Context.root(), carrier, MapGetter.INSTANCE);
  }

  private enum MapGetter implements TextMapGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s.toLowerCase(Locale.ROOT));
    }
  }
}
