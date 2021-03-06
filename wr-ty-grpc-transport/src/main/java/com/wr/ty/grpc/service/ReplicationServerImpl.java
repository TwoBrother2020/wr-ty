package com.wr.ty.grpc.service;

import com.wr.ty.grpc.Session;
import com.wr.ty.grpc.TransportConfig;
import com.wr.ty.grpc.core.PipelineNameGenerator;
import com.wr.ty.grpc.core.channel.ChannelHandler;
import com.wr.ty.grpc.core.channel.ChannelPipeline;
import com.wr.ty.grpc.core.channel.ChannelPipelineFactory;
import com.wr.ty.grpc.core.channel.DefaultChannelPipeline;
import com.wr.ty.grpc.handler.server.*;
import com.wr.ty.grpc.register.Registry;
import com.wr.ty.grpc.util.ProtocolMessageEnvelopes;
import com.wr.ty.grpc.util.SourceIdGenerator;
import com.wr.ty.grpc.util.SubscribeMessages;
import com.xh.demo.grpc.ReplicationServiceGrpc;
import com.xh.demo.grpc.WrTy;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.wr.ty.grpc.util.ProtocolMessageEnvelopes.*;

/**
 * @author xiaohei
 * @date 2020/2/29 17:48
 */
public class ReplicationServerImpl extends ReplicationServiceGrpc.ReplicationServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationServerImpl.class);

    private final ChannelPipelineFactory channelPipelineFactory;
    private final Registry register;
    private static Function<WrTy.SubscribeRequest, WrTy.ProtocolMessageEnvelope> inputMap = value -> {

        WrTy.SubscribeRequest.ItemCase itemCase = value.getItemCase();
        switch (itemCase) {
            case HEARTBEAT:
                WrTy.Heartbeat heartbeat = value.getHeartbeat();
                return ProtocolMessageEnvelopes.fromHeartbeat(heartbeat);
            case CLIENTHELLO:
                return ProtocolMessageEnvelopes.fromClientHello(value.getClientHello());
            case INTERESTREGISTRATION:
                return fromInterestRegistration(value.getInterestRegistration());
            default:
                throw new RuntimeException("unknown message type");
        }
    };
    private static Function<WrTy.ProtocolMessageEnvelope, WrTy.SubscribeResponse> outMap = value -> {
        WrTy.ProtocolMessageEnvelope.ItemCase itemCase = value.getItemCase();
        switch (itemCase) {
            case SERVERHELLO:
                return SubscribeMessages.fromServerHello(value.getServerHello());
            case HEARTBEAT:
                return SubscribeMessages.fromHeartbeat(value.getHeartbeat());
            case CHANGENOTIFICATION:
                return SubscribeMessages.fromChangeNotification(value.getChangeNotification());
            default:
                throw new RuntimeException("unknown message type");
        }
    };

    public ReplicationServerImpl(Registry registry,
                                 TransportConfig config,
                                 PipelineNameGenerator pipelineNameGenerator,
                                 Scheduler scheduler
    ) {
        Objects.requireNonNull(scheduler);
        Objects.requireNonNull(registry);
        this.register = registry;
        this.channelPipelineFactory = () -> Mono.create(fluxSink -> {
            String pipelineId = pipelineNameGenerator.generate("registration pipeline");
            List<ChannelHandler> handlers = new ArrayList<>();
            handlers.add(new ServerHeartbeatHandler(config.heartbeatTimeout(), scheduler));
            handlers.add(new ServerHandshakeHandler());
            handlers.add(new ServerReplicationHandler());
            handlers.add(new ServerSubscribeHandler(register));
            DefaultChannelPipeline pipeline = new DefaultChannelPipeline(pipelineId, 0, handlers);
            fluxSink.success(pipeline);

        });

    }

    @Override
    public StreamObserver<WrTy.SubscribeRequest> subscribe(StreamObserver<WrTy.SubscribeResponse> responseObserver) {
        Session<WrTy.SubscribeRequest, WrTy.SubscribeResponse> session = new Session(
                responseObserver,
                channelPipelineFactory,
                inputMap,
                outMap
        );
        return session;
    }

}
