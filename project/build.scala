//
// Socko Web Server build file
//

import sbt._
import Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin._
import sbt.Project.Initialize
import sbtassembly.Plugin._
import AssemblyKeys._

//
// Build setup
//
object SockoBuild extends Build {

  //
  // Settings
  //
  lazy val defaultSettings = Defaults.defaultSettings ++ Seq(
    // Info
    organization := "org.mashupbots.plebify",
    version      := "0.1.0",
    scalaVersion := "2.10.0-RC1",

    // Repositories
    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    
    // Compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-optimize"),
    javacOptions  ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    
    // sbtEclipse - see examples https://github.com/typesafehub/sbteclipse/blob/master/sbteclipse-plugin/src/sbt-test/sbteclipse/02-contents/project/Build.scala
    EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.Unmanaged, EclipseCreateSrc.Source, EclipseCreateSrc.Resource),
    EclipseKeys.withSource := true    
  )
    
  //
  // Packaging to SonaType using SBT
  //
  // https://github.com/sbt/sbt.github.com/blob/gen-master/src/jekyll/using_sonatype.md
  // http://www.cakesolutions.net/teamblogs/2012/01/28/publishing-sbt-projects-to-nexus/
  // https://docs.sonatype.org/display/Repository/How+To+Generate+PGP+Signatures+With+Maven
  //    
  def plebifyPomExtra = {
    <url>http://www.plebify.com</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:mashupbots/plebify.git</url>
      <connection>scm:git:git@github.com:mashupbots/plebify.git</connection>
    </scm>
    <developers>
      <developer>
        <id>veebs</id>
        <name>Vibul Imtarnasan</name>
        <url>https://github.com/veebs</url>
      </developer>
    </developers>
  }

  def plebifyPublishTo: Initialize[Option[Resolver]] = {
    (version) { version: String =>
      val nexus = " https://oss.sonatype.org/"
      if (version.trim.endsWith("SNAPSHOT")) {
        Some("snapshots" at nexus + "content/repositories/snapshots/")
      } else {
        Some("releases" at nexus + "service/local/staging/deploy/maven2/")
      }
    }
  }
    
  lazy val doNotPublishSettings = Seq(publish := {}, publishLocal := {})

  //
  // Projects
  //
  lazy val root = Project(id = "plebify",
                          base = file("."),
                          settings = defaultSettings) aggregate(core, httpConnector, fileConnector)

  lazy val core = Project(id = "plebify-core",
                         base = file("core"),
                         settings = defaultSettings ++ Seq(
                           description := "The Plebify engine",
                           libraryDependencies ++= Dependencies.core,
                           publishTo <<= plebifyPublishTo,
                           publishMavenStyle := true,
                           publishArtifact in Test := false,
                           pomIncludeRepository := { x => false },
                           pomExtra := plebifyPomExtra
                         ))
                         
  lazy val httpConnector = Project(id = "plebify-http-connector",
                         base = file("http-connector"),
                         dependencies = Seq(core),
                         settings = defaultSettings ++ Seq(
                           description := "HTTP events and actions",
                           libraryDependencies ++= Dependencies.httpConnector
                         ))  

  lazy val fileConnector = Project(id = "plebify-file-connector",
                         base = file("file-connector"),
                         dependencies = Seq(core),
                         settings = defaultSettings ++ Seq(
                           description := "File system events and actions",
                           libraryDependencies ++= Dependencies.fileConnector
                         ))  

  lazy val examples = Project(id = "socko-examples",
                         base = file("examples"),
                         dependencies = Seq(core, httpConnector, fileConnector),
                         settings = defaultSettings ++ doNotPublishSettings ++ Seq(
                           description := "Examples",
                           libraryDependencies ++= Dependencies.examples
                         ))  

}

//
// Dependencies
//
object Dependencies {
  import Dependency._

  val core = Seq(
    Dependency.akkaActor, Dependency.akkaCamel, Dependency.akkaSlf4j, Dependency.akkaTestKit,
    Dependency.logback, Dependency.scalatest
  )
  
  val httpConnector = Seq(
  )

  val fileConnector = Seq(
  )

  val examples = Seq(
    Dependency.logback
  )  
}

object Dependency {
  val akkaActor     = "com.typesafe.akka" %% "akka-actor"        % "2.1.0-RC1" cross CrossVersion.full
  val akkaCamel     = "com.typesafe.akka" %% "akka-camel"        % "2.1.0-RC1" cross CrossVersion.full
  val akkaSlf4j     = "com.typesafe.akka" %% "akka-slf4j"        % "2.1.0-RC1" cross CrossVersion.full
  val akkaTestKit   = "com.typesafe.akka" %% "akka-testkit"      % "2.1.0-RC1"     % "test" cross CrossVersion.full
  val logback       = "ch.qos.logback"    % "logback-classic"    % "1.0.3"         % "runtime"
  val scalatest     = "org.scalatest"     %% "scalatest"         % "2.0.M4"        % "test" cross CrossVersion.full
}




