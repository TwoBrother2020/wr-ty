/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wr.ty.grpc.handler.client;

import com.wr.ty.grpc.SubscriberFluxSinkWrap;
import com.wr.ty.grpc.core.channel.ChannelHandler;
import com.wr.ty.grpc.core.channel.ChannelPipeline;
import com.wr.ty.grpc.util.ProtocolMessageEnvelopes;
import com.xh.demo.grpc.WrTy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 *
 */
public class ClientHandshakeHandler implements ChannelHandler {
    private final Logger logger = LoggerFactory.getLogger(ClientHandshakeHandler.class);

    protected static final IllegalStateException DATA_BEFORE_HANDSHAKE_REPLY = new IllegalStateException("Data before handshake reply");
    private String prefix;


    @Override
    public Flux<WrTy.ProtocolMessageEnvelope> handle(Flux<WrTy.ProtocolMessageEnvelope> inputStream, ChannelPipeline pipeline) {

        return Flux.create(fluxSink -> {
            prefix = pipeline.pipelineId();
            AtomicBoolean handshakeCompleted = new AtomicBoolean(false);
            pipeline.handle(Flux.just(ProtocolMessageEnvelopes.CLIENT_HELLO).concatWith(inputStream)).log()
                    .flatMap(handshakeVerifier(handshakeCompleted))
                    .subscribe(new SubscriberFluxSinkWrap(fluxSink));

        });
    }

    protected Function<WrTy.ProtocolMessageEnvelope, Flux<WrTy.ProtocolMessageEnvelope>> handshakeVerifier(AtomicBoolean handshakeCompleted) {
        return value -> {

            if (value.getItemCase() == WrTy.ProtocolMessageEnvelope.ItemCase.SERVERHELLO) {
                if (!handshakeCompleted.getAndSet(true)) {
                    logger.debug("{} Handshake has completed", prefix);
                    return Flux.empty();
                }
            }
            if (value.getItemCase() == WrTy.ProtocolMessageEnvelope.ItemCase.INSTANCEINFO && !handshakeCompleted.get()) {
                logger.error("{} Data sent from server before handshake has completed", prefix);
                return Flux.error(DATA_BEFORE_HANDSHAKE_REPLY);
            }
            return Flux.just(value);
        };
    }
}
