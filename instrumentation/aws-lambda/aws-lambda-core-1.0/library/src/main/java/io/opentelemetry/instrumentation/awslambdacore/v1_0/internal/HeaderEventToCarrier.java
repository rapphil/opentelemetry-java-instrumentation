/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HeaderEventToCarrier implements EventToCarrier {
  @Override
  @CanIgnoreReturnValue
  public Map<String, String> convert(Map<String, String> carrier, AwsLambdaRequest event) {
    carrier.putAll(event.getHeaders());
    return carrier;
  }
}
