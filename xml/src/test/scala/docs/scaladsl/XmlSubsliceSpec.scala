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
import pekko.stream.connectors.xml._
import pekko.stream.connectors.xml.scaladsl.XmlParsing
import pekko.stream.scaladsl.{ Flow, Keep, Sink, Source }
import pekko.util.ByteString
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class XmlSubsliceSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with LogCapturing {
  implicit val system: ActorSystem = ActorSystem("Test")

  // #subslice
  val parse = Flow[String]
    .map(ByteString(_))
    .via(XmlParsing.parser)
    .via(XmlParsing.subslice("doc" :: "elem" :: "item" :: Nil))
    .toMat(Sink.seq)(Keep.right)
  // #subslice

  "XML subslice support" must {

    "properly extract subslices of events" in {
      val doc =
        """
          |<doc>
          |  <elem>
          |    <item>i1</item>
          |    <item>i2</item>
          |    <item>i3</item>
          |  </elem>
          |</doc>
        """.stripMargin

      val result = Await.result(Source.single(doc).runWith(parse), 3.seconds)
      result should ===(
        List(
          Characters("i1"),
          Characters("i2"),
          Characters("i3")))
    }

    "properly extract subslices of nested events" in {

      // #subslice-usage
      val doc =
        """
          |<doc>
          |  <elem>
          |    <item>i1</item>
          |    <item><sub>i2</sub></item>
          |    <item>i3</item>
          |  </elem>
          |</doc>
        """.stripMargin
      val resultFuture = Source.single(doc).runWith(parse)
      // #subslice-usage

      val result = Await.result(resultFuture, 3.seconds)
      result should ===(
        List(
          Characters("i1"),
          StartElement("sub", Map.empty[String, String]),
          Characters("i2"),
          EndElement("sub"),
          Characters("i3")))
    }

    "properly ignore matches not deep enough" in {
      val doc =
        """
          |<doc>
          |  <elem>
          |     I am lonely here :(
          |  </elem>
          |</doc>
        """.stripMargin

      val result = Await.result(Source.single(doc).runWith(parse), 3.seconds)
      result should ===(Nil)
    }

    "properly ignore partial matches" in {
      val doc =
        """
          |<doc>
          |  <elem>
          |     <notanitem>ignore me</notanitem>
          |     <notanitem>ignore me</notanitem>
          |     <foo>ignore me</foo>
          |  </elem>
          |  <bar></bar>
          |</doc>
        """.stripMargin

      val result = Await.result(Source.single(doc).runWith(parse), 3.seconds)
      result should ===(Nil)
    }

    "properly filter from the combination of the above" in {
      val doc =
        """
          |<doc>
          |  <elem>
          |    <notanitem>ignore me</notanitem>
          |    <notanitem>ignore me</notanitem>
          |    <foo>ignore me</foo>
          |    <item>i1</item>
          |    <item><sub>i2</sub></item>
          |    <item>i3</item>
          |  </elem>
          |  <elem>
          |    not me please
          |  </elem>
          |  <elem><item>i4</item></elem>
          |</doc>
        """.stripMargin

      val result = Await.result(Source.single(doc).runWith(parse), 3.seconds)
      result should ===(
        List(
          Characters("i1"),
          StartElement("sub", Map.empty[String, String]),
          Characters("i2"),
          EndElement("sub"),
          Characters("i3"),
          Characters("i4")))
    }

  }

  override protected def afterAll(): Unit = system.terminate()
}
