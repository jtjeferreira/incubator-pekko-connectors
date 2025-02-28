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

package org.apache.pekko.stream.connectors.huawei.pushkit.impl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.LoggingAdapter
import pekko.http.scaladsl.{ HttpExt, HttpsConnectionContext }
import pekko.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpRequest, HttpResponse }
import pekko.http.scaladsl.settings.ConnectionPoolSettings
import pekko.http.scaladsl.unmarshalling.Unmarshal
import HmsTokenApi.AccessTokenExpiry
import pekko.stream.connectors.huawei.pushkit.HmsSettings
import pekko.stream.connectors.testkit.scaladsl.LogCapturing
import pekko.testkit.TestKit
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ verify, when }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.DurationInt

class HmsTokenApiSpec
    extends TestKit(ActorSystem())
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterAll
    with LogCapturing {

  override def afterAll() =
    TestKit.shutdownActorSystem(system)

  implicit val defaultPatience =
    PatienceConfig(timeout = 2.seconds, interval = 50.millis)

  val config = HmsSettings()

  implicit val executionContext: ExecutionContext = system.dispatcher

  "HmsTokenApi" should {

    "call the api as the docs want to" in {

      val http = mock[HttpExt]
      when(
        http.singleRequest(any[HttpRequest](),
          any[HttpsConnectionContext](),
          any[ConnectionPoolSettings](),
          any[LoggingAdapter]())).thenReturn(
        Future.successful(
          HttpResponse(
            entity = HttpEntity(ContentTypes.`application/json`,
              """{"access_token": "token", "token_type": "String", "expires_in": 3600}"""))))

      val api = new HmsTokenApi(http, system, Option.empty)
      Await.result(api.getAccessToken(config.appId, config.appSecret), defaultPatience.timeout)

      val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])
      verify(http).singleRequest(captor.capture(),
        any[HttpsConnectionContext](),
        any[ConnectionPoolSettings](),
        any[LoggingAdapter]())
      val request: HttpRequest = captor.getValue

      request.uri.toString() shouldBe "https://oauth-login.cloud.huawei.com/oauth2/v3/token"
      val data = Unmarshal(request.entity).to[String].futureValue
      data should startWith(
        "grant_type=client_credentials&client_secret=a192c0f08d03216b0f03b946918d5c725bbf54264a434227928c612012eefd24&client_id=105260069")
    }

    "return the token" in {
      val http = mock[HttpExt]
      when(
        http.singleRequest(any[HttpRequest](),
          any[HttpsConnectionContext](),
          any[ConnectionPoolSettings](),
          any[LoggingAdapter]())).thenReturn(
        Future.successful(
          HttpResponse(
            entity = HttpEntity(ContentTypes.`application/json`,
              """{"access_token": "token", "token_type": "String", "expires_in": 3600}"""))))

      val api = new HmsTokenApi(http, system, Option.empty)
      api.getAccessToken(config.appId, config.appSecret).futureValue should matchPattern {
        case AccessTokenExpiry("token", exp) if exp > (System.currentTimeMillis / 1000L + 3000L) =>
      }
    }
  }

}
