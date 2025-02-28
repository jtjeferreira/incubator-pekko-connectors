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

package docs.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.connectors.testkit.scaladsl.LogCapturing
import pekko.testkit.TestKit
import com.github.pjfanning.pekkohttpspi.PekkoHttpClient
import org.scalatest.BeforeAndAfterAll
import software.amazon.awssdk.auth.credentials.{ AwsBasicCredentials, StaticCredentialsProvider }
// #awsRetryConfiguration
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy
import software.amazon.awssdk.core.retry.conditions.RetryCondition

// #awsRetryConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import org.scalatest.wordspec.AnyWordSpecLike

class RetrySpec
    extends TestKit(ActorSystem("RetrySpec"))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with LogCapturing {

  // #clientRetryConfig
  implicit val client: DynamoDbAsyncClient = DynamoDbAsyncClient
    .builder()
    .region(Region.AWS_GLOBAL)
    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
    .httpClient(PekkoHttpClient.builder().withActorSystem(system).build())
    // #awsRetryConfiguration
    .overrideConfiguration(
      ClientOverrideConfiguration
        .builder()
        .retryPolicy(
          // This example shows the AWS SDK 2 `RetryPolicy.defaultRetryPolicy()`
          // See https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/core/retry/RetryPolicy.html
          RetryPolicy.builder
            .backoffStrategy(BackoffStrategy.defaultStrategy)
            .throttlingBackoffStrategy(BackoffStrategy.defaultThrottlingStrategy)
            .numRetries(SdkDefaultRetrySetting.defaultMaxAttempts)
            .retryCondition(RetryCondition.defaultRetryCondition)
            .build)
        .build())
    // #awsRetryConfiguration
    .build()
  // #clientRetryConfig

  override def afterAll(): Unit = {
    client.close()
    shutdown();
  }

}
