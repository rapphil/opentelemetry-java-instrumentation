/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class CompositeEventToCarrier implements EventToCarrier {

  private final Iterable<EventToCarrier> eventToCarriers;

  public CompositeEventToCarrier(Iterable<EventToCarrier> carriers) {
    this.eventToCarriers = carriers;
  }

  @Override
  public Map<String, String> convert(Map<String, String> carrier, AwsLambdaRequest event) {
    for (EventToCarrier eventToCarrier : eventToCarriers) {
      carrier = eventToCarrier.convert(carrier, event);
    }
    return carrier;
  }
}
