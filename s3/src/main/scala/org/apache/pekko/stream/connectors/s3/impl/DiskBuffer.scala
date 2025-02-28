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

package org.apache.pekko.stream.connectors.s3.impl

import java.io.{ File, FileOutputStream }
import java.nio.BufferOverflowException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko
import pekko.NotUsed
import pekko.dispatch.ExecutionContexts
import pekko.stream.ActorAttributes
import pekko.stream.Attributes
import pekko.stream.FlowShape
import pekko.stream.Inlet
import pekko.stream.Outlet
import pekko.stream.scaladsl.FileIO
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler
import pekko.util.ByteString
import java.nio.file.Path

import pekko.annotation.InternalApi

/**
 * Internal Api
 *
 * Buffers the complete incoming stream into a file, which can then be read several times afterwards.
 * ``
 * The stage waits for the incoming stream to complete. After that, it emits a single Chunk item on its output. The Chunk
 * contains a bytestream source that can be materialized multiple times, and the total size of the file.
 *
 * @param maxMaterializations Number of expected materializations for the completed chunk. After this, the temp file is deleted.
 * @param maxSize Maximum size on disk to buffer
 */
@InternalApi private[impl] final class DiskBuffer(maxMaterializations: Int, maxSize: Int, tempPath: Option[Path])
    extends GraphStage[FlowShape[ByteString, Chunk]] {
  require(maxMaterializations > 0, "maxMaterializations should be at least 1")
  require(maxSize > 0, "maximumSize should be at least 1")

  val in = Inlet[ByteString]("DiskBuffer.in")
  val out = Outlet[Chunk]("DiskBuffer.out")
  override val shape = FlowShape.of(in, out)

  override def initialAttributes =
    super.initialAttributes and Attributes.name("DiskBuffer") and ActorAttributes.IODispatcher

  override def createLogic(attr: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler with InHandler {
      val path: File = tempPath
        .map(dir => Files.createTempFile(dir, "s3-buffer-", ".bin"))
        .getOrElse(Files.createTempFile("s3-buffer-", ".bin"))
        .toFile
      path.deleteOnExit()
      var length = 0
      val pathOut = new FileOutputStream(path)

      override def onPull(): Unit = if (isClosed(in)) emit() else pull(in)

      override def onPush(): Unit = {
        val elem = grab(in)
        length += elem.size
        if (length > maxSize) {
          throw new BufferOverflowException()
        }

        pathOut.write(elem.toArray)
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        if (isAvailable(out)) emit()
        completeStage()
      }

      override def postStop(): Unit =
        // close stream even if we didn't emit
        try {
          pathOut.close()
        } catch { case x: Throwable => () }

      private def emit(): Unit = {
        pathOut.close()

        val deleteCounter = new AtomicInteger(maxMaterializations)
        val src = FileIO.fromPath(path.toPath, 65536).mapMaterializedValue { f =>
          if (deleteCounter.decrementAndGet() <= 0)
            f.onComplete { _ =>
              path.delete()

            }(ExecutionContexts.parasitic)
          NotUsed
        }
        emit(out, DiskChunk(src, length), () => completeStage())
      }
      setHandlers(in, out, this)
    }
}
