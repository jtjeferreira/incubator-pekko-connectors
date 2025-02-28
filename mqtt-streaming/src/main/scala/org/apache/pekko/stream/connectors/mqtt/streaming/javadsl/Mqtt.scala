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

package org.apache.pekko.stream.connectors.mqtt.streaming
package javadsl

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.connectors.mqtt.streaming.MqttCodec.DecodeError
import pekko.stream.javadsl.BidiFlow
import pekko.stream.scaladsl.{ BidiFlow => ScalaBidiFlow }
import pekko.util.ByteString

object Mqtt {

  /**
   * Create a bidirectional flow that maintains client session state with an MQTT endpoint.
   * The bidirectional flow can be joined with an endpoint flow that receives
   * [[ByteString]] payloads and independently produces [[ByteString]] payloads e.g.
   * an MQTT server.
   *
   * @param session the client session to use
   * @param connectionId a identifier to distinguish the client connection so that the session
   *                     can route the incoming requests
   * @return the bidirectional flow
   */
  def clientSessionFlow[A](
      session: MqttClientSession,
      connectionId: ByteString): BidiFlow[Command[A], ByteString, ByteString, DecodeErrorOrEvent[A], NotUsed] =
    inputOutputConverter
      .atop(scaladsl.Mqtt.clientSessionFlow[A](session.underlying, connectionId))
      .asJava

  /**
   * Create a bidirectional flow that maintains server session state with an MQTT endpoint.
   * The bidirectional flow can be joined with an endpoint flow that receives
   * [[ByteString]] payloads and independently produces [[ByteString]] payloads e.g.
   * an MQTT server.
   *
   * @param session the server session to use
   * @param connectionId a identifier to distinguish the client connection so that the session
   *                     can route the incoming requests
   * @return the bidirectional flow
   */
  def serverSessionFlow[A](
      session: MqttServerSession,
      connectionId: ByteString): BidiFlow[Command[A], ByteString, ByteString, DecodeErrorOrEvent[A], NotUsed] =
    inputOutputConverter
      .atop(scaladsl.Mqtt.serverSessionFlow[A](session.underlying, connectionId))
      .asJava

  /*
   * Converts Java inputs to Scala, and vice-versa.
   */
  private def inputOutputConverter[A] =
    ScalaBidiFlow
      .fromFunctions[Command[A], Command[A], Either[DecodeError, Event[A]], DecodeErrorOrEvent[A]](
        identity,
        DecodeErrorOrEvent.apply)
}
