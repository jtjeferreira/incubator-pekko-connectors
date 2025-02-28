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
import pekko.stream.connectors.hdfs._
import pekko.stream.connectors.hdfs.scaladsl.{ HdfsFlow, HdfsSource }
import pekko.stream.connectors.hdfs.util.ScalaTestUtils._
import pekko.stream.connectors.testkit.scaladsl.LogCapturing
import pekko.stream.scaladsl.{ Sink, Source }
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{ FileSystem, Path }
import org.apache.hadoop.hdfs.MiniDFSCluster
import org.apache.hadoop.io.Text
import org.apache.hadoop.io.compress.DefaultCodec
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContextExecutor, Future }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class HdfsReaderSpec
    extends AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with LogCapturing {

  private var hdfsCluster: MiniDFSCluster = _
  private val destination = "/tmp/alpakka/"

  implicit val system: ActorSystem = ActorSystem()

  val conf = new Configuration()
  conf.set("fs.default.name", "hdfs://localhost:54310")

  val fs: FileSystem = FileSystem.get(conf)
  val settings = HdfsWritingSettings()

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  "HdfsSource" should {
    "read data file" in {
      val flow = HdfsFlow.data(
        fs,
        SyncStrategy.count(500),
        RotationStrategy.size(0.5, FileUnit.KB),
        HdfsWritingSettings())

      val content = generateFakeContent(1, FileUnit.KB.byteCount)

      val resF1 = Source
        .fromIterator(() => content.iterator)
        .map(HdfsWriteMessage(_))
        .via(flow)
        .runWith(Sink.seq)

      val resF = resF1.flatMap { logs =>
        Future
          .sequence(
            logs.map { log =>
              val path = new Path("/tmp/alpakka", log.path)
              // #define-data-source
              val source = HdfsSource.data(fs, path)
              // #define-data-source
              source.runWith(Sink.seq)
            })
          .map(_.flatten)
      }

      val result = Await.result(resF, Duration.Inf)
      content.flatMap(_.utf8String) shouldBe result.flatMap(_.utf8String)
    }

    "read compressed data file" in {
      val codec = new DefaultCodec()
      codec.setConf(fs.getConf)

      val flow = HdfsFlow.compressed(
        fs,
        SyncStrategy.count(1),
        RotationStrategy.size(0.1, FileUnit.MB),
        codec,
        settings)

      val content = generateFakeContentWithPartitions(1, FileUnit.MB.byteCount, 30)

      val resF1 = Source
        .fromIterator(() => content.iterator)
        .map(HdfsWriteMessage(_))
        .via(flow)
        .runWith(Sink.seq)

      val resF = resF1.flatMap { logs =>
        Future
          .sequence(
            logs.map { log =>
              val path = new Path("/tmp/alpakka", log.path)
              // #define-compressed-source
              val source = HdfsSource.compressed(fs, path, codec)
              // #define-compressed-source
              source.runWith(Sink.seq)
            })
          .map(_.flatten)
      }

      val result = Await.result(resF, Duration.Inf)
      content.flatMap(_.utf8String) shouldBe result.flatMap(_.utf8String)
    }

    "read sequence file" in {
      val flow = HdfsFlow.sequence(
        fs,
        SyncStrategy.none,
        RotationStrategy.size(1, FileUnit.MB),
        settings,
        classOf[Text],
        classOf[Text])

      val content = generateFakeContentForSequence(0.5, FileUnit.MB.byteCount)

      val resF1 = Source
        .fromIterator(() => content.iterator)
        .map(HdfsWriteMessage(_))
        .via(flow)
        .runWith(Sink.seq)

      val resF = resF1.flatMap { logs =>
        Future
          .sequence(
            logs.map { log =>
              val path = new Path("/tmp/alpakka", log.path)
              // #define-sequence-source
              val source = HdfsSource.sequence(fs, path, classOf[Text], classOf[Text])
              // #define-sequence-source
              source.runWith(Sink.seq)
            })
          .map(_.flatten)
      }

      val result = Await.result(resF, Duration.Inf)
      content shouldBe result
    }
  }

  override protected def beforeAll(): Unit =
    hdfsCluster = setupCluster()

  override protected def afterAll(): Unit = {
    fs.close()
    hdfsCluster.shutdown()
  }

  override protected def afterEach(): Unit = {
    fs.delete(new Path(destination), true)
    fs.delete(settings.pathGenerator(0, 0).getParent, true)
    ()
  }
}
