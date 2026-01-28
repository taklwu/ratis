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

import static org.apache.ratis.trace.RatisAttributes.OPERATION_NAME;
import static org.apache.ratis.trace.RatisAttributes.OPERATION_TYPE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import org.apache.ratis.protocol.RaftClientRequest;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.trace.RatisAttributes;
import org.apache.ratis.trace.TraceUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;


public class OperationSpanBuilder implements Supplier<Span> {

  private static final String DEFAULT_OPERATION = "DEFAULT_OPERATION";
  private static final String UNKNOWN_PEER = "UNKNOWN_PEER";

  private final RaftPeerId server;
  private final Map<AttributeKey<?>, Object> attributes = new HashMap<>();

  public OperationSpanBuilder(RaftPeerId server) {
    this.server = server;
    setRaftPeerId(server);
  }

  @Override
  public Span get() {
    return build();
  }


  public OperationSpanBuilder setOperationType(final RaftClientRequest.Type type) {
    attributes.put(OPERATION_TYPE, type.getTypeCase().name());
    return this;
  }

  public OperationSpanBuilder setOperationName(final String name) {
    attributes.put(OPERATION_NAME, name);
    return this;
  }

  public OperationSpanBuilder setRaftPeerId(RaftPeerId server) {
    attributes.put(RatisAttributes.PEER_ID, server == null ? UNKNOWN_PEER : server.toString());
    return this;
  }

  @SuppressWarnings("unchecked")
  public Span build() {
    final String name = attributes.getOrDefault(OPERATION_NAME, DEFAULT_OPERATION).toString();
    final SpanBuilder builder = TraceUtil.getGlobalTracer().spanBuilder(name)
        // TODO: what about clients embedded in Master/RegionServer/Gateways/&c?
        .setSpanKind(SpanKind.CLIENT);
    attributes.forEach((k, v) -> builder.setAttribute((AttributeKey<? super Object>) k, v));
    return builder.startSpan();
  }
}
