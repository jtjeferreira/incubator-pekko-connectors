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
import pekko.{ Done, NotUsed }
import pekko.stream.FlowShape
import pekko.stream.connectors.ironmq._
import pekko.stream.connectors.ironmq.impl.IronMqPushStage
import pekko.stream.scaladsl._

import scala.concurrent.Future

object IronMqProducer {

  /**
   * This is plain producer [[pekko.stream.scaladsl.Flow Flow]] that consume [[PushMessage]] and emit [[Message.Id]] for each produced message.
   */
  def flow(queueName: String, settings: IronMqSettings): Flow[PushMessage, Message.Id, NotUsed] =
    // The parallelism MUST be 1 to guarantee the order of the messages
    Flow.fromGraph(new IronMqPushStage(queueName, settings)).mapAsync(1)(identity).mapConcat(_.ids)

  /**
   * A plain producer [[pekko.stream.scaladsl.Sink Sink]] that consume [[PushMessage]] and push them on IronMq.
   */
  def sink(queueName: String, settings: IronMqSettings): Sink[PushMessage, Future[Done]] =
    flow(queueName, settings).toMat(Sink.ignore)(Keep.right)

  /**
   * A [[Committable]] aware producer [[pekko.stream.scaladsl.Flow Flow]] that consume [[(PushMessage, Committable)]], push messages on IronMq and
   * commit the associated [[Committable]].
   */
  def atLeastOnceFlow(queueName: String,
      settings: IronMqSettings): Flow[(PushMessage, Committable), Message.Id, NotUsed] =
    // TODO Not sure about parallelism, as the commits should not be in-order, maybe add it as parameter?
    atLeastOnceFlow(queueName, settings, Flow[Committable].mapAsync(1)(_.commit())).map(_._1)

  /**
   * A [[Committable]] aware producer [[pekko.stream.scaladsl.Sink Sink]] that consume [[(PushMessage, Committable)]] push messages on IronMq and
   * commit the associated [[Committable]].
   */
  def atLeastOnceSink(queueName: String, settings: IronMqSettings): Sink[(PushMessage, Committable), NotUsed] =
    atLeastOnceFlow(queueName, settings).to(Sink.ignore)

  /**
   * A more generic committable aware producer [[pekko.stream.scaladsl.Flow Flow]] that can be used for other committable source, like Kafka. The
   * user is responsible to supply the committing flow. The result of the commit is emitted as well as the materialized
   * value of the committing flow.
   */
  def atLeastOnceFlow[ToCommit, CommitResult, CommitMat](
      queueName: String,
      settings: IronMqSettings,
      commitFlow: Flow[ToCommit, CommitResult, CommitMat])
      : Flow[(PushMessage, ToCommit), (Message.Id, CommitResult), CommitMat] = {

    // This graph is used to pass the ToCommit through the producer. It assume a 1-to-1 semantic on the producer
    val producerGraph = GraphDSL.createGraph(flow(queueName, settings)) { implicit builder => producer =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[(PushMessage, ToCommit)](2))
      val zip = builder.add(Zip[Message.Id, ToCommit]())
      val extractPushMessage = builder.add(Flow[(PushMessage, ToCommit)].map(_._1))
      val extractCommittable = builder.add(Flow[(PushMessage, ToCommit)].map(_._2))

      broadcast ~> extractPushMessage ~> producer ~> zip.in0
      broadcast ~> extractCommittable ~> zip.in1

      FlowShape(broadcast.in, zip.out)
    }

    // This graph is used to pass the ToCommit through the commitFlow. Again it assume the commitFlow to have 1-to-1 semantic
    Flow.fromGraph(GraphDSL.createGraph(producerGraph, commitFlow)(Keep.right) {
      implicit builder => (producer, committer) =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[(Message.Id, ToCommit)](2))
        val extractMessageId = builder.add(Flow[(Message.Id, ToCommit)].map(_._1))
        val extractCommittable = builder.add(Flow[(Message.Id, ToCommit)].map(_._2))
        val zip = builder.add(Zip[Message.Id, CommitResult]())

        producer  ~> broadcast          ~> extractMessageId ~> zip.in0
        broadcast ~> extractCommittable ~> committer        ~> zip.in1

        FlowShape(producer.in, zip.out)
    })
  }

  /**
   * A more generic committable aware producer [[pekko.stream.scaladsl.Sink Sink]] that can be used for other committable source, like Kafka. The
   * user is responsible to supply the committing flow. The materialized value of the committing flow is returned.
   */
  def atLeastOnceSink[ToCommit, CommitResult, CommitMat](
      queueName: String,
      settings: IronMqSettings,
      commitFlow: Flow[ToCommit, CommitResult, CommitMat]): Sink[(PushMessage, ToCommit), CommitMat] =
    atLeastOnceFlow(queueName, settings, commitFlow).to(Sink.ignore)

}
