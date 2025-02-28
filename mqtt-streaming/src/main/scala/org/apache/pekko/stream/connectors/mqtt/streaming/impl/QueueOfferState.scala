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

package org.apache.pekko.stream.connectors.mqtt.streaming.impl

import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.stream.QueueOfferResult
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

private[mqtt] object QueueOfferState {

  /**
   * A marker trait that holds a result for SourceQueue#offer
   */
  trait QueueOfferCompleted {
    def result: Either[Throwable, QueueOfferResult]
  }

  /**
   * A behavior that stashes messages until a response to the SourceQueue#offer
   * method is received.
   *
   * This is to be used only with SourceQueues that use backpressure.
   */
  def waitForQueueOfferCompleted[T](result: Future[QueueOfferResult],
      f: Try[QueueOfferResult] => T with QueueOfferCompleted,
      behavior: Behavior[T],
      stash: Seq[T]): Behavior[T] = Behaviors.setup { context =>
    import context.executionContext

    val s = stash.map(BehaviorRunner.StoredMessage.apply)

    if (result.isCompleted) {
      // optimize for a common case where we were immediately able to enqueue

      result.value.get match {
        case Success(QueueOfferResult.Enqueued) =>
          BehaviorRunner.run(behavior, context, s)

        case Success(other) =>
          throw new IllegalStateException(s"Failed to offer to queue: $other")

        case Failure(failure) =>
          throw failure
      }
    } else {
      result.onComplete { r =>
        context.self.tell(f(r))
      }

      behaviorImpl(behavior, s)
    }
  }

  private def behaviorImpl[T](behavior: Behavior[T], stash: Seq[BehaviorRunner.Interpretable[T]]): Behavior[T] =
    Behaviors
      .receive[T] {
        case (context, completed: QueueOfferCompleted) =>
          completed.result match {
            case Right(QueueOfferResult.Enqueued) =>
              BehaviorRunner.run(behavior, context, stash)

            case Right(other) =>
              throw new IllegalStateException(s"Failed to offer to queue: $other")

            case Left(failure) =>
              throw failure
          }

        case (_, other) =>
          behaviorImpl(behavior, stash :+ BehaviorRunner.StoredMessage(other))

      }
      .receiveSignal {
        case (_, signal) =>
          behaviorImpl(behavior, stash :+ BehaviorRunner.StoredSignal(signal))
      }
}
