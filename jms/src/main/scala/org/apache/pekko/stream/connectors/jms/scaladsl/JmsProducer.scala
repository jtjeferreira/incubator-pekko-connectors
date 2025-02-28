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

package org.apache.pekko.stream.connectors.jms.scaladsl

import org.apache.pekko
import pekko.stream.connectors.jms._
import pekko.stream.connectors.jms.impl.{ JmsProducerMatValue, JmsProducerStage }
import pekko.stream.scaladsl.{ Flow, Keep, Sink, Source }
import pekko.util.ByteString
import pekko.{ Done, NotUsed }

import scala.concurrent.Future

/**
 * Factory methods to create JMS producers.
 */
object JmsProducer {

  /**
   * Create a flow to send [[pekko.stream.connectors.jms.JmsMessage JmsMessage]] sub-classes to
   * a JMS broker.
   */
  def flow[T <: JmsMessage](settings: JmsProducerSettings): Flow[T, T, JmsProducerStatus] =
    settings.destination match {
      case None => throw new IllegalArgumentException(noProducerDestination(settings))
      case Some(destination) =>
        Flow[T]
          .viaMat(Flow.fromGraph(new JmsProducerStage[T, NotUsed](settings, destination)))(Keep.right)
          .mapMaterializedValue(toProducerStatus)
    }

  /**
   * Create a flow to send [[pekko.stream.connectors.jms.JmsEnvelope JmsEnvelope]] sub-classes to
   * a JMS broker to support pass-through of data.
   */
  def flexiFlow[PassThrough](
      settings: JmsProducerSettings): Flow[JmsEnvelope[PassThrough], JmsEnvelope[PassThrough], JmsProducerStatus] =
    settings.destination match {
      case None => throw new IllegalArgumentException(noProducerDestination(settings))
      case Some(destination) =>
        Flow
          .fromGraph(new JmsProducerStage[JmsEnvelope[PassThrough], PassThrough](settings, destination))
          .mapMaterializedValue(toProducerStatus)
    }

  /**
   * Create a sink to send [[pekko.stream.connectors.jms.JmsMessage JmsMessage]] sub-classes to
   * a JMS broker.
   */
  def sink(settings: JmsProducerSettings): Sink[JmsMessage, Future[Done]] =
    flow(settings).toMat(Sink.ignore)(Keep.right)

  /**
   * Create a sink to send Strings as text messages to a JMS broker.
   */
  def textSink(settings: JmsProducerSettings): Sink[String, Future[Done]] =
    Flow.fromFunction((s: String) => JmsTextMessage(s)).via(flow(settings)).toMat(Sink.ignore)(Keep.right)

  /**
   * Create a sink to send byte arrays to a JMS broker.
   */
  def bytesSink(settings: JmsProducerSettings): Sink[Array[Byte], Future[Done]] =
    Flow.fromFunction((s: Array[Byte]) => JmsByteMessage(s)).via(flow(settings)).toMat(Sink.ignore)(Keep.right)

  /**
   * Create a sink to send [[pekko.util.ByteString ByteString]]s to a JMS broker.
   */
  def byteStringSink(settings: JmsProducerSettings): Sink[ByteString, Future[Done]] =
    Flow.fromFunction((s: ByteString) => JmsByteStringMessage(s)).via(flow(settings)).toMat(Sink.ignore)(Keep.right)

  /**
   * Create a sink to send map structures to a JMS broker.
   */
  def mapSink(settings: JmsProducerSettings): Sink[Map[String, Any], Future[Done]] =
    Flow.fromFunction((s: Map[String, Any]) => JmsMapMessage(s)).via(flow(settings)).toMat(Sink.ignore)(Keep.right)

  /**
   * Create a sink to send serialized objects to a JMS broker.
   */
  def objectSink(settings: JmsProducerSettings): Sink[java.io.Serializable, Future[Done]] =
    Flow
      .fromFunction((s: java.io.Serializable) => JmsObjectMessage(s))
      .via(flow(settings))
      .toMat(Sink.ignore)(Keep.right)

  private def toProducerStatus(internal: JmsProducerMatValue) = new JmsProducerStatus {

    override def connectorState: Source[JmsConnectorState, NotUsed] = transformConnectorState(internal.connected)
  }

  private def noProducerDestination(settings: JmsProducerSettings) =
    s"""Unable to create JmsProducer: it needs a default destination to send messages to, but none was provided in
      |$settings
      |Please use withQueue, withTopic or withDestination to specify a destination.""".stripMargin
}
