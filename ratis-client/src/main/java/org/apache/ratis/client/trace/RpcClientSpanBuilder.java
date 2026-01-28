/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ratis.client.trace;

import static org.apache.ratis.trace.RatisAttributes.RPC_METHOD;
import static org.apache.ratis.trace.RatisAttributes.RPC_SERVICE;
import static org.apache.ratis.trace.RatisAttributes.RPC_SYSTEM;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import org.apache.ratis.trace.RatisAttributes;
import org.apache.ratis.trace.TraceUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;


/**
 * Construct {@link Span} instances originating from the client side of an RPC.
 * @see <a href=
 *      "https://github.com/open-telemetry/opentelemetry-specification/blob/3e380e249f60c3a5f68746f5e84d10195ba41a79/specification/trace/semantic_conventions/rpc.md">Semantic
 *      conventions for RPC spans</a>
 */
public class RpcClientSpanBuilder implements Supplier<Span> {

  private String name;
  private final Map<AttributeKey<?>, Object> attributes = new HashMap<>();

  @Override
  public Span get() {
    return build();
  }

  public RpcClientSpanBuilder setMethod(final String packageAndService, final String method) {
    this.name = buildSpanName(packageAndService, method);
    setRpcAttributes(attributes, packageAndService, method);
    return this;
  }

  public RpcClientSpanBuilder setPeerId(final String peerId) {
    attributes.put(RatisAttributes.PEER_ID, peerId);
    return this;
  }

  public RpcClientSpanBuilder setProxyName(final String proxyName) {
    attributes.put(RatisAttributes.RPC_PROXY_NAME, proxyName);
    return this;
  }

  @SuppressWarnings("unchecked")
  public Span build() {
    final SpanBuilder builder = TraceUtils.getGlobalTracer().spanBuilder(name)
        .setSpanKind(SpanKind.CLIENT);
    attributes.forEach((k, v) -> builder.setAttribute((AttributeKey<? super Object>) k, v));
    return builder.startSpan();
  }

  /**
   * Static utility method that performs the primary logic of this builder. It is visible to other
   * classes in this package so that other builders can use this functionality as a mix-in.
   * @param attributes the attributes map to be populated.
   */
  static void setRpcAttributes(final Map<AttributeKey<?>, Object> attributes,
      final String packageAndService, final String method) {
    attributes.put(RPC_SYSTEM, RatisAttributes.RpcSystem.RATIS_RPC.name());
    attributes.put(RPC_SERVICE, packageAndService);
    attributes.put(RPC_METHOD, method);
  }

  /**
   * Construct an RPC span name.
   */
  public static String buildSpanName(final String packageAndService, final String method) {
    return packageAndService + "/" + method;
  }
}
