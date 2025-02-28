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

package org.apache.pekko.stream.connectors.googlecloud.bigquery.scaladsl.schema

import org.apache.pekko.stream.connectors.googlecloud.bigquery.model.{
  TableFieldSchema,
  TableFieldSchemaMode,
  TableSchema
}

import scala.annotation.implicitNotFound

/**
 * Provides a [[org.apache.pekko.stream.connectors.googlecloud.bigquery.model.TableFieldSchema]] for type [[T]].
 */
@implicitNotFound(msg = "Cannot find SchemaWriter type class for ${T}")
trait SchemaWriter[-T] {

  def write(name: String, mode: TableFieldSchemaMode): TableFieldSchema

}

object SchemaWriter {

  def apply[T](implicit writer: SchemaWriter[T]): SchemaWriter[T] = writer

}

/**
 * Provides a [[org.apache.pekko.stream.connectors.googlecloud.bigquery.model.TableSchema]] for type [[T]].
 */
@implicitNotFound(msg = "Cannot find TableSchemaWriter type class for ${T}")
trait TableSchemaWriter[-T] extends SchemaWriter[T] {

  def write: TableSchema

}

object TableSchemaWriter {

  def apply[T](implicit writer: TableSchemaWriter[T]): TableSchemaWriter[T] = writer

}
