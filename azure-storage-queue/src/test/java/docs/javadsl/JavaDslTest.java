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
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.connectors.azure.storagequeue.AzureQueueSourceSettings;
import org.apache.pekko.stream.connectors.azure.storagequeue.DeleteOrUpdateMessage;
import org.apache.pekko.stream.connectors.azure.storagequeue.javadsl.AzureQueueDeleteOrUpdateSink;
import org.apache.pekko.stream.connectors.azure.storagequeue.javadsl.AzureQueueDeleteSink;
import org.apache.pekko.stream.connectors.azure.storagequeue.javadsl.AzureQueueSink;
import org.apache.pekko.stream.connectors.azure.storagequeue.javadsl.AzureQueueSource;
import org.apache.pekko.stream.connectors.azure.storagequeue.javadsl.AzureQueueWithTimeoutsSink;
import org.apache.pekko.stream.connectors.azure.storagequeue.javadsl.MessageAndDeleteOrUpdate;
import org.apache.pekko.stream.connectors.azure.storagequeue.javadsl.MessageWithTimeouts;
import org.apache.pekko.stream.connectors.testkit.javadsl.LogCapturingJunit4;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.testkit.javadsl.StreamTestKit;
import org.apache.pekko.testkit.javadsl.TestKit;
import com.microsoft.azure.storage.*;
import com.microsoft.azure.storage.queue.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.junit.*;

public class JavaDslTest {
  @Rule public final LogCapturingJunit4 logCapturing = new LogCapturingJunit4();

  private static ActorSystem system;
  private static final String storageConnectionString = System.getenv("AZURE_CONNECTION_STRING");
  private static final Supplier<CloudQueue> queueSupplier =
      () -> {
        try {
          if (storageConnectionString == null) {
            return null;
          }
          CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);
          CloudQueueClient queueClient = storageAccount.createCloudQueueClient();
          return queueClient.getQueueReference("testqueue");
        } catch (Exception ex) {
          throw new RuntimeException("Could not create CloudQueue", ex);
        }
      };

  private static final CloudQueue queue = queueSupplier.get();

  @BeforeClass
  public static void setup() throws StorageException {
    system = ActorSystem.create();

    if (queue != null) {
      queue.createIfNotExists();
    }
  }

  @AfterClass
  public static void teardown() throws StorageException {
    TestKit.shutdownActorSystem(system);
    if (queue != null) {
      queue.deleteIfExists();
    }
  }

  @Before
  public void clearQueue() throws StorageException {
    if (queue != null) {
      queue.clear();
    }
  }

  @After
  public void checkForStageLeaks() {
    StreamTestKit.assertAllStagesStopped(org.apache.pekko.stream.Materializer.matFromSystem(system));
  }

  @Test
  public void testAzureQueueSink()
      throws StorageException, InterruptedException, ExecutionException, TimeoutException {
    Assume.assumeNotNull(queue);
    final Source<Integer, NotUsed> sourceInt = Source.range(1, 10);
    final Source<CloudQueueMessage, NotUsed> source =
        sourceInt.map(i -> new CloudQueueMessage("Java Azure Cloud Test " + i.toString()));

    final Sink<CloudQueueMessage, CompletionStage<Done>> sink =
        AzureQueueSink.create(queueSupplier);

    source.runWith(sink, system).toCompletableFuture().get(10, TimeUnit.SECONDS);

    Assert.assertNotNull(queue.retrieveMessage());
  }

  @Test
  public void testAzureQueueWithTimeoutsSink()
      throws StorageException, InterruptedException, ExecutionException, TimeoutException {
    Assume.assumeNotNull(queue);
    final Source<Integer, NotUsed> sourceInt = Source.range(1, 10);
    final Source<MessageWithTimeouts, NotUsed> source =
        sourceInt.map(
            i ->
                new MessageWithTimeouts(
                    new CloudQueueMessage("Java Azure Cloud Test " + i.toString()), 0, 600));

    final Sink<MessageWithTimeouts, CompletionStage<Done>> sink =
        AzureQueueWithTimeoutsSink.create(queueSupplier);

    source.runWith(sink, system).toCompletableFuture().get(10, TimeUnit.SECONDS);

    Assert.assertNull(
        queue.retrieveMessage()); // There should be no message because of inital visibility timeout
  }

  @Test
  public void testAzureQueueSource()
      throws StorageException, InterruptedException, ExecutionException, TimeoutException {
    Assume.assumeNotNull(queue);

    // Queue 10 Messages
    for (int i = 0; i < 10; i++) {
      queue.addMessage(new CloudQueueMessage("Java Test " + i));
    }

    final Source<CloudQueueMessage, NotUsed> source = AzureQueueSource.create(queueSupplier);

    final CompletionStage<List<CloudQueueMessage>> msgs =
        source.take(10).runWith(Sink.seq(), system);

    msgs.toCompletableFuture().get(10, TimeUnit.SECONDS);
  }

  @Test
  public void testAzureQueueDeleteSink()
      throws StorageException, InterruptedException, ExecutionException, TimeoutException {
    Assume.assumeNotNull(queue);

    // Queue 10 Messages
    for (int i = 0; i < 10; i++) {
      queue.addMessage(new CloudQueueMessage("Java Test " + i));
    }

    // We limit us to buffers of size 1 here, so that there are no stale message in the buffer
    final Source<CloudQueueMessage, NotUsed> source =
        AzureQueueSource.create(
            queueSupplier,
            AzureQueueSourceSettings.create(20, 1).withRetrieveRetryTimeout(Duration.ZERO));

    final Sink<CloudQueueMessage, CompletionStage<Done>> deleteSink =
        AzureQueueDeleteSink.create(queueSupplier);

    final CompletionStage<Done> done = source.take(10).runWith(deleteSink, system);

    done.toCompletableFuture().get(10, TimeUnit.SECONDS);

    Assert.assertNull(queue.retrieveMessage());
  }

  @Test
  public void testAzureQueueDeleteOrUpdateSink()
      throws StorageException, InterruptedException, ExecutionException, TimeoutException {
    Assume.assumeNotNull(queue);

    // Queue 10 Messages
    for (int i = 0; i < 10; i++) {
      queue.addMessage(new CloudQueueMessage("Java Test " + i));
    }

    // We limit us to buffers of size 1 here, so that there are no stale message in the buffer
    final Source<CloudQueueMessage, NotUsed> source =
        AzureQueueSource.create(
            queueSupplier,
            AzureQueueSourceSettings.create(20, 1).withRetrieveRetryTimeout(Duration.ZERO));

    final Sink<MessageAndDeleteOrUpdate, CompletionStage<Done>> deleteOrUpdateSink =
        AzureQueueDeleteOrUpdateSink.create(queueSupplier);

    final CompletionStage<Done> done =
        source
            .take(10)
            .map(msg -> new MessageAndDeleteOrUpdate(msg, DeleteOrUpdateMessage.createDelete()))
            .runWith(deleteOrUpdateSink, system);

    done.toCompletableFuture().get(10, TimeUnit.SECONDS);

    Assert.assertNull(queue.retrieveMessage());
  }
}
