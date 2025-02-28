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

package org.apache.pekko.stream.connectors.kinesis.impl

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.annotation.InternalApi
import pekko.dispatch.ExecutionContexts.parasitic
import pekko.stream.connectors.kinesis.{ KinesisErrors => Errors, ShardSettings }
import pekko.stream.stage.GraphStageLogic.StageActor
import pekko.stream.stage._
import pekko.stream.{ Attributes, Outlet, SourceShape }
import pekko.util.ccompat.JavaConverters._
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model._

import scala.collection.mutable
import scala.util.{ Failure, Success, Try }

import scala.compat.java8.FutureConverters._

/**
 * Internal API
 */
@InternalApi
private[kinesis] object KinesisSourceStage {

  private[kinesis] final case class GetShardIteratorSuccess(result: GetShardIteratorResponse)

  private[kinesis] final case class GetShardIteratorFailure(ex: Throwable)

  private[kinesis] final case class GetRecordsSuccess(records: GetRecordsResponse)

  private[kinesis] final case class GetRecordsFailure(ex: Throwable)

  private[kinesis] final case object Pump

  private[kinesis] final case object GetRecords

}

/**
 * Internal API
 */
@InternalApi
private[kinesis] class KinesisSourceStage(shardSettings: ShardSettings, amazonKinesisAsync: => KinesisAsyncClient)
    extends GraphStage[SourceShape[Record]] {

  import KinesisSourceStage._

  private val out = Outlet[Record]("Records")

  override def shape: SourceShape[Record] = new SourceShape[Record](out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with StageLogging with OutHandler {

      setHandler(out, this)

      import shardSettings._

      private[this] var currentShardIterator: String = _
      private[this] val buffer = mutable.Queue.empty[Record]
      private[this] var self: StageActor = _

      override def preStart(): Unit = {
        self = getStageActor(awaitingShardIterator)
        requestShardIterator()
      }

      override def onPull(): Unit = self.ref ! Pump

      private def awaitingShardIterator(in: (ActorRef, Any)): Unit = in match {
        case (_, GetShardIteratorSuccess(result)) =>
          currentShardIterator = result.shardIterator
          self.become(awaitingRecords)
          requestRecords()

        case (_, GetShardIteratorFailure(ex)) =>
          val error = new Errors.GetShardIteratorError(shardId, ex)
          log.error(ex, error.getMessage)
          failStage(error)

        case (_, Pump) =>
        case (_, msg) =>
          throw new IllegalArgumentException(s"unexpected message $msg in state `ready`")
      }

      private def awaitingRecords(in: (ActorRef, Any)): Unit = in match {
        case (_, GetRecordsSuccess(result)) =>
          val records = result.records.asScala
          if (result.nextShardIterator == null) {
            log.info("Shard {} returned a null iterator and will now complete.", shardId)
            completeStage()
          } else {
            currentShardIterator = result.nextShardIterator
          }
          if (records.nonEmpty) {
            records.foreach(buffer.enqueue(_))
            self.become(ready)
            self.ref ! Pump
          } else {
            scheduleOnce(GetRecords, refreshInterval)
          }

        case (_, GetRecordsFailure(ex)) =>
          val error = new Errors.GetRecordsError(shardId, ex)
          log.error(ex, error.getMessage)
          failStage(error)

        case (_, Pump) =>
        case (_, msg) =>
          throw new IllegalArgumentException(s"unexpected message $msg in state `ready`")
      }

      private def ready(in: (ActorRef, Any)): Unit = in match {
        case (_, Pump) =>
          if (isAvailable(shape.out)) {
            push(shape.out, buffer.dequeue())
            self.ref ! Pump
          }
          if (buffer.isEmpty) {
            self.become(awaitingRecords)
            requestRecords()
          }

        case (_, msg) =>
          throw new IllegalArgumentException(s"unexpected message $msg in state `ready`")
      }

      override protected def onTimer(timerKey: Any): Unit = timerKey match {
        case GetRecords => requestRecords()
        case other =>
          log.warning("unexpected timer [{}]", other)
      }

      private[this] val handleGetRecords: Try[GetRecordsResponse] => Unit = {
        case Failure(exception) => self.ref ! GetRecordsFailure(exception)
        case Success(result)    => self.ref ! GetRecordsSuccess(result)
      }

      private[this] def requestRecords(): Unit =
        amazonKinesisAsync
          .getRecords(
            GetRecordsRequest.builder().limit(limit).shardIterator(currentShardIterator).build())
          .toScala
          .onComplete(handleGetRecords)(parasitic)

      private[this] def requestShardIterator(): Unit = {
        val request = Function
          .chain[GetShardIteratorRequest.Builder](
            Seq(
              r => startingSequenceNumber.fold(r)(r.startingSequenceNumber),
              r => atTimestamp.fold(r)(instant => r.timestamp(instant))))(
            GetShardIteratorRequest
              .builder()
              .streamName(streamName)
              .shardId(shardId)
              .shardIteratorType(shardIteratorType))
          .build()

        val handleGetShardIterator: Try[GetShardIteratorResponse] => Unit = {
          case Success(result)    => self.ref ! GetShardIteratorSuccess(result)
          case Failure(exception) => self.ref ! GetShardIteratorFailure(exception)
        }

        amazonKinesisAsync
          .getShardIterator(request)
          .toScala
          .onComplete(handleGetShardIterator)(parasitic)
      }

    }

}
