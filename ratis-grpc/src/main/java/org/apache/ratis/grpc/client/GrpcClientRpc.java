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
package org.apache.ratis.grpc.client;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.apache.ratis.client.impl.ClientProtoUtils;
import org.apache.ratis.client.impl.RaftClientRpcWithProxy;
import org.apache.ratis.client.trace.RpcClientSpanBuilder;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.grpc.GrpcUtil;
import org.apache.ratis.protocol.*;
import org.apache.ratis.protocol.exceptions.AlreadyClosedException;
import org.apache.ratis.thirdparty.io.grpc.StatusRuntimeException;
import org.apache.ratis.thirdparty.io.grpc.stub.StreamObserver;
import org.apache.ratis.proto.RaftProtos.GroupInfoRequestProto;
import org.apache.ratis.proto.RaftProtos.GroupListRequestProto;
import org.apache.ratis.proto.RaftProtos.GroupManagementRequestProto;
import org.apache.ratis.proto.RaftProtos.RaftClientReplyProto;
import org.apache.ratis.proto.RaftProtos.RaftClientRequestProto;
import org.apache.ratis.proto.RaftProtos.SetConfigurationRequestProto;
import org.apache.ratis.proto.RaftProtos.TransferLeadershipRequestProto;
import org.apache.ratis.proto.RaftProtos.SnapshotManagementRequestProto;
import org.apache.ratis.proto.RaftProtos.LeaderElectionManagementRequestProto;
import org.apache.ratis.thirdparty.io.netty.handler.ssl.SslContext;
import org.apache.ratis.trace.TraceUtil;
import org.apache.ratis.util.IOUtils;
import org.apache.ratis.util.JavaUtils;
import org.apache.ratis.util.PeerProxyMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class GrpcClientRpc extends RaftClientRpcWithProxy<GrpcClientProtocolClient> {
  public static final Logger LOG = LoggerFactory.getLogger(GrpcClientRpc.class);

  private final ClientId clientId;
  private final int maxMessageSize;

  public GrpcClientRpc(ClientId clientId, RaftProperties properties,
      SslContext adminSslContext, SslContext clientSslContext) {
    super(new PeerProxyMap<>(clientId.toString(),
        p -> new GrpcClientProtocolClient(clientId, p, properties, adminSslContext, clientSslContext)));
    this.clientId = clientId;
    this.maxMessageSize = GrpcConfigKeys.messageSizeMax(properties, LOG::debug).getSizeInt();
  }

  @Override
  public CompletableFuture<RaftClientReply> sendRequestAsync(
      RaftClientRequest request) {
    final RaftPeerId serverId = request.getServerId();
    try {
      final GrpcClientProtocolClient proxy = getProxies().getProxy(serverId);
      final Supplier<Span> supplier = new RpcClientSpanBuilder()
          .setMethod(request.getType().toString(),
          request.getClass().getName() + "/sendRequestAsync")
          .setProxyName(proxy.getName())
          .setPeerId(serverId.toString());
      // Reuse the same grpc stream for all async calls.
      return TraceUtil.tracedFuture(() -> proxy.getOrderedStreamObservers().onNext(request),
          supplier);
    } catch (Exception e) {
      return JavaUtils.completeExceptionally(e);
    }
  }

  @Override
  public CompletableFuture<RaftClientReply> sendRequestAsyncUnordered(RaftClientRequest request) {
    final RaftPeerId serverId = request.getServerId();
    try {
      final GrpcClientProtocolClient proxy = getProxies().getProxy(serverId);
      final Supplier<Span> supplier = new RpcClientSpanBuilder()
          .setMethod(request.getType().toString(),
              request.getClass().getName() + "/sendRequestAsyncUnordered")
          .setProxyName(proxy.getName())
          .setPeerId(serverId.toString());
      // Reuse the same grpc stream for all async calls.
      return TraceUtil.tracedFuture(() -> proxy.getUnorderedAsyncStreamObservers().onNext(request),
          supplier);
    } catch (Exception e) {
      LOG.error(clientId + ": Failed " + request, e);
      return JavaUtils.completeExceptionally(e);
    }
  }

  @Override
  public RaftClientReply sendRequest(RaftClientRequest request)
      throws IOException {
    final RaftPeerId serverId = request.getServerId();
    final GrpcClientProtocolClient proxy = getProxies().getProxy(serverId);
    final Span span = new RpcClientSpanBuilder()
        .setMethod(request.getType().toString(),
            request.getClass().getName() + "/sendRequest")
        .setPeerId(serverId.toString())
        .setProxyName(proxy.getName())
        .build();
    try (Scope scope = span.makeCurrent()) {
      if (request instanceof GroupManagementRequest) {
        final GroupManagementRequestProto proto =
            ClientProtoUtils.toGroupManagementRequestProto((GroupManagementRequest) request);
        span.addEvent(proto.getDescriptorForType().getFullName());
        return ClientProtoUtils.toRaftClientReply(proxy.groupAdd(proto));
      } else if (request instanceof SetConfigurationRequest) {
        final SetConfigurationRequestProto proto =
            ClientProtoUtils.toSetConfigurationRequestProto((SetConfigurationRequest) request);
        span.addEvent(proto.getDescriptorForType().getFullName());
        return ClientProtoUtils.toRaftClientReply(proxy.setConfiguration(proto));
      } else if (request instanceof GroupListRequest) {
        final GroupListRequestProto proto = ClientProtoUtils.toGroupListRequestProto((GroupListRequest) request);
        span.addEvent(proto.getDescriptorForType().getFullName());
        return ClientProtoUtils.toGroupListReply(proxy.groupList(proto));
      } else if (request instanceof GroupInfoRequest) {
        final GroupInfoRequestProto proto = ClientProtoUtils.toGroupInfoRequestProto((GroupInfoRequest) request);
        span.addEvent(proto.getDescriptorForType().getFullName());
        return ClientProtoUtils.toGroupInfoReply(proxy.groupInfo(proto));
      } else if (request instanceof TransferLeadershipRequest) {
        final TransferLeadershipRequestProto proto =
            ClientProtoUtils.toTransferLeadershipRequestProto((TransferLeadershipRequest) request);
        span.addEvent(proto.getDescriptorForType().getFullName());
        return ClientProtoUtils.toRaftClientReply(proxy.transferLeadership(proto));
      } else if (request instanceof SnapshotManagementRequest) {
        final SnapshotManagementRequestProto proto =
            ClientProtoUtils.toSnapshotManagementRequestProto((SnapshotManagementRequest) request);
        span.addEvent(proto.getDescriptorForType().getFullName());
        return ClientProtoUtils.toRaftClientReply(proxy.snapshotManagement(proto));
      } else if (request instanceof LeaderElectionManagementRequest) {
        final LeaderElectionManagementRequestProto proto =
            ClientProtoUtils.toLeaderElectionManagementRequestProto((LeaderElectionManagementRequest) request);
        span.addEvent(proto.getDescriptorForType().getFullName());
        return ClientProtoUtils.toRaftClientReply(proxy.leaderElectionManagement(proto));
      } else {
        final CompletableFuture<RaftClientReply> f = sendRequest(request, proxy, span);
        // TODO: timeout support
        try {
          return f.get();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new InterruptedIOException("Interrupted while waiting for response of request " + request);
        } catch (ExecutionException e) {
          if (LOG.isTraceEnabled()) {
            LOG.trace(clientId + ": failed " + request, e);
          }
          throw IOUtils.toIOException(e);
        }
      }
    } finally {
      span.end();
    }
  }

  private CompletableFuture<RaftClientReply> sendRequest(
      RaftClientRequest request, GrpcClientProtocolClient proxy, Span span) throws IOException {
    final RaftClientRequestProto requestProto =
        toRaftClientRequestProto(request);
    span.addEvent("Other/" + requestProto.getRpcRequest().getDescriptorForType().getFullName());
    final CompletableFuture<RaftClientReplyProto> replyFuture = new CompletableFuture<>();
    // create a new grpc stream for each non-async call.
    final StreamObserver<RaftClientRequestProto> requestObserver =
        proxy.unorderedWithTimeout(new StreamObserver<RaftClientReplyProto>() {
          @Override
          public void onNext(RaftClientReplyProto value) {
            replyFuture.complete(value);
          }

          @Override
          public void onError(Throwable t) {
            replyFuture.completeExceptionally(GrpcUtil.unwrapIOException(t));
          }

          @Override
          public void onCompleted() {
            if (!replyFuture.isDone()) {
              replyFuture.completeExceptionally(
                  new AlreadyClosedException(clientId + ": Stream completed but no reply for request " + request));
            }
          }
        });
    requestObserver.onNext(requestProto);
    requestObserver.onCompleted();

    return replyFuture.thenApply(ClientProtoUtils::toRaftClientReply);
  }

  private RaftClientRequestProto toRaftClientRequestProto(RaftClientRequest request) throws IOException {
    final RaftClientRequestProto proto = ClientProtoUtils.toRaftClientRequestProto(request);
    if (proto.getSerializedSize() > maxMessageSize) {
      throw new IOException(clientId + ": Message size:" + proto.getSerializedSize()
          + " exceeds maximum:" + maxMessageSize);
    }
    return proto;
  }

  @Override
  public boolean shouldReconnect(Throwable e) {
    final Throwable cause = e.getCause();
    if (e instanceof IOException && cause instanceof StatusRuntimeException) {
      return !((StatusRuntimeException) cause).getStatus().isOk();
    } else if (e instanceof IllegalArgumentException) {
      return e.getMessage().contains("null frame before EOS");
    }
    return super.shouldReconnect(e);
  }
}
