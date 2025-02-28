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

package org.apache.pekko.stream.connectors.googlecloud.storage.impl

import org.apache.pekko
import pekko.stream.connectors.googlecloud.storage.StorageObject
import pekko.annotation.InternalApi

@InternalApi
private[impl] final case class RewriteResponse(
    kind: String,
    totalBytesRewritten: Long,
    objectSize: Long,
    done: Boolean,
    rewriteToken: Option[String],
    resource: Option[StorageObject])
