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

package org.apache.pekko.stream.connectors.google.scaladsl

/**
 * Models a paginated resource
 * @tparam T the resource
 */
trait Paginated[-T] {

  /**
   * Returns the token for the next page, if present
   * @param resource the paginated resource
   */
  def pageToken(resource: T): Option[String]
}
