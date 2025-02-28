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

package org.apache.pekko.stream.connectors.json.impl

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream._
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.util.ByteString
import org.jsfr.json.exception.JsonSurfingException
import org.jsfr.json.path.JsonPath
import org.jsfr.json.{ JsonPathListener, JsonSurferJackson, ParsingContext }

import scala.collection.immutable.Queue

/**
 * Internal API
 */
@InternalApi
private[pekko] final class JsonStreamReader(path: JsonPath) extends GraphStage[FlowShape[ByteString, ByteString]] {

  private val in = Inlet[ByteString]("Json.in")
  private val out = Outlet[ByteString]("Json.out")
  override val shape = FlowShape(in, out)

  override def initialAttributes: Attributes = Attributes.name(s"jsonReader($path)")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private val in = shape.in
      private val out = shape.out
      setHandlers(in, out, this)

      private var buffer = Queue.empty[ByteString]

      private val surfer = JsonSurferJackson.INSTANCE
      private val config = surfer.configBuilder
        .bind(path,
          new JsonPathListener {
            override def onValue(value: Any, context: ParsingContext): Unit =
              buffer = buffer.enqueue(ByteString(value.toString))
          })
        .build
      private val parser = surfer.createNonBlockingParser(config)

      override def onPull(): Unit = tryPull(in)

      override def onPush(): Unit = {
        val input = grab(in)

        val array = input.toArray

        // Feeding the parser will fail in situations like invalid JSON being provided.
        try {
          parser.feed(array, 0, array.length)
        } catch {
          case e: JsonSurfingException => failStage(e)
        }

        if (buffer.nonEmpty) {
          emitMultiple(out, buffer)
          buffer = Queue.empty[ByteString]
        } else {
          // Iff the buffer is empty, we haven't consumed any values yet
          // and thus we still need to fulfill downstream need.
          tryPull(in)
        }
      }

      override def onUpstreamFinish(): Unit =
        // Ending the parser will fail when the JSON structure is incomplete.
        try {
          parser.endOfInput()
          completeStage()
        } catch {
          case e: JsonSurfingException => failStage(e)
        }
    }
}
