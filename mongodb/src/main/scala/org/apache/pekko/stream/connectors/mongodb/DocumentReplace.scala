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

package org.apache.pekko.stream.connectors.mongodb

import org.bson.conversions.Bson

/**
 * @param filter      a document describing the query filter, which may not be null. This can be of any type for which a { @code Codec} is registered
 * @param replacement an object to replace the previous one, which may not be null. This can be of any type for which a { @code Codec} is registered
 */
final class DocumentReplace[T] private (val filter: Bson, val replacement: T) {

  def withFilter(filter: Bson): DocumentReplace[T] = copy(filter = filter)
  def withReplacement[T1](replacement: T1): DocumentReplace[T1] = copy(replacement = replacement)

  override def toString: String =
    "DocumentReplace(" +
    s"filter=$filter," +
    s"replacement=$replacement" +
    ")"

  private def copy[T1](filter: Bson = filter, replacement: T1 = replacement) =
    new DocumentReplace[T1](filter, replacement)
}

object DocumentReplace {
  def apply[T](filter: Bson, replacement: T): DocumentReplace[T] = new DocumentReplace(filter, replacement)

  /**
   * Java Api
   */
  def create[T](filter: Bson, replacement: T): DocumentReplace[T] = DocumentReplace(filter, replacement)
}
