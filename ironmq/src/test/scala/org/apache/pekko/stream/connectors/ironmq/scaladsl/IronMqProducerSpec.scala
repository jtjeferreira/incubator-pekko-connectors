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

package org.apache.pekko.stream.connectors.ironmq.scaladsl

import org.apache.pekko
import pekko.dispatch.ExecutionContexts
import pekko.stream.connectors.ironmq.{ IronMqSettings, IronMqSpec, PushMessage }
import pekko.stream.scaladsl.{ Flow, Sink, Source }
import pekko.{ Done, NotUsed }
import pekko.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped

import scala.concurrent.{ ExecutionContext, Future }

class IronMqProducerSpec extends IronMqSpec {

  import IronMqProducer._

  val messages: Source[PushMessage, NotUsed] =
    Source.fromIterator(() => Iterator.from(0)).map(i => PushMessage(s"test-$i"))
  implicit val ec: ExecutionContext = ExecutionContexts.global()

  "producerSink" should {
    "publish messages on IronMq" in assertAllStagesStopped {

      val queue = givenQueue()
      val settings = IronMqSettings()

      val expectedMessagesBodies = List("test-1", "test-2")

      val done = Source(expectedMessagesBodies).map(PushMessage(_)).runWith(sink(queue, settings))

      whenReady(done) { _ =>
        ironMqClient
          .pullMessages(queue, 20)
          .futureValue
          .map(_.body)
          .toSeq should contain theSameElementsInOrderAs expectedMessagesBodies

      }

    }
  }

  "producerFlow" should {
    "return published messages' ids" in assertAllStagesStopped {

      val queue = givenQueue()
      val settings = IronMqSettings()

      val messageIds = messages.take(10).via(flow(queue, settings)).runWith(Sink.seq).futureValue

      ironMqClient
        .pullMessages(queue, 10)
        .futureValue
        .map(_.messageId)
        .toSeq should contain theSameElementsInOrderAs messageIds

    }
  }

  "atLeastOnceProducerFlow" should {
    "commit the committables" in assertAllStagesStopped {

      val queue = givenQueue()
      val settings = IronMqSettings()

      val committables = List(
        new MockCommittable,
        new MockCommittable,
        new MockCommittable)

      whenReady(
        messages
          .zip(Source(committables))
          .via(atLeastOnceFlow(queue, settings, Flow[Committable].mapAsync(1)(_.commit())))
          .runWith(Sink.ignore)) { _ =>
        committables.forall(_.committed) shouldBe true
      }
    }
  }

}

class MockCommittable extends Committable {

  var committed: Boolean = false

  override def commit(): Future[Done] = {
    committed = true
    Future.successful(Done)
  }
}
