/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import java.util.Collections;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class LambdaRuntimeXRayEventToCarrier implements EventToCarrier {
  private static final String AWS_TRACE_HEADER_ENV_KEY = "_X_AMZN_TRACE_ID";
  private static final String AWS_TRACE_HEADER_PROP = "com.amazonaws.xray.traceHeader";
  // lower-case map getter used for extraction
  private static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

  private static Map<String, String> getTraceHeaderMap() {
    String traceHeader = System.getProperty(AWS_TRACE_HEADER_PROP);
    if (isEmptyOrNull(traceHeader)) {
      traceHeader = System.getenv(AWS_TRACE_HEADER_ENV_KEY);
    }
    return isEmptyOrNull(traceHeader)
        ? Collections.emptyMap()
        : Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, traceHeader);
  }

  @Override
  @CanIgnoreReturnValue
  public Map<String, String> convert(Map<String, String> carrier, AwsLambdaRequest event) {
    carrier.putAll(getTraceHeaderMap());
    return carrier;
  }

  private static boolean isEmptyOrNull(String value) {
    return value == null || value.isEmpty();
  }
}
