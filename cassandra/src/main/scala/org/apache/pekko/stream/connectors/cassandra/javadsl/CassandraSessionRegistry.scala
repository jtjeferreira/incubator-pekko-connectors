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

package org.apache.pekko.stream.connectors.cassandra.javadsl

import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.Done
import pekko.actor.ClassicActorSystemProvider
import pekko.stream.connectors.cassandra.{ scaladsl, CassandraSessionSettings }
import com.datastax.oss.driver.api.core.CqlSession

import scala.compat.java8.FutureConverters._

/**
 * This Cassandra session registry makes it possible to share Cassandra sessions between multiple use sites
 * in the same `ActorSystem` (important for the Cassandra Akka Persistence plugin where it is shared between journal,
 * query plugin and snapshot plugin)
 */
object CassandraSessionRegistry {

  /**
   * Get the session registry with new actors API.
   */
  def get(system: ClassicActorSystemProvider): CassandraSessionRegistry =
    new CassandraSessionRegistry(scaladsl.CassandraSessionRegistry(system))

  /**
   * Get the session registry with the classic actors API.
   */
  def get(system: pekko.actor.ActorSystem): CassandraSessionRegistry =
    new CassandraSessionRegistry(scaladsl.CassandraSessionRegistry(system))

}

final class CassandraSessionRegistry private (delegate: scaladsl.CassandraSessionRegistry) {

  /**
   * Get an existing session or start a new one with the given settings,
   * makes it possible to share one session across plugins.
   *
   * Sessions in the session registry are closed after actor system termination.
   */
  def sessionFor(configPath: String): CassandraSession =
    new CassandraSession(delegate.sessionFor(configPath))

  /**
   * Get an existing session or start a new one with the given settings,
   * makes it possible to share one session across plugins.
   *
   * The `init` function will be performed once when the session is created, i.e.
   * if `sessionFor` is called from multiple places with different `init` it will
   * only execute the first.
   *
   * Sessions in the session registry are closed after actor system termination.
   */
  def sessionFor(configPath: String,
      init: java.util.function.Function[CqlSession, CompletionStage[Done]]): CassandraSession =
    new CassandraSession(delegate.sessionFor(configPath, ses => init(ses).toScala))

  /**
   * Get an existing session or start a new one with the given settings,
   * makes it possible to share one session across plugins.
   */
  def sessionFor(settings: CassandraSessionSettings): CassandraSession =
    new CassandraSession(delegate.sessionFor(settings))

}
