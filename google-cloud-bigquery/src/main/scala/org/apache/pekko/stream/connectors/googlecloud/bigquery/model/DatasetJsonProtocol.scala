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

package org.apache.pekko.stream.connectors.googlecloud.bigquery.model

import org.apache.pekko
import pekko.stream.connectors.google.scaladsl.Paginated
import pekko.stream.connectors.googlecloud.bigquery.scaladsl.spray.BigQueryRestJsonProtocol._
import pekko.util.ccompat.JavaConverters._
import spray.json.{ JsonFormat, RootJsonFormat }

import java.util
import scala.collection.immutable.Seq
import scala.compat.java8.OptionConverters._

/**
 * Dataset resource model
 * @see [[https://cloud.google.com/bigquery/docs/reference/rest/v2/datasets#resource:-dataset BigQuery reference]]
 *
 * @param datasetReference a reference that identifies the dataset
 * @param friendlyName a descriptive name for the dataset
 * @param labels the labels associated with this dataset
 * @param location the geographic location where the dataset should reside
 */
final case class Dataset private (datasetReference: DatasetReference,
    friendlyName: Option[String],
    labels: Option[Map[String, String]],
    location: Option[String]) {

  def getDatasetReference = datasetReference
  def getFriendlyName = friendlyName.asJava
  def getLabels = labels.map(_.asJava).asJava
  def getLocation = location.asJava

  def withDatasetReference(datasetReference: DatasetReference) =
    copy(datasetReference = datasetReference)

  def withFriendlyName(friendlyName: Option[String]) =
    copy(friendlyName = friendlyName)
  def withFriendlyName(friendlyName: util.Optional[String]) =
    copy(friendlyName = friendlyName.asScala)

  def withLabels(labels: Option[Map[String, String]]) =
    copy(labels = labels)
  def withLabels(labels: util.Optional[util.Map[String, String]]) =
    copy(labels = labels.asScala.map(_.asScala.toMap))

  def withLocation(location: util.Optional[String]) =
    copy(location = location.asScala)
}

object Dataset {

  /**
   * Java API: Dataset resource model
   * @see [[https://cloud.google.com/bigquery/docs/reference/rest/v2/datasets#resource:-dataset BigQuery reference]]
   *
   * @param datasetReference a reference that identifies the dataset
   * @param friendlyName a descriptive name for the dataset
   * @param labels the labels associated with this dataset
   * @param location the geographic location where the dataset should reside
   * @return a [[Dataset]]
   */
  def create(datasetReference: DatasetReference,
      friendlyName: util.Optional[String],
      labels: util.Optional[util.Map[String, String]],
      location: util.Optional[String]) =
    Dataset(datasetReference, friendlyName.asScala, labels.asScala.map(_.asScala.toMap), location.asScala)

  implicit val format: RootJsonFormat[Dataset] = jsonFormat4(apply)
}

/**
 * DatasetReference model
 * @see [[https://cloud.google.com/bigquery/docs/reference/rest/v2/datasets#datasetreference BigQuery reference]]
 *
 * @param datasetId A unique ID for this dataset, without the project name
 * @param projectId The ID of the project containing this dataset
 */
final case class DatasetReference private (datasetId: Option[String], projectId: Option[String]) {

  def getDatasetId = datasetId.asJava
  def getProjectId = projectId.asJava

  def withDatasetId(datasetId: Option[String]) =
    copy(datasetId = datasetId)
  def withDatasetId(datasetId: util.Optional[String]) =
    copy(datasetId = datasetId.asScala)

  def withProjectId(projectId: Option[String]) =
    copy(projectId = projectId)
  def withProjectId(projectId: util.Optional[String]) =
    copy(projectId = projectId.asScala)
}

object DatasetReference {

  /**
   * Java API: DatasetReference model
   * @see [[https://cloud.google.com/bigquery/docs/reference/rest/v2/datasets#datasetreference BigQuery reference]]
   *
   * @param datasetId A unique ID for this dataset, without the project name
   * @param projectId The ID of the project containing this dataset
   * @return a [[DatasetReference]]
   */
  def create(datasetId: util.Optional[String], projectId: util.Optional[String]) =
    DatasetReference(datasetId.asScala, projectId.asScala)

  implicit val format: JsonFormat[DatasetReference] = jsonFormat2(apply)
}

/**
 * DatasetListResponse model
 * @see [[https://cloud.google.com/bigquery/docs/reference/rest/v2/datasets/list#response-body BigQuery reference]]
 *
 * @param nextPageToken a token that can be used to request the next results page
 * @param datasets an array of the dataset resources in the project
 */
final case class DatasetListResponse private (nextPageToken: Option[String], datasets: Option[Seq[Dataset]]) {

  def getNextPageToken = nextPageToken.asJava
  def getDatasets = datasets.map(_.asJava).asJava

  def withNextPageToken(nextPageToken: Option[String]) =
    copy(nextPageToken = nextPageToken)
  def withNextPageToken(nextPageToken: util.Optional[String]) =
    copy(nextPageToken = nextPageToken.asScala)

  def withDatasets(datasets: Option[Seq[Dataset]]) =
    copy(datasets = datasets)
  def withDatasets(datasets: util.Optional[util.List[Dataset]]) =
    copy(datasets = datasets.asScala.map(_.asScala.toList))
}

object DatasetListResponse {

  /**
   * Java API: DatasetListResponse model
   * @see [[https://cloud.google.com/bigquery/docs/reference/rest/v2/datasets/list#response-body BigQuery reference]]
   *
   * @param nextPageToken a token that can be used to request the next results page
   * @param datasets an array of the dataset resources in the project
   * @return a [[DatasetListResponse]]
   */
  def create(nextPageToken: util.Optional[String], datasets: util.Optional[util.List[Dataset]]) =
    DatasetListResponse(nextPageToken.asScala, datasets.asScala.map(_.asScala.toList))

  implicit val format: RootJsonFormat[DatasetListResponse] = jsonFormat2(apply)
  implicit val paginated: Paginated[DatasetListResponse] = _.nextPageToken
}
