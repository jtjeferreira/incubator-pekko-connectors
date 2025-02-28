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

package org.apache.pekko.stream.connectors.s3.impl.auth

import java.nio.charset.StandardCharsets._
import java.nio.file.{ Files, Path }
import java.security.{ DigestInputStream, MessageDigest }

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.connectors.testkit.scaladsl.LogCapturing
import pekko.stream.scaladsl.{ Sink, Source, StreamConverters }
import pekko.testkit.TestKit
import pekko.util.ByteString
import com.google.common.jimfs.{ Configuration, Jimfs }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }

class StreamUtilsSpec(_system: ActorSystem)
    extends TestKit(_system)
    with AnyFlatSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with LogCapturing {
  def this() = this(ActorSystem("StreamUtilsSpec"))

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(30, Millis))

  override protected def afterAll(): Unit = {
    fs.close()
    TestKit.shutdownActorSystem(system)
  }

  val fs = Jimfs.newFileSystem("FileSourceSpec", Configuration.unix())

  val TestText = {
    ("a" * 1000) +
    ("b" * 1000) +
    ("c" * 1000) +
    ("d" * 1000) +
    ("e" * 1000) +
    ("f" * 1000)
  }

  val bigFile: Path = {
    val f = Files.createTempFile(fs.getPath("/"), "file-source-spec", ".tmp")
    val writer = Files.newBufferedWriter(f, UTF_8)
    (1 to 3500).foreach(_ => writer.append(TestText))
    writer.close()
    f
  }

  "digest" should "calculate the digest of a short string" in {
    val bytes = "abcdefghijklmnopqrstuvwxyz".getBytes()
    val flow = Source.single(ByteString(bytes)).via(digest()).runWith(Sink.head)

    val testDigest = MessageDigest.getInstance("SHA-256").digest(bytes)
    whenReady(flow) { result =>
      result should contain theSameElementsInOrderAs testDigest
    }
  }

  it should "calculate the digest of a file" in {
    val input = StreamConverters.fromInputStream(() => Files.newInputStream(bigFile))
    val flow = input.via(digest()).runWith(Sink.head)

    val testDigest = MessageDigest.getInstance("SHA-256")
    val dis = new DigestInputStream(Files.newInputStream(bigFile), testDigest)

    val buffer = new Array[Byte](1024)

    var bytesRead: Int = dis.read(buffer)
    while (bytesRead > -1) {
      bytesRead = dis.read(buffer)
    }

    whenReady(flow) { result =>
      result should contain theSameElementsInOrderAs dis.getMessageDigest.digest()
    }
  }
}
