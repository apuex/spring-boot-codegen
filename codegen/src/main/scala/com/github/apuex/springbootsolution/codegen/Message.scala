package com.github.apuex.springbootsolution.codegen

import java.io.{File, PrintWriter}

import com.github.apuex.springbootsolution.runtime.SymbolConverters._
import com.github.apuex.springbootsolution.runtime.TypeConverters._
import com.github.apuex.springbootsolution.runtime.TextUtils._

import scala.xml.{Node, Text}

object Message extends App {
  val xml = ModelLoader(args(0)).xml
  val modelName = xml.attribute("name").asInstanceOf[Some[Text]].get.data
  val modelPackage = xml.attribute("package").asInstanceOf[Some[Text]].get.data
  val projectRoot = s"${System.getProperty("project.root", "target/generated")}"
  val projectDir = s"${projectRoot}/${cToShell(modelName)}/${cToShell(modelName)}-message"
  val srcDir = s"${projectDir}/src/main/proto/${modelPackage.replace('.', '/')}/message"

  new File(srcDir).mkdirs()

  project

  val printWriter = new PrintWriter(s"${srcDir}/${cToShell(modelName)}.proto", "utf-8")

  val prelude =
    s"""syntax = "proto3";
      |import "google/protobuf/timestamp.proto";
      |
      |package ${modelPackage}.message;
      |""".stripMargin

  printWriter.print(prelude)

  xml.child.filter(x => x.label == "entity")
    .foreach(x => {
      messageForEntity(modelPackage, x)
    })

  printWriter.close()

  def messageForEntity(modelPackage: String, entity: Node): Unit = {
    val entityName = entity.attribute("name").asInstanceOf[Some[Text]].get.data

    val pkColumns = entity.child.filter(x => x.label == "primaryKey")
      .flatMap(k => k.child.filter(x => x.label == "field"))
      .map(f => f.attribute("name").asInstanceOf[Some[Text]].get.data)
      .toSet

    val columns = entity.child.filter(x => x.label == "field")
      .map(f => (
        f.attribute("no").asInstanceOf[Some[Text]].get.data,
        f.attribute("name").asInstanceOf[Some[Text]].get.data,
        f.attribute("type").asInstanceOf[Some[Text]].get.data
        )
      )

    val crud =
      s"""
        |message ${cToPascal(entityName)}Vo {
        |${indent(fields(columns), 2)};
        |}
        |
        |message ${cToPascal(entityName)}ListVo {
        |  repeated ${cToPascal(entityName)}Vo items = 1;
        |}
        |
        |message Create${cToPascal(entityName)}Cmd {
        |${indent(fields(columns), 2)};
        |}
        |
        |message Update${cToPascal(entityName)}Cmd {
        |${indent(fields(columns), 2)};
        |}
        |
        |message Delete${cToPascal(entityName)}Cmd {
        |${indent(fields(columns.filter(f => pkColumns.contains(f._2))), 2)};
        |}
        |
        |message Retrieve${cToPascal(entityName)}Cmd {
        |${indent(fields(columns.filter(f => pkColumns.contains(f._2))), 2)};
        |}
        |
      """.stripMargin

    printWriter.print(crud)
  }

  def fields(columns: Seq[(String, String, String)]): String = {
    columns.map(f => "%s %s = %s".format(toProtobufType(f._3), cToCamel(f._2), f._1))
      .reduce((x, y) => "%s;\n%s".format(x, y))
  }

  private def project = {
    val printWriter = new PrintWriter(s"${projectDir}/pom.xml", "utf-8")

    val source =
      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         |         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
         |  <modelVersion>4.0.0</modelVersion>
         |
         |  <groupId>${modelPackage}</groupId>
         |  <artifactId>${cToShell(modelName)}-message</artifactId>
         |  <version>1.0-SNAPSHOT</version>
         |
         |  <parent>
         |    <groupId>${modelPackage}</groupId>
         |    <artifactId>${cToShell(modelName)}</artifactId>
         |    <version>1.0-SNAPSHOT</version>
         |  </parent>
         |
         |  <dependencies>
         |    <dependency>
         |      <groupId>com.google.protobuf</groupId>
         |      <artifactId>protobuf-java</artifactId>
         |      <version>3.5.1</version>
         |    </dependency>
         |    <dependency>
         |      <groupId>com.google.protobuf</groupId>
         |      <artifactId>protobuf-java-util</artifactId>
         |      <version>3.5.1</version>
         |    </dependency>
         |  </dependencies>
         |
         |  <build>
         |    <plugins>
         |      <plugin>
         |        <groupId>com.google.protobuf.tools</groupId>
         |        <artifactId>maven-protoc-plugin</artifactId>
         |        <version>0.3.2</version>
         |        <configuration>
         |          <protocExecutable>/usr/local/bin/protoc</protocExecutable>
         |        </configuration>
         |        <executions>
         |          <execution>
         |            <goals>
         |              <goal>compile</goal>
         |              <goal>testCompile</goal>
         |            </goals>
         |          </execution>
         |        </executions>
         |      </plugin>
         |    </plugins>
         |  </build>
         |
         |  <pluginRepositories>
         |    <pluginRepository>
         |      <id>mapr</id>
         |      <url>http://repository.mapr.com/nexus/content/groups/mapr-public/releases</url>
         |    </pluginRepository>
         |  </pluginRepositories>
         |</project>
         |
       """.stripMargin

    printWriter.print(source)

    printWriter.close()
  }
}