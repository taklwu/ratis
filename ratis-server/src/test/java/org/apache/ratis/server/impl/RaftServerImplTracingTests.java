package org.apache.ratis.server.impl;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.proto.RaftProtos;
import org.apache.ratis.proto.RaftProtos.AppendEntriesRequestProto;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.StateMachine;
import org.apache.ratis.statemachine.impl.SimpleStateMachine4Testing;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.apache.ratis.trace.RatisAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class RaftServerImplTracingTests {

  @RegisterExtension
  private static final OpenTelemetryExtension openTelemetryExtension =
      OpenTelemetryExtension.create();

  private List<SpanData> spans;

  @Test
  public void appendEntriesEmitsSpan() throws Exception {
    // mock dependencies for constructor; stub calls used during construction
    RaftGroup group = RaftGroup.emptyGroup();
    StateMachine sm = new SimpleStateMachine4Testing();
    RaftServerProxy proxy = mock(RaftServerProxy.class);
    when(proxy.getId()).thenReturn(RaftPeerId.valueOf("peer1"));
    when(proxy.getProperties()).thenReturn(new RaftProperties());
    when(proxy.getThreadGroup()).thenReturn(new ThreadGroup("test"));


    // create test server (may require additional stubbing depending on your constructor)
    RaftServerImpl server = new RaftServerImpl(group, sm, proxy, RaftStorage.StartupOption.FORMAT);

    // build a minimal AppendEntriesRequestProto; populate requestor id so attribute is set
    AppendEntriesRequestProto request = AppendEntriesRequestProto.newBuilder()
        .setServerRequest(RaftProtos.RaftRpcRequestProto.newBuilder()
            .setRequestorId(ByteString.copyFromUtf8("callerB"))
            .build())
        .build();

    // invoke appendEntries
    Span span = openTelemetryExtension
        .getOpenTelemetry().getTracer("test").spanBuilder("test-appendEntries_emitsSpan")
        .startSpan();
          try {
            server.appendEntries(request);
          } catch (IOException | RuntimeException ignored) {
            // appendEntries may throw depending on mocked internals; the goal is span emission
          } finally {
            span.end();
          }

    Thread.sleep(1000);

    // verify exported span
    spans = openTelemetryExtension.getSpans();

    assertEquals(2, spans.size());
    openTelemetryExtension.assertTraces().hasTracesSatisfyingExactly(
        trace -> trace.hasSpansSatisfyingExactly(
            spanAssert -> spanAssert.hasName("raft.server.appendEntries")
        ),
        trace -> trace.hasSpansSatisfyingExactly(
            spanAssert -> spanAssert.hasName("test-appendEntries_emitsSpan")
        )
    );

    SpanData raftSpan = spans.stream()
        .filter(s -> "raft.server.appendEntries".equals(s.getName()))
        .findFirst().get();

    assertTrue(raftSpan.getAttributes().get(RatisAttributes.ATTR_MEMBER_ID).contains("peer1"));
    assertTrue(raftSpan.getAttributes().get(RatisAttributes.ATTR_CALLER_ID).contains("callerB"));

  }
}

