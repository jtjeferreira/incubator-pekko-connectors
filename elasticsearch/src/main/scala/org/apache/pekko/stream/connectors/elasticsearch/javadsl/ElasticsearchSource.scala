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

package org.apache.pekko.stream.connectors.elasticsearch.javadsl

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.http.scaladsl.{ Http, HttpExt }
import pekko.stream.connectors.elasticsearch.{ impl, _ }
import pekko.stream.javadsl.Source
import pekko.stream.{ Attributes, Materializer }
import pekko.util.ccompat.JavaConverters._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.{ ArrayNode, NumericNode }

import scala.concurrent.ExecutionContext

/**
 * Java API to create Elasticsearch sources.
 */
object ElasticsearchSource {

  /**
   * Creates a [[pekko.stream.javadsl.Source]] from Elasticsearch that streams [[ReadResult]]s of [[java.util.Map]].
   * Using default objectMapper
   */
  def create(elasticsearchParams: ElasticsearchParams,
      query: String,
      settings: SourceSettingsBase[_, _]): Source[ReadResult[java.util.Map[String, Object]], NotUsed] =
    create(elasticsearchParams, query, settings, new ObjectMapper())

  /**
   * Creates a [[pekko.stream.javadsl.Source]] from Elasticsearch that streams [[ReadResult]]s of [[java.util.Map]].
   * Using custom objectMapper
   */
  def create(elasticsearchParams: ElasticsearchParams,
      query: String,
      settings: SourceSettingsBase[_, _],
      objectMapper: ObjectMapper): Source[ReadResult[java.util.Map[String, Object]], NotUsed] =
    Source
      .fromMaterializer { (mat: Materializer, _: Attributes) =>
        {
          implicit val system: ActorSystem = mat.system
          implicit val http: HttpExt = Http()
          implicit val ec: ExecutionContext = mat.executionContext

          Source
            .fromGraph(
              new impl.ElasticsearchSourceStage(
                elasticsearchParams,
                Map("query" -> query),
                settings,
                new JacksonReader[java.util.Map[String, Object]](objectMapper, classOf[java.util.Map[String, Object]])))
        }
      }
      .mapMaterializedValue(_ => NotUsed)

  /**
   * Creates a [[pekko.stream.javadsl.Source]] from Elasticsearch that streams [[ReadResult]]s of [[java.util.Map]].
   * Using custom objectMapper.
   *
   * Example of searchParams-usage:
   *
   * Map<String, String> searchParams = new HashMap<>();
   * searchParams.put("query", "{\"match_all\": {}}");
   * searchParams.put("_source", "[\"fieldToInclude\", \"anotherFieldToInclude\"]");
   */
  def create(elasticsearchParams: ElasticsearchParams,
      searchParams: java.util.Map[String, String],
      settings: SourceSettingsBase[_, _],
      objectMapper: ObjectMapper): Source[ReadResult[java.util.Map[String, Object]], NotUsed] =
    Source
      .fromMaterializer { (mat: Materializer, _: Attributes) =>
        {
          implicit val system: ActorSystem = mat.system
          implicit val http: HttpExt = Http()
          implicit val ec: ExecutionContext = mat.executionContext

          Source.fromGraph(
            new impl.ElasticsearchSourceStage(
              elasticsearchParams,
              searchParams.asScala.toMap,
              settings,
              new JacksonReader[java.util.Map[String, Object]](objectMapper, classOf[java.util.Map[String, Object]])))
        }
      }
      .mapMaterializedValue(_ => NotUsed)

  /**
   * Creates a [[pekko.stream.javadsl.Source]] from Elasticsearch that streams [[ReadResult]]s of type `T`.
   * Using default objectMapper
   */
  def typed[T](elasticsearchParams: ElasticsearchParams,
      query: String,
      settings: SourceSettingsBase[_, _],
      clazz: Class[T]): Source[ReadResult[T], NotUsed] =
    typed[T](elasticsearchParams, query, settings, clazz, new ObjectMapper())

  /**
   * Creates a [[pekko.stream.javadsl.Source]] from Elasticsearch that streams [[ReadResult]]s of type `T`.
   * Using custom objectMapper
   */
  def typed[T](elasticsearchParams: ElasticsearchParams,
      query: String,
      settings: SourceSettingsBase[_, _],
      clazz: Class[T],
      objectMapper: ObjectMapper): Source[ReadResult[T], NotUsed] =
    Source
      .fromMaterializer { (mat: Materializer, _: Attributes) =>
        {
          implicit val system: ActorSystem = mat.system
          implicit val http: HttpExt = Http()
          implicit val ec: ExecutionContext = mat.executionContext

          Source.fromGraph(
            new impl.ElasticsearchSourceStage(
              elasticsearchParams,
              Map("query" -> query),
              settings,
              new JacksonReader[T](objectMapper, clazz)))
        }
      }
      .mapMaterializedValue(_ => NotUsed)

  /**
   * Creates a [[pekko.stream.javadsl.Source]] from Elasticsearch that streams [[ReadResult]]s of type `T`.
   * Using custom objectMapper
   *
   * Example of searchParams-usage:
   *
   * Map<String, String> searchParams = new HashMap<>();
   * searchParams.put("query", "{\"match_all\": {}}");
   * searchParams.put("_source", "[\"fieldToInclude\", \"anotherFieldToInclude\"]");
   */
  def typed[T](elasticsearchParams: ElasticsearchParams,
      searchParams: java.util.Map[String, String],
      settings: SourceSettingsBase[_, _],
      clazz: Class[T],
      objectMapper: ObjectMapper): Source[ReadResult[T], NotUsed] =
    Source
      .fromMaterializer { (mat: Materializer, _: Attributes) =>
        {
          implicit val system: ActorSystem = mat.system
          implicit val http: HttpExt = Http()
          implicit val ec: ExecutionContext = mat.executionContext

          Source.fromGraph(
            new impl.ElasticsearchSourceStage(
              elasticsearchParams,
              searchParams.asScala.toMap,
              settings,
              new JacksonReader[T](objectMapper, clazz)))
        }
      }
      .mapMaterializedValue(_ => NotUsed)

  private final class JacksonReader[T](mapper: ObjectMapper, clazz: Class[T]) extends impl.MessageReader[T] {

    override def convert(json: String): impl.ScrollResponse[T] = {

      val jsonTree = mapper.readTree(json)

      if (jsonTree.has("error")) {
        impl.ScrollResponse(Some(jsonTree.get("error").asText()), None)
      } else {
        val scrollId = Option(jsonTree.get("_scroll_id")).map(_.asText())
        val hits = jsonTree.get("hits").get("hits").asInstanceOf[ArrayNode]
        val messages = hits.elements().asScala.toList.map { element =>
          val id = element.get("_id").asText()
          val source = element.get("_source")
          val version: Option[Long] = element.get("_version") match {
            case n: NumericNode => Some(n.asLong())
            case _              => None
          }

          new ReadResult[T](id, mapper.treeToValue(source, clazz), version)
        }
        impl.ScrollResponse(None, Some(impl.ScrollResult(scrollId, messages)))
      }
    }
  }
}
