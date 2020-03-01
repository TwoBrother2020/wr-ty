//package com.wr.ty.grpc.client;
//
//import com.xh.demo.grpc.RegistrationServiceGrpc;
//import com.xh.demo.grpc.WrTy;
//import io.grpc.ManagedChannel;
//import io.grpc.Server;
//import io.grpc.inprocess.InProcessChannelBuilder;
//import io.grpc.inprocess.InProcessServerBuilder;
//import io.grpc.stub.StreamObserver;
//import io.grpc.util.MutableHandlerRegistry;
//import org.junit.After;
//import org.junit.Before;
//import org.junit.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import reactor.test.StepVerifier;
//import reactor.test.publisher.TestPublisher;
//import reactor.test.scheduler.VirtualTimeScheduler;
//
//import java.io.IOException;
//import java.time.Duration;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicReference;
//
///**
// * @author xiaohei
// * @date 2020/2/19 22:20
// */
//public class RegistrationClientTest {
//
//    private final MutableHandlerRegistry serviceRegistry = new MutableHandlerRegistry();
//    Logger logger = LoggerFactory.getLogger(RegistrationClientTest.class);
//    VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
//    Duration timeOut = Duration.ofSeconds(5);
//    WrTy.RegistrationRequest requestClientHello = WrTy.RegistrationRequest.newBuilder().setClientHello(WrTy.ClientHello.getDefaultInstance()).build();
//    WrTy.RegistrationRequest requestHeartbeat = WrTy.RegistrationRequest.newBuilder().setHeartbeat(WrTy.Heartbeat.getDefaultInstance()).build();
//    WrTy.RegistrationRequest requestInstance = WrTy.RegistrationRequest.newBuilder().setInstanceInfo(WrTy.InstanceInfo.getDefaultInstance()).build();
//    WrTy.RegistrationResponse responseHeartbeat = WrTy.RegistrationResponse.newBuilder().setHeartbeat(WrTy.Heartbeat.getDefaultInstance()).build();
//    ManagedChannel channel;
//    Server server;
//    TestPublisher<WrTy.RegistrationRequest> propagateRequest = TestPublisher.create();
//    final AtomicReference<StreamObserver<WrTy.RegistrationResponse>> responseObserverRef = new AtomicReference();
//    @Before
//    public void setup() throws IOException {
//        // Generate a unique in-process server name.
//        String serverName = InProcessServerBuilder.generateName();
//        // Use a mutable service registry for later registering the service impl for each test case.
//        server = InProcessServerBuilder.forName(serverName)
//                .fallbackHandlerRegistry(serviceRegistry).directExecutor().build().start();
//        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
//        RegistrationServiceGrpc.RegistrationImplBase registrationImpl = new RegistrationServiceGrpc.RegistrationImplBase() {
//            @Override
//            public StreamObserver<WrTy.RegistrationRequest> register(StreamObserver<WrTy.RegistrationResponse> responseObserver) {
//                responseObserverRef.set(responseObserver);
//                StreamObserver requestStream = new StreamObserver<WrTy.RegistrationRequest>() {
//                    @Override
//                    public void onNext(WrTy.RegistrationRequest value) {
//                        logger.debug("mock server receive message {}", value.toString());
//                        propagateRequest.next(value);
//                    }
//
//                    @Override
//                    public void onError(Throwable t) {
//                        logger.error("mock server receive error message ", t);
//                        propagateRequest.error(t);
//                    }
//
//                    @Override
//                    public void onCompleted() {
//                        logger.debug("mock server receive complete message");
//                        propagateRequest.complete();
//                    }
//                };
//                return requestStream;
//            }
//        };
//        serviceRegistry.addService(registrationImpl);
//    }
//
//    @After
//    public void tearDown() throws Exception {
//        if (server != null && !server.isShutdown()) {
//            server.shutdown();
//        }
//        if (channel != null && !channel.isShutdown()) {
//            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
//        }
//    }
//
//    @Test
//    public void testCheckClientOutSequence() {
//        TestPublisher<WrTy.InstanceInfo> publisher = TestPublisher.create();
//        RegistrationClient client = new RegistrationClient(scheduler, 1000 * 10, value -> channel);
//        // check client send message sequence
//        StepVerifier.create(propagateRequest)
//                .then(() -> {
//                    client.register(publisher.flux()).subscribe();
//                    scheduler.advanceTimeBy(Duration.ofSeconds(30));// send heartbeat
//                    publisher.next(WrTy.InstanceInfo.getDefaultInstance());
//                    publisher.complete();
//                })
//                .expectNext(requestClientHello)
//                .expectNext(requestHeartbeat)
//                .expectNext(requestInstance)
//                .expectComplete()
//                .log()
//                .verify(timeOut);
//    }
//    @Test
//    public void testClientHeartbeatTimeOut() {
//        TestPublisher<WrTy.InstanceInfo> publisher = TestPublisher.create();
//        RegistrationClient client = new RegistrationClient(scheduler, 1000 * 10, value -> channel);
//        // check client send message sequence
//        StepVerifier.create(propagateRequest)
//                .then(() -> {
//                    client.register(publisher.flux()).subscribe();
//                    scheduler.advanceTimeBy(Duration.ofSeconds(90));// send heartbeat,heartbeat check
//                    scheduler.advanceTime();
//                })
//                .expectNext(requestClientHello)
//                .expectNext(requestHeartbeat)
//                .expectNext(requestHeartbeat)
//                .expectError()
//                .log()
//                .verify(timeOut);
//    }
//
//}