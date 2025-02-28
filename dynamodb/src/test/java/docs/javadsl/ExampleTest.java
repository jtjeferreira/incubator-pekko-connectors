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

import org.apache.pekko.NotUsed;
// #init-client
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;

// #init-client
import org.apache.pekko.stream.connectors.dynamodb.DynamoDbOp;
import org.apache.pekko.stream.connectors.dynamodb.javadsl.DynamoDb;
import org.apache.pekko.stream.connectors.testkit.javadsl.LogCapturingJunit4;
import org.apache.pekko.stream.javadsl.FlowWithContext;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.SourceWithContext;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.*;
// #init-client
import com.github.pjfanning.pekkohttpspi.PekkoHttpClient;
import scala.util.Try;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

// #init-client
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;

public class ExampleTest {
  @Rule public final LogCapturingJunit4 logCapturing = new LogCapturingJunit4();

  static ActorSystem system;
  static DynamoDbAsyncClient client;

  @BeforeClass
  public static void setup() throws Exception {

    // #init-client
    final ActorSystem system = ActorSystem.create();

    // Don't encode credentials in your source code!
    // see https://doc.akka.io/docs/alpakka/current/aws-shared-configuration.html
    StaticCredentialsProvider credentialsProvider =
        StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x"));
    final DynamoDbAsyncClient client =
        DynamoDbAsyncClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.AWS_GLOBAL)
            .httpClient(PekkoHttpClient.builder().withActorSystem(system).build())
            // Possibility to configure the retry policy
            // see https://doc.akka.io/docs/alpakka/current/aws-shared-configuration.html
            // .overrideConfiguration(...)
            // #init-client
            .endpointOverride(new URI("http://localhost:8001/"))
            // #init-client
            .build();

    system.registerOnTermination(() -> client.close());

    // #init-client

    ExampleTest.system = system;
    ExampleTest.client = client;
  }

  @AfterClass
  public static void tearDown() {
    client.close();
    TestKit.shutdownActorSystem(system);
  }

  @Test
  public void listTables() throws Exception {
    // format: off
    // #simple-request
    final CompletionStage<ListTablesResponse> listTables =
        DynamoDb.single(
            client, DynamoDbOp.listTables(), ListTablesRequest.builder().build(), system);
    // #simple-request
    // format: on
    ListTablesResponse result = listTables.toCompletableFuture().get(5, TimeUnit.SECONDS);
    assertNotNull(result.tableNames());
  }

  @Test(expected = ExecutionException.class)
  public void allowMultipleRequests() throws Exception {
    // #flow
    Source<DescribeTableResponse, NotUsed> tableArnSource =
        Source.single(CreateTableRequest.builder().tableName("testTable").build())
            .via(DynamoDb.flow(client, DynamoDbOp.createTable(), 1))
            .map(
                result ->
                    DescribeTableRequest.builder()
                        .tableName(result.tableDescription().tableName())
                        .build())
            .via(DynamoDb.flow(client, DynamoDbOp.describeTable(), 1));
    // #flow

    CompletionStage<List<DescribeTableResponse>> streamCompletion =
        tableArnSource.runWith(Sink.seq(), system);
    // exception expected
    streamCompletion.toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test(expected = ExecutionException.class)
  public void flowWithContext() throws Throwable {
    class SomeContext {}

    // #withContext
    SourceWithContext<PutItemRequest, SomeContext, NotUsed> source = // ???
        // #withContext
        SourceWithContext.fromPairs(
            Source.single(Pair.create(PutItemRequest.builder().build(), new SomeContext())));

    // #withContext

    FlowWithContext<PutItemRequest, SomeContext, Try<PutItemResponse>, SomeContext, NotUsed> flow =
        DynamoDb.flowWithContext(client, DynamoDbOp.putItem(), 1);

    SourceWithContext<PutItemResponse, SomeContext, NotUsed> writtenSource =
        source
            .via(flow)
            .map(
                result -> {
                  if (result.isSuccess()) return result.get();
                  else throw (Exception) result.failed().get();
                });
    // #withContext

    CompletionStage<Pair<PutItemResponse, SomeContext>> streamCompletion =
        writtenSource.runWith(Sink.head(), system);
    // exception expected
    streamCompletion.toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test(expected = ExecutionException.class)
  public void paginated() throws Exception {
    // #paginated
    ScanRequest scanRequest = ScanRequest.builder().tableName("testTable").build();

    Source<ScanResponse, NotUsed> scanPages =
        DynamoDb.source(client, DynamoDbOp.scan(), scanRequest);

    // #paginated
    CompletionStage<List<ScanResponse>> streamCompletion = scanPages.runWith(Sink.seq(), system);
    // exception expected
    streamCompletion.toCompletableFuture().get(1, TimeUnit.SECONDS);
  }

  @Test(expected = ExecutionException.class)
  public void flowPaginated() throws Exception {
    ScanRequest scanRequest = ScanRequest.builder().tableName("testTable").build();
    // #paginated
    Source<ScanResponse, NotUsed> scanPageInFlow =
        Source.single(scanRequest).via(DynamoDb.flowPaginated(client, DynamoDbOp.scan()));
    // #paginated
    CompletionStage<List<ScanResponse>> streamCompletion2 =
        scanPageInFlow.runWith(Sink.seq(), system);
    // exception expected
    streamCompletion2.toCompletableFuture().get(1, TimeUnit.SECONDS);
  }
}
