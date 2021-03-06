package com.github.apuex.springbootsolution.codegen

import java.io._

import com.github.apuex.springbootsolution.codegen.ModelUtils._
import com.github.apuex.springbootsolution.runtime.SymbolConverters._
import com.github.apuex.springbootsolution.runtime.TextUtils._
import com.github.apuex.springbootsolution.runtime.TypeConverters._

import scala.io.Source
import scala.xml.Node

object Message extends App {
  val xml = ModelLoader(args(0)).xml
  val modelName = xml.\@("name")
  val modelPackage = xml.\@("package")
  val projectRoot = s"${System.getProperty("output.dir", "target/generated")}"
  val projectDir = s"${projectRoot}/${cToShell(modelName)}/${cToShell(modelName)}-message"
  val srcDir = s"${projectDir}/src/main/proto/${modelPackage.replace('.', '/')}/message"

  new File(srcDir).mkdirs()

  project
  scalapb

  val printWriter = new PrintWriter(s"${srcDir}/${cToShell(modelName)}.proto", "utf-8")

  val prelude =
    s"""syntax = "proto3";
      |import "google/protobuf/timestamp.proto";
      |
      |package ${modelPackage}.message;
      |option java_package = "${modelPackage}.message";
      |option java_outer_classname = "${cToPascal(modelName)}";
      |option java_multiple_files = true;
      |
      |import "scalapb/scalapb.proto";
      |option (scalapb.options) = {
      |  flat_package: true
      |};
      |""".stripMargin

  printWriter.print(prelude)

  messages(modelPackage, xml)

  printWriter.close()


  def messages(modelPackage: String, xml: Node): Unit = {
    xml.child.filter(x => x.label == "entity")
      .foreach(x => {
        messageForEntity(xml, modelPackage, x)
      })
  }

  def messageForEntity(model: Node, modelPackage: String, entity: Node): Unit = {
    val entityName = entity.\@("name")

    val pkColumns = primaryKeyColumns(model, entity)
        .map(x => x.\@("name"))
        .toSet

    val columns = persistentColumnsExtended(model, entity)
      .map(f => (
        f.\@("no"),
        f.\@("name"),
        f.\@("type")
        )
      )

    val crud =
      s"""
        |message ${cToPascal(entityName)}Vo {
        |  ${indent(fields(columns), 2)};
        |}
        |
        |message ${cToPascal(entityName)}ListVo {
        |  repeated ${cToPascal(entityName)}Vo items = 1;
        |  bool hasMore = 2;
        |  string pagingState = 3;
        |}
        |
        |message Create${cToPascal(entityName)}Cmd {
        |  ${indent(fields(columns), 2)};
        |}
        |
        |message Update${cToPascal(entityName)}Cmd {
        |  ${indent(fields(columns), 2)};
        |}
        |
        |message Delete${cToPascal(entityName)}Cmd {
        |  ${indent(fields(columns.filter(f => pkColumns.contains(f._2))), 2)};
        |}
        |
        |message Retrieve${cToPascal(entityName)}Cmd {
        |  ${indent(fields(columns.filter(f => pkColumns.contains(f._2))), 2)};
        |}
        |""".stripMargin

    printWriter.print(crud)
    if(isEnum(entity)) printWriter.print(enum(entity, entityName))
  }

  def fields(columns: Seq[(String, String, String)]): String = {
    columns.map(f => "%s %s = %s".format(toProtobufType(f._3), cToCamel(f._2), f._1))
      .reduceOption((x, y) => "%s;\n%s".format(x, y))
      .getOrElse("")
  }

  private def enumItems(entity: Node): String = {
    entity.child.filter(x => x.label == "row")
      .map(x => "%s = %s;".format(
        x.\@("name"),
        x.\@("id")))
      .reduceOption((x, y) => "%s\n%s".format(x, y))
      .getOrElse("")
  }

  private def enum(entity: Node, entityName: String): String = {
    s"""
       |enum ${cToPascal(entityName)} {
       |  ${indent(enumItems(entity), 2)}
       |}
       """.stripMargin
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
         |      <version>3.6.1</version>
         |    </dependency>
         |  </dependencies>
         |
         |  <build>
         |    <plugins>
         |      <!-- walk-around solution for idea cannot import generated code. -->
         |      <plugin>
         |        <groupId>org.apache.maven.plugins</groupId>
         |        <artifactId>maven-resources-plugin</artifactId>
         |        <version>3.1.0</version>
         |        <configuration>
         |          <resources>
         |            <resource>
         |              <directory>target/generated-resources</directory>
         |            </resource>
         |          </resources>
         |        </configuration>
         |      </plugin>
         |      <plugin>
         |        <groupId>org.xolstice.maven.plugins</groupId>
         |        <artifactId>protobuf-maven-plugin</artifactId>
         |        <version>0.5.1</version>
         |        <configuration>
         |          <protocExecutable>/usr/local/bin/protoc</protocExecutable>
         |          <includeDependenciesInDescriptorSet>true</includeDependenciesInDescriptorSet>
         |          <attachDescriptorSet>true</attachDescriptorSet>
         |          <writeDescriptorSet>true</writeDescriptorSet>
         |        </configuration>
         |        <executions>
         |          <execution>
         |            <goals>
         |              <goal>compile</goal>
         |              <goal>test-compile</goal>
         |            </goals>
         |          </execution>
         |        </executions>
         |      </plugin>
         |    </plugins>
         |  </build>
         |
         |</project>
         |
       """.stripMargin

    printWriter.print(source)

    printWriter.close()
  }

  def scalapb: Unit = {
    val is = getClass.getResourceAsStream("/scalapb/scalapb.proto")
    new File(s"${projectDir}/src/main/proto/scalapb/").mkdirs()
    val pw = new PrintWriter(s"${projectDir}/src/main/proto/scalapb/scalapb.proto", "utf-8")

    Source.fromInputStream(is, "utf-8")
        .getLines()
        .foreach(l => pw.println(l))

    is.close()
    pw.close()
  }
}
