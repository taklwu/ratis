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
package org.apache.ratis.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.ratis.RaftTestUtil;
import org.apache.ratis.RaftTestUtil.SimpleMessage;
import org.apache.ratis.RetryCacheTests;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.proto.RaftProtos;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftClientRequest;
import org.apache.ratis.protocol.exceptions.ResourceUnavailableException;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.server.RetryCache;
import org.apache.ratis.server.impl.MiniRaftCluster;
import org.apache.ratis.server.impl.RetryCacheTestUtil;
import org.apache.ratis.statemachine.StateMachine;
import org.apache.ratis.statemachine.impl.SimpleStateMachine4Testing;
import org.apache.ratis.util.JavaUtils;
import org.apache.ratis.util.Slf4jUtils;
import org.apache.ratis.util.TimeDuration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TestRetryCacheWithGrpcTracing
    extends TestRetryCacheWithGrpc {

  @RegisterExtension
  private static final OpenTelemetryExtension openTelemetryExtension =
      OpenTelemetryExtension.create();

  private List<SpanData> spans;

  @Test
  public void testBasicRetry() throws Exception {
    Span span = openTelemetryExtension
        .getOpenTelemetry().getTracer("TestRetryCacheWithGrpcTracing").spanBuilder("testBasicRetry")
        .startSpan();
    super.testBasicRetry();

    Thread.sleep(1000);

    spans = openTelemetryExtension.getSpans();

    Assertions.assertTrue(
        spans.stream().anyMatch(s -> s.getKind() == SpanKind.CLIENT),
        "Expected at least one span with SpanKind.CLIENT"
    );

    Assertions.assertTrue(
        spans.stream().anyMatch(s -> s.getKind() == SpanKind.SERVER),
        "Expected at least one span with SpanKind.CLIENT"
    );

    // this must fail as there are more than two spans created in the test
    openTelemetryExtension.assertTraces().hasTracesSatisfyingExactly(
        trace -> trace.hasSpansSatisfyingExactly(spanAssert -> spanAssert.hasName("raft.server.appendEntries")),
        trace -> trace.hasSpansSatisfyingExactly(spanAssert -> spanAssert.hasName("test-appendEntries_emitsSpan")));
  }
}
