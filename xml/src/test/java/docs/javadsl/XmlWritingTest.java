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

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.connectors.testkit.javadsl.LogCapturingJunit4;
import org.apache.pekko.stream.connectors.xml.Characters;
import org.apache.pekko.stream.connectors.xml.EndDocument;
import org.apache.pekko.stream.connectors.xml.EndElement;
import org.apache.pekko.stream.connectors.xml.Namespace;
import org.apache.pekko.stream.connectors.xml.ParseEvent;
import org.apache.pekko.stream.connectors.xml.StartDocument;
import org.apache.pekko.stream.connectors.xml.StartElement;
import org.apache.pekko.stream.connectors.xml.javadsl.XmlWriting;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.ByteString;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.xml.stream.XMLOutputFactory;

import static org.junit.Assert.assertEquals;

public class XmlWritingTest {
  @Rule public final LogCapturingJunit4 logCapturing = new LogCapturingJunit4();

  private static ActorSystem system;

  @Test
  public void xmlWriter() throws InterruptedException, ExecutionException, TimeoutException {

    // #writer
    final Sink<ParseEvent, CompletionStage<String>> write =
        Flow.of(ParseEvent.class)
            .via(XmlWriting.writer())
            .map(ByteString::utf8String)
            .toMat(Sink.fold("", (acc, el) -> acc + el), Keep.right());
    // #writer

    final String doc =
        "<?xml version='1.0' encoding='UTF-8'?><doc><elem>elem1</elem><elem>elem2</elem></doc>";
    final List<ParseEvent> docList = new ArrayList<>();
    docList.add(StartDocument.getInstance());
    docList.add(StartElement.create("doc", Collections.emptyMap()));
    docList.add(StartElement.create("elem", Collections.emptyMap()));
    docList.add(Characters.create("elem1"));
    docList.add(EndElement.create("elem"));
    docList.add(StartElement.create("elem", Collections.emptyMap()));
    docList.add(Characters.create("elem2"));
    docList.add(EndElement.create("elem"));
    docList.add(EndElement.create("doc"));
    docList.add(EndDocument.getInstance());

    final CompletionStage<String> resultStage = Source.from(docList).runWith(write, system);

    resultStage
        .thenAccept((str) -> assertEquals(doc, str))
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);
  }

  @Test
  public void xmlWriterNamespace()
      throws InterruptedException, ExecutionException, TimeoutException {

    // #writer
    final Sink<ParseEvent, CompletionStage<String>> write =
        Flow.of(ParseEvent.class)
            .via(XmlWriting.writer())
            .map(ByteString::utf8String)
            .toMat(Sink.fold("", (acc, el) -> acc + el), Keep.right());
    // #writer

    // #writer-usage
    final String doc =
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<bk:book xmlns:bk=\"urn:loc.gov:books\" xmlns:isbn=\"urn:ISBN:0-395-36341-6\">"
            + "<bk:title>Cheaper by the Dozen</bk:title><isbn:number>1568491379</isbn:number></bk:book>";
    final List<Namespace> nmList = new ArrayList<>();
    nmList.add(Namespace.create("urn:loc.gov:books", Optional.of("bk")));
    nmList.add(Namespace.create("urn:ISBN:0-395-36341-6", Optional.of("isbn")));
    final List<ParseEvent> docList = new ArrayList<>();
    docList.add(StartDocument.getInstance());
    docList.add(
        StartElement.create(
            "book",
            Collections.emptyList(),
            Optional.of("bk"),
            Optional.of("urn:loc.gov:books"),
            nmList));
    docList.add(
        StartElement.create(
            "title", Collections.emptyList(), Optional.of("bk"), Optional.of("urn:loc.gov:books")));
    docList.add(Characters.create("Cheaper by the Dozen"));
    docList.add(EndElement.create("title"));
    docList.add(
        StartElement.create(
            "number",
            Collections.emptyList(),
            Optional.of("isbn"),
            Optional.of("urn:ISBN:0-395-36341-6")));
    docList.add(Characters.create("1568491379"));
    docList.add(EndElement.create("number"));
    docList.add(EndElement.create("book"));
    docList.add(EndDocument.getInstance());

    final CompletionStage<String> resultStage = Source.from(docList).runWith(write, system);
    // #writer-usage

    resultStage
        .thenAccept((str) -> assertEquals(doc, str))
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);
  }

  @Test
  public void xmlWriterProvidedFactory()
      throws InterruptedException, ExecutionException, TimeoutException {

    final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
    // #writer
    final Sink<ParseEvent, CompletionStage<String>> write =
        Flow.of(ParseEvent.class)
            .via(XmlWriting.writer(xmlOutputFactory))
            .map(ByteString::utf8String)
            .toMat(Sink.fold("", (acc, el) -> acc + el), Keep.right());
    // #writer

    final String doc =
        "<?xml version='1.0' encoding='UTF-8'?><doc><elem>elem1</elem><elem>elem2</elem></doc>";
    final List<ParseEvent> docList = new ArrayList<>();
    docList.add(StartDocument.getInstance());
    docList.add(StartElement.create("doc", Collections.emptyMap()));
    docList.add(StartElement.create("elem", Collections.emptyMap()));
    docList.add(Characters.create("elem1"));
    docList.add(EndElement.create("elem"));
    docList.add(StartElement.create("elem", Collections.emptyMap()));
    docList.add(Characters.create("elem2"));
    docList.add(EndElement.create("elem"));
    docList.add(EndElement.create("doc"));
    docList.add(EndDocument.getInstance());

    final CompletionStage<String> resultStage = Source.from(docList).runWith(write, system);

    resultStage
        .thenAccept((str) -> assertEquals(doc, str))
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);
  }

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create();
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
  }
}
