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

package docs.scaladsl

import io.pravega.client.stream.Serializer
import java.nio.ByteBuffer
import io.pravega.client.stream.impl.UTF8StringSerializer

object Serializers {

  implicit val stringSerializer = new UTF8StringSerializer()

  implicit val personSerializer = new Serializer[Person] {
    def serialize(x: Person): ByteBuffer = {
      val name = x.firstname.getBytes("UTF-8")
      val buff = ByteBuffer.allocate(4 + name.length).putInt(x.id)
      buff.put(ByteBuffer.wrap(name))
      buff.position(0)
      buff
    }

    def deserialize(x: ByteBuffer): Person = {
      val i = x.getInt()
      val name = new String(x.array())
      Person(i, name)
    }

  }

  implicit val intSerializer = new Serializer[Int] {
    override def serialize(value: Int): ByteBuffer = {
      val buff = ByteBuffer.allocate(4).putInt(value)
      buff.position(0)
      buff
    }

    override def deserialize(serializedValue: ByteBuffer): Int =
      serializedValue.getInt
  }

}
