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

package org.apache.pekko.stream.connectors.hdfs.javadsl

import org.apache.pekko
import pekko.NotUsed
import pekko.japi.Pair
import pekko.stream.connectors.hdfs._
import pekko.stream.connectors.hdfs.scaladsl.{ HdfsFlow => ScalaHdfsFlow }
import pekko.stream.javadsl
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.io.SequenceFile.CompressionType
import org.apache.hadoop.io.Writable
import org.apache.hadoop.io.compress.CompressionCodec

object HdfsFlow {

  /**
   * Java API: creates a Flow for [[org.apache.hadoop.fs.FSDataOutputStream]]
   *
   * @param fs Hadoop file system
   * @param syncStrategy sync strategy
   * @param rotationStrategy rotation strategy
   * @param settings hdfs writing settings
   */
  def data(
      fs: FileSystem,
      syncStrategy: SyncStrategy,
      rotationStrategy: RotationStrategy,
      settings: HdfsWritingSettings): javadsl.Flow[HdfsWriteMessage[ByteString, NotUsed], RotationMessage, NotUsed] =
    ScalaHdfsFlow.data(fs, syncStrategy, rotationStrategy, settings).asJava

  /**
   * Java API: creates a flow for [[org.apache.hadoop.fs.FSDataOutputStream]]
   * with `passThrough` of type `C`
   *
   * @param fs Hadoop file system
   * @param syncStrategy sync strategy
   * @param rotationStrategy rotation strategy
   * @param settings hdfs writing settings
   */
  def dataWithPassThrough[P](
      fs: FileSystem,
      syncStrategy: SyncStrategy,
      rotationStrategy: RotationStrategy,
      settings: HdfsWritingSettings): javadsl.Flow[HdfsWriteMessage[ByteString, P], OutgoingMessage[P], NotUsed] =
    ScalaHdfsFlow
      .dataWithPassThrough[P](
        fs,
        syncStrategy,
        rotationStrategy,
        settings)
      .asJava

  /**
   * Java API: creates a flow for [[org.apache.hadoop.io.compress.CompressionOutputStream]]
   *
   * @param fs Hadoop file system
   * @param syncStrategy sync strategy
   * @param rotationStrategy rotation strategy
   * @param compressionCodec a streaming compression/decompression pair
   * @param settings hdfs writing settings
   */
  def compressed(
      fs: FileSystem,
      syncStrategy: SyncStrategy,
      rotationStrategy: RotationStrategy,
      compressionCodec: CompressionCodec,
      settings: HdfsWritingSettings): javadsl.Flow[HdfsWriteMessage[ByteString, NotUsed], RotationMessage, NotUsed] =
    ScalaHdfsFlow.compressed(fs, syncStrategy, rotationStrategy, compressionCodec, settings).asJava

  /**
   * Java API: creates a flow for [[org.apache.hadoop.io.compress.CompressionOutputStream]]
   * with `passThrough` of type `C`
   *
   * @param fs Hadoop file system
   * @param syncStrategy sync strategy
   * @param rotationStrategy rotation strategy
   * @param compressionCodec a streaming compression/decompression pair
   * @param settings hdfs writing settings
   */
  def compressedWithPassThrough[P](
      fs: FileSystem,
      syncStrategy: SyncStrategy,
      rotationStrategy: RotationStrategy,
      compressionCodec: CompressionCodec,
      settings: HdfsWritingSettings): javadsl.Flow[HdfsWriteMessage[ByteString, P], OutgoingMessage[P], NotUsed] =
    ScalaHdfsFlow
      .compressedWithPassThrough[P](
        fs,
        syncStrategy,
        rotationStrategy,
        compressionCodec,
        settings)
      .asJava

  /**
   * Java API: creates a flow for [[org.apache.hadoop.io.SequenceFile.Writer]]
   * without a compression
   *
   * @param fs Hadoop file system
   * @param syncStrategy sync strategy
   * @param rotationStrategy rotation strategy
   * @param settings hdfs writing settings
   * @param classK a key class
   * @param classV a value class
   */
  def sequence[K <: Writable, V <: Writable](
      fs: FileSystem,
      syncStrategy: SyncStrategy,
      rotationStrategy: RotationStrategy,
      settings: HdfsWritingSettings,
      classK: Class[K],
      classV: Class[V]): javadsl.Flow[HdfsWriteMessage[Pair[K, V], NotUsed], RotationMessage, NotUsed] =
    sequenceWithPassThrough[K, V, NotUsed](fs, syncStrategy, rotationStrategy, settings, classK, classV)
      .collect(ScalaHdfsFlow.OnlyRotationMessage)

