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

package org.apache.pekko.stream.connectors.cassandra.scaladsl

import org.apache.pekko
import pekko.actor.NoSerializationVerificationNeeded
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.stream.connectors.cassandra.{ CassandraMetricsRegistry, CassandraServerMetaData, CqlSessionProvider }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.{ Materializer, SystemMaterializer }
import pekko.util.OptionVal
import pekko.{ Done, NotUsed }
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql._
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException

import scala.collection.immutable
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

/**
 * Data Access Object for Cassandra. The statements are expressed in
 * <a href="https://cassandra.apache.org/doc/latest/cql/">Apache Cassandra Query Language</a>
 * (CQL) syntax.
 *
 * See even <a href="https://docs.datastax.com/en/dse/6.7/cql/">CQL for Datastax Enterprise</a>.
 *
 * The `init` hook is called before the underlying session is used by other methods,
 * so it can be used for things like creating the keyspace and tables.
 *
 * All methods are non-blocking.
 */
final class CassandraSession(system: pekko.actor.ActorSystem,
    sessionProvider: CqlSessionProvider,
    executionContext: ExecutionContext,
    log: LoggingAdapter,
    metricsCategory: String,
    init: CqlSession => Future[Done],
    onClose: () => Unit)
    extends NoSerializationVerificationNeeded {

  implicit private[pekko] val ec: ExecutionContext = executionContext
  private lazy implicit val materializer: Materializer = SystemMaterializer(system).materializer

  log.debug("Starting CassandraSession [{}]", metricsCategory)

  private var cachedServerMetaData: OptionVal[Future[CassandraServerMetaData]] = OptionVal.None

  private val _underlyingSession: Future[CqlSession] = sessionProvider
    .connect()
    .flatMap { cqlSession =>
      cqlSession.getMetrics.ifPresent(metrics => {
        CassandraMetricsRegistry(system).addMetrics(metricsCategory, metrics.getRegistry)
      })
      init(cqlSession).map(_ => cqlSession)
    }
    .recover {
      case NonFatal(e) =>
        log.error(e, "failed to start CassandraSession")
        throw e
    }

  /**
   * The `Session` of the underlying
   * <a href="https://docs.datastax.com/en/developer/java-driver/">Datastax Java Driver</a>.
   * Can be used in case you need to do something that is not provided by the
   * API exposed by this class. Be careful to not use blocking calls.
   */
  def underlying(): Future[CqlSession] = _underlyingSession

  /**
   * Closes the underlying Cassandra session.
   * @param executionContext when used after actor system termination, the a different execution context must be provided
   */
  def close(executionContext: ExecutionContext): Future[Done] = {
    implicit val ec: ExecutionContext = executionContext
    onClose()
    _underlyingSession.map(_.closeAsync().toScala).map(_ => Done)
  }

  /**
   * Meta data about the Cassandra server, such as its version.
   */
  def serverMetaData: Future[CassandraServerMetaData] = {
    cachedServerMetaData match {
      case OptionVal.Some(cached) =>
        cached
      case OptionVal.None =>
        val result = selectOne("select cluster_name, data_center, release_version from system.local").map {
          case Some(row) =>
            new CassandraServerMetaData(row.getString("cluster_name"),
              row.getString("data_center"),
              row.getString("release_version"))
          case None =>
            log.warning("Couldn't retrieve serverMetaData from system.local table. No rows found.")
            new CassandraServerMetaData("", "", "")
        }

        result.foreach { meta =>
          cachedServerMetaData = OptionVal.Some(Future.successful(meta))
        }
        result.failed.foreach {
          case e: InvalidQueryException =>
            log.warning("Couldn't retrieve serverMetaData from system.local table: [{}]", e.getMessage)
            cachedServerMetaData = OptionVal.Some(Future.successful(new CassandraServerMetaData("", "", "")))
          case _ => // don't cache other problems, like connection errors
        }

        result
      case other => throw new MatchError(other)
    }
  }

  /**
   * Execute <a href="https://docs.datastax.com/en/dse/6.7/cql/">CQL commands</a>
   * to manage database resources (create, replace, alter, and drop tables, indexes, user-defined types, etc).
   *
   * The returned `Future` is completed when the command is done, or if the statement fails.
   */
  def executeDDL(stmt: String): Future[Done] =
    underlying().flatMap { cqlSession =>
      cqlSession.executeAsync(stmt).toScala.map(_ => Done)
    }

  /**
   * Create a `PreparedStatement` that can be bound and used in
   * `executeWrite` or `select` multiple times.
   */
  def prepare(stmt: String): Future[PreparedStatement] =
    underlying().flatMap { cqlSession =>
      cqlSession.prepareAsync(stmt).toScala
    }

  /**
   * Execute several statements in a batch. First you must `prepare` the
   * statements and bind its parameters.
   *
   * See <a href="https://docs.datastax.com/en/dse/6.7/cql/cql/cql_using/useBatchTOC.html">Batching data insertion and updates</a>.
   *
   * The configured write consistency level is used if a specific consistency
   * level has not been set on the `BatchStatement`.
   *
   * The returned `Future` is completed when the batch has been
   * successfully executed, or if it fails.
   */
  def executeWriteBatch(batch: BatchStatement): Future[Done] =
    executeWrite(batch)

  /**
   * Execute one statement. First you must `prepare` the
   * statement and bind its parameters.
   *
   * See <a href="https://docs.datastax.com/en/dse/6.7/cql/cql/cql_using/useInsertDataTOC.html">Inserting and updating data</a>.
   *
   * The configured write consistency level is used if a specific consistency
   * level has not been set on the `Statement`.
   *
   * The returned `Future` is completed when the statement has been
   * successfully executed, or if it fails.
   */
  def executeWrite(stmt: Statement[_]): Future[Done] = {
    underlying().flatMap { cqlSession =>
      cqlSession.executeAsync(stmt).toScala.map(_ => Done)
    }
  }

  /**
   * Prepare, bind and execute one statement in one go.
   *
   * See <a href="https://docs.datastax.com/en/dse/6.7/cql/cql/cql_using/useInsertDataTOC.html">Inserting and updating data</a>.
   *
   * The configured write consistency level is used.
   *
   * The returned `Future` is completed when the statement has been
   * successfully executed, or if it fails.
   */
  def executeWrite(stmt: String, bindValues: AnyRef*): Future[Done] = {
    bind(stmt, bindValues).flatMap(b => executeWrite(b))
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] def selectResultSet(stmt: Statement[_]): Future[AsyncResultSet] = {
    underlying().flatMap { s =>
      s.executeAsync(stmt).toScala
    }
  }

  /**
   * Execute a select statement. First you must `prepare` the
   * statement and bind its parameters.
   *
   * See <a href="https://docs.datastax.com/en/dse/6.7/cql/cql/cql_using/queriesTOC.html">Querying data</a>.
   *
   * The configured read consistency level is used if a specific consistency
   * level has not been set on the `Statement`.
   *
   * Note that you have to connect a `Sink` that consumes the messages from
   * this `Source` and then `run` the stream.
   */
  def select(stmt: Statement[_]): Source[Row, NotUsed] = {
    Source
      .futureSource {
        underlying().map { cqlSession =>
          Source.fromPublisher(cqlSession.executeReactive(stmt))
        }
      }
      .mapMaterializedValue(_ => NotUsed)
  }

  /**
   * Execute a select statement created by `prepare`.
   *
   * See <a href="https://docs.datastax.com/en/dse/6.7/cql/cql/cql_using/queriesTOC.html">Querying data</a>.
   *
   * The configured read consistency level is used if a specific consistency
   * level has not been set on the `Statement`.
   *
   * Note that you have to connect a `Sink` that consumes the messages from
   * this `Source` and then `run` the stream.
   */
  def select(stmt: Future[Statement[_]]): Source[Row, NotUsed] = {
    Source
      .futureSource {
        underlying().flatMap(cqlSession => stmt.map(cqlSession -> _)).map {
          case (cqlSession, stmtValue) =>
            Source.fromPublisher(cqlSession.executeReactive(stmtValue))
        }
      }
      .mapMaterializedValue(_ => NotUsed)
  }

  /**
   * Prepare, bind and execute a select statement in one go.
   *
   * See <a href="https://docs.datastax.com/en/dse/6.7/cql/cql/cql_using/queriesTOC.html">Querying data</a>.
   *
   * The configured read consistency level is used.
   *
   * Note that you have to connect a `Sink` that consumes the messages from
   * this `Source` and then `run` the stream.
   */
  def select(stmt: String, bindValues: AnyRef*): Source[Row, NotUsed] = {
    select(bind(stmt, bindValues))
  }

  /**
   * Execute a select statement. First you must `prepare` the statement and
   * bind its parameters. Only use this method when you know that the result
   * is small, e.g. includes a `LIMIT` clause. Otherwise you should use the
   * `select` method that returns a `Source`.
   *
   * The configured read consistency level is used if a specific consistency
   * level has not been set on the `Statement`.
   *
   * The returned `Future` is completed with the found rows.
   */
  def selectAll(stmt: Statement[_]): Future[immutable.Seq[Row]] = {
    select(stmt)
      .runWith(Sink.seq)
      .map(_.toVector) // Sink.seq returns Seq, not immutable.Seq (compilation issue in Eclipse)
  }

  /**
   * Prepare, bind and execute a select statement in one go. Only use this method
   * when you know that the result is small, e.g. includes a `LIMIT` clause.
   * Otherwise you should use the `select` method that returns a `Source`.
   *
   * The configured read consistency level is used.
   *
   * The returned `Future` is completed with the found rows.
   */
  def selectAll(stmt: String, bindValues: AnyRef*): Future[immutable.Seq[Row]] = {
    bind(stmt, bindValues).flatMap(bs => selectAll(bs))
  }

  /**
   * Execute a select statement that returns one row. First you must `prepare` the
   * statement and bind its parameters.
   *
   * The configured read consistency level is used if a specific consistency
   * level has not been set on the `Statement`.
   *
   * The returned `Future` is completed with the first row,
   * if any.
   */
  def selectOne(stmt: Statement[_]): Future[Option[Row]] = {
    selectResultSet(stmt).map { rs =>
      Option(rs.one()) // rs.one returns null if exhausted
    }
  }

  /**
   * Prepare, bind and execute a select statement that returns one row.
   *
   * The configured read consistency level is used.
   *
   * The returned `Future` is completed with the first row,
   * if any.
   */
  def selectOne(stmt: String, bindValues: AnyRef*): Future[Option[Row]] = {
    bind(stmt, bindValues).flatMap(bs => selectOne(bs))
  }

  private def bind(stmt: String, bindValues: Seq[AnyRef]): Future[BoundStatement] = {
    prepare(stmt).map { ps =>
      if (bindValues.isEmpty) ps.bind()
      else ps.bind(bindValues: _*)
    }
  }

}
