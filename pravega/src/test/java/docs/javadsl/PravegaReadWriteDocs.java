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

package docs.javadsl;

import org.apache.pekko.Done;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.connectors.pravega.*;
import org.apache.pekko.stream.connectors.pravega.PravegaReaderGroupManager;
import org.apache.pekko.stream.connectors.pravega.javadsl.Pravega;
import org.apache.pekko.stream.connectors.pravega.javadsl.PravegaTable;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import io.pravega.client.stream.ReaderGroup;
import io.pravega.client.stream.Serializer;
import io.pravega.client.stream.impl.JavaSerializer;
import io.pravega.client.stream.impl.UTF8StringSerializer;
import io.pravega.client.tables.TableKey;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class PravegaReadWriteDocs extends PravegaPekkoTestCaseSupport {

  public static void docs() {
    WriterSettings<String> writerSettings =
        WriterSettingsBuilder.<String>create(system).withSerializer(new JavaSerializer<>());
    WriterSettings<String> writerSettingsWithRoutingKey =
        WriterSettingsBuilder.<String>create(system)
            .withKeyExtractor((String str) -> str.substring(0, 2))
            .withSerializer(new JavaSerializer<>());
    ReaderSettings<String> readerSettings =
        ReaderSettingsBuilder.create(system).withSerializer(new JavaSerializer<>());

    // #writing
    Sink<String, CompletionStage<Done>> sinkWithRouting =
        Pravega.sink("an_existing_scope", "an_existing_scope", writerSettings);

    CompletionStage<Done> doneWithRouting =
        Source.from(Arrays.asList("One", "Two", "Three")).runWith(sinkWithRouting, system);

    // #writing

    // #reader-group

    ReaderGroup readerGroup;
    try (PravegaReaderGroupManager readerGroupManager =
        Pravega.readerGroup("an_existing_scope", readerSettings.clientConfig())) {
      readerGroup = readerGroupManager.createReaderGroup("my_group", "streamName");
    }
    // #reader-group

    // #reading
    CompletionStage<Done> fut =
        Pravega.<String>source(readerGroup, readerSettings)
            .to(Sink.foreach(e -> processMessage(e.message())))
            .run(system);
    // #reading

    UTF8StringSerializer serializer = new UTF8StringSerializer();

    Serializer<Integer> intSerializer =
        new Serializer<Integer>() {
          public ByteBuffer serialize(Integer value) {
            ByteBuffer buff = ByteBuffer.allocate(4).putInt(value);
            buff.position(0);
            return buff;
          }

          public Integer deserialize(ByteBuffer serializedValue) {

            return serializedValue.getInt();
          }
        };

    TableWriterSettings<Integer, String> tablewriterSettings =
        TableWriterSettingsBuilder.<Integer, String>create(system, intSerializer, serializer)
            .withKeyExtractor(id -> new TableKey(intSerializer.serialize(id)))
            .build();

    // #table-writing
    final List<Pair<Integer, String>> events =
        Arrays.asList(
            new Pair<Integer, String>(1, "One"),
            new Pair<Integer, String>(2, "Two"),
            new Pair<Integer, String>(3, "Three"),
            new Pair<Integer, String>(4, "Four"));

    Sink<Pair<Integer, String>, CompletionStage<Done>> sink =
        PravegaTable.sink("an_existing_scope", "an_existing_tableName", tablewriterSettings);

    CompletionStage<Done> done = Source.from(events).toMat(sink, Keep.right()).run(system);

    // #table-writing

    TableReaderSettings<Integer, String> tableReaderSettings =
        TableReaderSettingsBuilder.<Integer, String>create(system, intSerializer, serializer)
            .withKeyExtractor(id -> new TableKey(intSerializer.serialize(id)))
            .build();

    // #table-reading

    final CompletionStage<Done> pair =
        PravegaTable.source("an_existing_scope", "an_existing_tableName", tableReaderSettings)
            .to(Sink.foreach((TableEntry<String> kvp) -> processKVP(kvp)))
            .run(system);
    // #table-reading

  }

  private static void processKVP(TableEntry<String> kvp) {
    LOGGER.info(kvp.toString());
  }

  private static void processMessage(String message) {
    LOGGER.info(message);
  }
}