  /**
   * Java API: creates a flow for [[org.apache.hadoop.io.SequenceFile.Writer]]
   * with a compression
   *
   * @param fs Hadoop file system
   * @param syncStrategy sync strategy
   * @param rotationStrategy rotation strategy
   * @param compressionType a compression type used to compress key/value pairs in the SequenceFile
   * @param compressionCodec a streaming compression/decompression pair
   * @param settings hdfs writing settings
   * @param classK a key class
   * @param classV a value class
   */
  def sequence[K <: Writable, V <: Writable](
      fs: FileSystem,
      syncStrategy: SyncStrategy,
      rotationStrategy: RotationStrategy,
      compressionType: CompressionType,
      compressionCodec: CompressionCodec,
      settings: HdfsWritingSettings,
      classK: Class[K],
      classV: Class[V]): javadsl.Flow[HdfsWriteMessage[Pair[K, V], NotUsed], RotationMessage, NotUsed] =
    sequenceWithPassThrough[K, V, NotUsed](
      fs,
      syncStrategy,
      rotationStrategy,
      compressionType,
      compressionCodec,
      settings,
      classK,
      classV).collect(ScalaHdfsFlow.OnlyRotationMessage)

  /**
   * Java API: creates a flow for [[org.apache.hadoop.io.SequenceFile.Writer]]
   * with `passThrough` of type `C` and without a compression
   *
   * @param fs Hadoop file system
   * @param syncStrategy sync strategy
   * @param rotationStrategy rotation strategy
   * @param settings hdfs writing settings
   * @param classK a key class
   * @param classV a value class
   */
  def sequenceWithPassThrough[K <: Writable, V <: Writable, P](
      fs: FileSystem,
      syncStrategy: SyncStrategy,
      rotationStrategy: RotationStrategy,
      settings: HdfsWritingSettings,
      classK: Class[K],
      classV: Class[V]): javadsl.Flow[HdfsWriteMessage[Pair[K, V], P], OutgoingMessage[P], NotUsed] =
    Flow[HdfsWriteMessage[Pair[K, V], P]]
      .map(message => message.copy(source = message.source.toScala))
      .via(
        ScalaHdfsFlow
          .sequenceWithPassThrough[K, V, P](
            fs,
            syncStrategy,
            rotationStrategy,
            settings,
            classK,
            classV))
      .asJava

  /**
   * Java API: creates a flow for [[org.apache.hadoop.io.SequenceFile.Writer]]
   * with `passThrough` of type `C` and a compression
   *
   * @param fs Hadoop file system
   * @param syncStrategy sync strategy
   * @param rotationStrategy rotation strategy
   * @param compressionType a compression type used to compress key/value pairs in the SequenceFile
   * @param compressionCodec a streaming compression/decompression pair
   * @param settings hdfs writing settings
   * @param classK a key class
   * @param classV a value class
   */
  def sequenceWithPassThrough[K <: Writable, V <: Writable, P](
      fs: FileSystem,
      syncStrategy: SyncStrategy,
      rotationStrategy: RotationStrategy,
      compressionType: CompressionType,
      compressionCodec: CompressionCodec,
      settings: HdfsWritingSettings,
      classK: Class[K],
      classV: Class[V]): javadsl.Flow[HdfsWriteMessage[Pair[K, V], P], OutgoingMessage[P], NotUsed] =
    Flow[HdfsWriteMessage[Pair[K, V], P]]
      .map(message => message.copy(source = message.source.toScala))
      .via(
        ScalaHdfsFlow
          .sequenceWithPassThrough[K, V, P](
            fs,
            syncStrategy,
            rotationStrategy,
            compressionType,
            compressionCodec,
            settings,
            classK,
            classV))
      .asJava

}
