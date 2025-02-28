/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) since 2016 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.connectors.eip.javadsl;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.japi.function.Function2;
import org.apache.pekko.kafka.CommitterSettings;
import org.apache.pekko.kafka.ConsumerMessage;
import org.apache.pekko.kafka.ConsumerSettings;
import org.apache.pekko.kafka.Subscriptions;
import org.apache.pekko.kafka.javadsl.Committer;
import org.apache.pekko.kafka.javadsl.Consumer;
import org.apache.pekko.stream.*;
import org.apache.pekko.stream.javadsl.*;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class PassThroughExamples {
  private static ActorSystem system;

  @Test
  public void passThroughWithKeep() throws InterruptedException, ExecutionException {
    // #PassThroughWithKeep
    // Sample Source
    Source<Integer, NotUsed> source = Source.from(Arrays.asList(1, 2, 3));

    // Pass through this flow maintaining the original message
    Flow<Integer, Integer, NotUsed> passThroughMe = Flow.of(Integer.class).map(i -> i * 10);

    CompletionStage<List<Integer>> ret =
        source.via(PassThroughFlow.create(passThroughMe, Keep.right())).runWith(Sink.seq(), system);

    // Verify results
    List<Integer> list = ret.toCompletableFuture().get();
    assert list.equals(Arrays.asList(1, 2, 3));
    // #PassThroughWithKeep
  }

  @Test
  public void passThroughTuple() throws InterruptedException, ExecutionException {
    // #PassThroughTuple
    // Sample Source
    Source<Integer, NotUsed> source = Source.from(Arrays.asList(1, 2, 3));

    // Pass through this flow maintaining the original message
    Flow<Integer, Integer, NotUsed> passThroughMe = Flow.of(Integer.class).map(i -> i * 10);

    CompletionStage<List<Pair<Integer, Integer>>> ret =
        source.via(PassThroughFlow.create(passThroughMe)).runWith(Sink.seq(), system);

    // Verify results
    List<Pair<Integer, Integer>> list = ret.toCompletableFuture().get();
    assert list.equals(
        Arrays.asList(
            new Pair<Integer, Integer>(10, 1),
            new Pair<Integer, Integer>(20, 2),
            new Pair<Integer, Integer>(30, 3)));
    // #PassThroughTuple
  }

  @BeforeClass
  public static void setup() throws Exception {
    system = ActorSystem.create();
  }

  @AfterClass
  public static void teardown() throws Exception {
    TestKit.shutdownActorSystem(system);
  }
}

// #PassThrough
class PassThroughFlow {

  public static <A, T> Graph<FlowShape<A, Pair<T, A>>, NotUsed> create(Flow<A, T, NotUsed> flow) {
    return create(flow, Keep.both());
  }

  public static <A, T, O> Graph<FlowShape<A, O>, NotUsed> create(
      Flow<A, T, NotUsed> flow, Function2<T, A, O> output) {
    return Flow.fromGraph(
        GraphDSL.create(
            builder -> {
              UniformFanOutShape<A, A> broadcast = builder.add(Broadcast.create(2));
              FanInShape2<T, A, O> zip = builder.add(ZipWith.create(output));
              builder.from(broadcast.out(0)).via(builder.add(flow)).toInlet(zip.in0());
              builder.from(broadcast.out(1)).toInlet(zip.in1());
              return FlowShape.apply(broadcast.in(), zip.out());
            }));
  }
}
// #PassThrough

class PassThroughFlowKafkaCommitExample {
  private static ActorSystem system;

  public void dummy() {
    // #passThroughKafkaFlow
    Flow<ConsumerMessage.CommittableMessage<String, byte[]>, String, NotUsed> writeFlow =
        Flow.fromFunction(i -> i.record().value().toString());

    ConsumerSettings<String, byte[]> consumerSettings =
        ConsumerSettings.create(system, new StringDeserializer(), new ByteArrayDeserializer());
    CommitterSettings comitterSettings = CommitterSettings.create(system);
    Consumer.DrainingControl<Done> control =
        Consumer.committableSource(consumerSettings, Subscriptions.topics("topic1"))
            .via(PassThroughFlow.create(writeFlow, Keep.right()))
            .map(i -> i.committableOffset())
            .toMat(Committer.sink(comitterSettings), Keep.both())
            .mapMaterializedValue(Consumer::createDrainingControl)
            .run(system);

    // #passThroughKafkaFlow
  }

  @BeforeClass
  public static void setup() throws Exception {
    system = ActorSystem.create();
  }
}
