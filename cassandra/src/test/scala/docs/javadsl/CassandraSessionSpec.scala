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

package docs.javadsl

import java.util
import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.Done
import pekko.actor.ActorSystem
import pekko.event.Logging
import pekko.stream.connectors.cassandra.CassandraSessionSettings
import pekko.stream.connectors.cassandra.javadsl
import pekko.stream.connectors.cassandra.scaladsl
import pekko.stream.connectors.cassandra.scaladsl.CassandraSpecBase
import pekko.stream.javadsl.Sink
import pekko.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped
import pekko.stream.testkit.scaladsl.TestSink
import pekko.util.ccompat.JavaConverters._
import com.datastax.oss.driver.api.core.cql.Row

import scala.collection.immutable
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

final class CassandraSessionSpec extends CassandraSpecBase(ActorSystem("CassandraSessionSpec")) {

  val log = Logging(system, this.getClass)
  val javadslSessionRegistry = javadsl.CassandraSessionRegistry.get(system)

  val data = 1 until 103

  private val dataTableName = "testcounts"
  lazy val dataTable = s"$keyspaceName.$dataTableName"

  def insertDataTable() = {
    withSchemaMetadataDisabled {
      for {
        _ <- lifecycleSession.executeDDL(s"""CREATE TABLE IF NOT EXISTS $dataTable (
               |  partition text,
               |  key text,
               |  count bigint,
               |  PRIMARY KEY (partition, key)
               |)
               |""".stripMargin)
        _ <- executeCql(
          immutable.Seq(
            s"INSERT INTO $dataTable (partition, key, count) VALUES ('A', 'a', 1);",
            s"INSERT INTO $dataTable (partition, key, count) VALUES ('A', 'b', 2);",
            s"INSERT INTO $dataTable (partition, key, count) VALUES ('A', 'c', 3);",
            s"INSERT INTO $dataTable (partition, key, count) VALUES ('A', 'd', 4);",
            s"INSERT INTO $dataTable (partition, key, count) VALUES ('B', 'e', 5);",
            s"INSERT INTO $dataTable (partition, key, count) VALUES ('B', 'f', 6);"))
      } yield Done
    }.futureValue mustBe Done
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    insertDataTable()
  }

  val sessionSettings: CassandraSessionSettings = CassandraSessionSettings()
  override val lifecycleSession: scaladsl.CassandraSession =
    sessionRegistry.sessionFor(sessionSettings)

  // testing javadsl to prove delegation works
  lazy val session: javadsl.CassandraSession = javadslSessionRegistry.sessionFor(sessionSettings)

  def await[T](cs: CompletionStage[T]): T = cs.toScala.futureValue

  "session" must {

    "stream the result of a Cassandra statement with one page" in assertAllStagesStopped {
      val session = javadslSessionRegistry.sessionFor(sessionSettings)
      val table = createTableName()
      withSchemaMetadataDisabled {
        for {
          _ <- lifecycleSession.executeDDL(s"""
               |CREATE TABLE IF NOT EXISTS $table (
               |    id int PRIMARY KEY
               |);""".stripMargin)
          _ <- Future.sequence(data.map { i =>
            lifecycleSession.executeWrite(s"INSERT INTO $table(id) VALUES ($i)")
          })
        } yield Done
      }.futureValue mustBe Done
      val sink: Sink[Row, CompletionStage[util.List[Row]]] = Sink.seq
      val rows = session.select(s"SELECT * FROM $table").runWith(sink, materializer).toScala.futureValue
      rows.asScala.map(_.getInt("id")) must contain theSameElementsAs data
    }

  }

  "CassandraSession" must {

    "select prepared Statement[_]as Source" in {
      val stmt = await(session.prepare(s"SELECT count FROM $dataTable WHERE partition = ?"))
      val bound = stmt.bind("A")
      val rows = session.select(bound).asScala
      val probe = rows.map(_.getLong("count")).runWith(TestSink.probe[Long])
      probe.within(10.seconds) {
        probe.request(10).expectNextUnordered(1L, 2L, 3L, 4L).expectComplete()
      }
    }

    "select and bind as Source" in {
      val rows = session.select(s"SELECT count FROM $dataTable WHERE partition = ?", "B").asScala
      val probe = rows.map(_.getLong("count")).runWith(TestSink.probe[Long])
      probe.within(10.seconds) {
        probe.request(10).expectNextUnordered(5L, 6L).expectComplete()
      }
    }
    "selectAll and bind" in {
      val rows = await(session.selectAll(s"SELECT count FROM $dataTable WHERE partition = ?", "A"))
      rows.asScala.map(_.getLong("count")).toSet mustBe Set(1L, 2L, 3L, 4L)
    }

    "selectAll empty" in {
      val rows = await(session.selectAll(s"SELECT count FROM $dataTable WHERE partition = ?", "X"))
      rows mustBe empty
    }

    "selectOne and bind" in {
      val row = await(session.selectOne(s"SELECT count FROM $dataTable WHERE partition = ? and key = ?", "A", "b"))
      row.get.getLong("count") mustBe 2L
    }

    "selectOne empty" in {
      val row = await(session.selectOne(s"SELECT count FROM $dataTable WHERE partition = ? and key = ?", "A", "x"))
      row.asScala mustBe empty
    }

    "create indexes" in {
      withSchemaMetadataDisabled(
        lifecycleSession.executeDDL(
          s"CREATE INDEX IF NOT EXISTS count_idx ON $dataTable(count)")).futureValue mustBe Done
      val row =
        await(
          session.selectOne("SELECT * FROM system_schema.indexes WHERE table_name = ? ALLOW FILTERING", dataTableName))
      row.asScala.map(index => index.getString("table_name") -> index.getString("index_name")) mustBe Some(
        dataTableName -> "count_idx")
    }

  }
}
