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

package org.apache.pekko.stream.connectors.google.auth

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.connectors.google.RequestSettings
import com.google.auth.{ Credentials => GoogleCredentials, RequestMetadataCallback }

import java.net.URI
import java.util
import java.util.concurrent.Executor
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success }

@InternalApi
private[auth] final class GoogleOAuth2Credentials(credentials: OAuth2Credentials)(
    implicit ec: ExecutionContext,
    settings: RequestSettings) extends GoogleCredentials {

  override def getAuthenticationType: String = "OAuth2"
  override def hasRequestMetadata: Boolean = true
  override def hasRequestMetadataOnly: Boolean = true

  override def getRequestMetadata(uri: URI): util.Map[String, util.List[String]] =
    Await.result(requestMetadata, Duration.Inf)

  override def getRequestMetadata(uri: URI, executor: Executor, callback: RequestMetadataCallback): Unit = {
    implicit val ec = ExecutionContext.fromExecutor(executor)
    requestMetadata.onComplete {
      case Success(metadata) => callback.onSuccess(metadata)
      case Failure(ex)       => callback.onFailure(ex)
    }
  }

  private def requestMetadata(implicit ec: ExecutionContext): Future[util.Map[String, util.List[String]]] = {
    credentials.get().map { token =>
      util.Collections.singletonMap("Authorization", util.Collections.singletonList(token.toString))
    }
  }

  override def refresh(): Unit = credentials.refresh()
}
