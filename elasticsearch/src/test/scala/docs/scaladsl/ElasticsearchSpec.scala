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
import pekko.http.scaladsl.{ Http, HttpExt }
import pekko.http.scaladsl.model.Uri.Path
import pekko.http.scaladsl.model.{ HttpMethods, HttpRequest, Uri }
import pekko.stream.connectors.elasticsearch._
import pekko.stream.connectors.testkit.scaladsl.LogCapturing
import pekko.testkit.TestKit
import org.scalatest.{ BeforeAndAfterAll, Inspectors }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ElasticsearchSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with Inspectors
    with LogCapturing
    with ElasticsearchConnectorBehaviour
    with ElasticsearchSpecUtils
    with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem()
  implicit val http: HttpExt = Http()

  val clientV5: ElasticsearchConnectionSettings =
    ElasticsearchConnectionSettings("http://localhost:9201")
  val clientV7: ElasticsearchConnectionSettings =
    ElasticsearchConnectionSettings("http://localhost:9202")

  override def afterAll(): Unit = {
    val deleteRequestV5 = HttpRequest(HttpMethods.DELETE)
      .withUri(Uri(clientV5.baseUrl).withPath(Path("/_all")))
    http.singleRequest(deleteRequestV5).futureValue

    val deleteRequestV7 = HttpRequest(HttpMethods.DELETE)
      .withUri(Uri(clientV7.baseUrl).withPath(Path("/_all")))
    http.singleRequest(deleteRequestV7).futureValue

    TestKit.shutdownActorSystem(system)
  }

  "Connector with ApiVersion 5 running against Elasticsearch v6.8.0" should {
    behave.like(elasticsearchConnector(ApiVersion.V5, clientV5))
  }

  "Connector with ApiVersion 7 running against Elasticsearch v7.6.0" should {
    behave.like(elasticsearchConnector(ApiVersion.V7, clientV7))
  }

}
