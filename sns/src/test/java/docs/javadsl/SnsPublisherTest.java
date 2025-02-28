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

package docs.javadsl;

import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.stream.connectors.sns.javadsl.SnsPublisher;
import org.apache.pekko.stream.connectors.testkit.javadsl.LogCapturingJunit4;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;

// #init-client
import java.net.URI;
import com.github.pjfanning.pekkohttpspi.PekkoHttpClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
// #init-client

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.CoreMatchers.*;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SnsPublisherTest {
  @Rule public final LogCapturingJunit4 logCapturing = new LogCapturingJunit4();

  static ActorSystem system;
  static SnsAsyncClient snsClient;
  static String topicArn;

  static final String endpoint = "http://localhost:4100";

  @BeforeClass
  public static void setUpBeforeClass() throws ExecutionException, InterruptedException {
    system = ActorSystem.create("SnsPublisherTest");
    snsClient = createSnsClient();
    topicArn =
        snsClient
            .createTopic(CreateTopicRequest.builder().name("alpakka-java-topic-1").build())
            .get()
            .topicArn();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    Http.get(system)
        .shutdownAllConnectionPools()
        .thenRun(() -> TestKit.shutdownActorSystem(system))
        .toCompletableFuture()
        .get(2, TimeUnit.SECONDS);
  }

  static SnsAsyncClient createSnsClient() {
    // #init-client

    // Don't encode credentials in your source code!
    // see https://doc.akka.io/docs/alpakka/current/aws-shared-configuration.html
    StaticCredentialsProvider credentialsProvider =
        StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x"));
    final SnsAsyncClient awsSnsClient =
        SnsAsyncClient.builder()
            .credentialsProvider(credentialsProvider)
            // #init-client
            .endpointOverride(URI.create(endpoint))
            // #init-client
            .region(Region.EU_CENTRAL_1)
            .httpClient(PekkoHttpClient.builder().withActorSystem(system).build())
            // Possibility to configure the retry policy
            // see https://doc.akka.io/docs/alpakka/current/aws-shared-configuration.html
            // .overrideConfiguration(...)
            .build();

    system.registerOnTermination(() -> awsSnsClient.close());
    // #init-client

    return awsSnsClient;
  }

  void documentation() {
    // #init-system
    ActorSystem system = ActorSystem.create();
    // #init-system
  }

  @Test
  public void sinkShouldPublishString() throws Exception {
    CompletionStage<Done> completion =
        // #use-sink
        Source.single("message").runWith(SnsPublisher.createSink(topicArn, snsClient), system);

    // #use-sink
    assertThat(completion.toCompletableFuture().get(2, TimeUnit.SECONDS), is(Done.getInstance()));
  }

  @Test
  public void sinkShouldPublishRequest() throws Exception {
    CompletionStage<Done> completion =
        // #use-sink
        Source.single(PublishRequest.builder().message("message").build())
            .runWith(SnsPublisher.createPublishSink(topicArn, snsClient), system);

    // #use-sink
    assertThat(completion.toCompletableFuture().get(2, TimeUnit.SECONDS), is(Done.getInstance()));
  }

  @Test
  public void sinkShouldPublishRequestWithDynamicTopic() throws Exception {
    CompletionStage<Done> completion =
        // #use-sink
        Source.single(PublishRequest.builder().message("message").topicArn(topicArn).build())
            .runWith(SnsPublisher.createPublishSink(snsClient), system);
    // #use-sink
    assertThat(completion.toCompletableFuture().get(2, TimeUnit.SECONDS), is(Done.getInstance()));
  }

  @Test
  public void flowShouldPublishString() throws Exception {
    CompletionStage<Done> completion =
        // #use-flow
        Source.single("message")
            .via(SnsPublisher.createFlow(topicArn, snsClient))
            .runWith(Sink.foreach(res -> System.out.println(res.messageId())), system);

    // #use-flow
    assertThat(completion.toCompletableFuture().get(2, TimeUnit.SECONDS), is(Done.getInstance()));
  }

  @Test
  public void flowShouldPublishRequest() throws Exception {
    CompletionStage<Done> completion =
        // #use-flow
        Source.single(PublishRequest.builder().message("message").build())
            .via(SnsPublisher.createPublishFlow(topicArn, snsClient))
            .runWith(Sink.foreach(res -> System.out.println(res.messageId())), system);

    // #use-flow
    assertThat(completion.toCompletableFuture().get(2, TimeUnit.SECONDS), is(Done.getInstance()));
  }

  @Test
  public void flowShouldPublishRequestWithDynamicTopic() throws Exception {
    CompletionStage<Done> completion =
        // #use-flow
        Source.single(PublishRequest.builder().message("message").topicArn(topicArn).build())
            .via(SnsPublisher.createPublishFlow(snsClient))
            .runWith(Sink.foreach(res -> System.out.println(res.messageId())), system);

    // #use-flow
    assertThat(completion.toCompletableFuture().get(2, TimeUnit.SECONDS), is(Done.getInstance()));
  }
}
