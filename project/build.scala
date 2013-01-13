//
// Plebify build file
//

import sbt._
import Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin._
import sbt.Project.Initialize
import sbtassembly.Plugin._
import AssemblyKeys._
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, dist, outputDirectory, distJvmOptions, distMainClass }
import java.io.File

//
// Build setup
//
object PlebifyBuild extends Build {

  val plebifyVersion = "0.2"

  //
  // Settings
  //
  lazy val defaultSettings = Defaults.defaultSettings ++ Seq(
    // Build
    organization := "org.mashupbots.plebify",
    version      := plebifyVersion,
    scalaVersion := "2.10.0",
    organizationHomepage := Some(url("https://github.com/mashupbots/plebify")),

    // Repositories
    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    
    // Compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-optimize", "-feature", "-language:postfixOps"),
    javacOptions  ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),

    //
    // Generate eclipse project files using sbteclipse-plugin
    // https://github.com/typesafehub/sbteclipse/blob/master/sbteclipse-plugin/src/sbt-test/sbteclipse/02-contents/project/Build.scala
    //
    EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.Unmanaged, EclipseCreateSrc.Source, EclipseCreateSrc.Resource),
    EclipseKeys.withSource := true    
  )

  //
  // Dont' generate eclipse files
  // 
  lazy val doNotGenerateEclipseFiles = Seq(EclipseKeys.skipProject := true)

  //
  // Don't publish this project to maven    
  //
  lazy val doNotPublishSettings = Seq(publish := {}, publishLocal := {})

  //
  // Packaging to SonaType using xsbt-gpg-plugin
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

  //
  // Projects
  //
  lazy val root = Project(
    id = "plebify",
    base = file("."),
    settings = defaultSettings
  ) aggregate (
    core,
    httpConnector, fileConnector, 
    mailConnector, dbConnector,
    kernel
  )

  lazy val core = Project(
    id = "plebify-core",
    base = file("core"),
    settings = defaultSettings ++ 
      Seq(
        description := "The Plebify engine",
        libraryDependencies ++= Dependencies.core,
        publishTo <<= plebifyPublishTo,
        publishMavenStyle := true,
        publishArtifact in Test := false,
        pomIncludeRepository := { x => false },
        pomExtra := plebifyPomExtra
      )
  )
                         
  lazy val httpConnector = Project(
    id = "plebify-http-connector",
    base = file("http-connector"),
    dependencies = Seq(core),
    settings = defaultSettings ++ 
      Seq(
        description := "HTTP events and actions",
        libraryDependencies ++= Dependencies.httpConnector
      )
  )  

  lazy val fileConnector = Project(
    id = "plebify-file-connector",
    base = file("file-connector"),
    dependencies = Seq(core),
    settings = defaultSettings ++ 
      Seq(
        description := "File system events and actions",
        libraryDependencies ++= Dependencies.fileConnector
      )
  )  

  lazy val mailConnector = Project(
    id = "plebify-mail-connector",
    base = file("mail-connector"),
    dependencies = Seq(core),
    settings = defaultSettings ++ 
      Seq(
        description := "Email events and actions",
        libraryDependencies ++= Dependencies.mailConnector
      )
  )  

  lazy val dbConnector = Project(
    id = "plebify-db-connector",
    base = file("db-connector"),
    dependencies = Seq(core),
    settings = defaultSettings ++ 
      Seq(
        description := "Database events and actions",
        libraryDependencies ++= Dependencies.dbConnector
      )
  )  

  lazy val kernel = Project(
    id = "plebify-kernel",
    base = file("kernel"),
    dependencies = Seq(
      core,
      httpConnector, fileConnector, 
      mailConnector, dbConnector
    ),
    settings = defaultSettings ++ 
      AkkaKernelPlugin.distSettings ++ 
      Seq(
        description := "Database events and actions",
        libraryDependencies ++= Dependencies.kernel,
        distJvmOptions in Dist := "-Xms256M -Xmx1024M",
        outputDirectory in Dist := file("target/plebify-" + plebifyVersion),
        distMainClass in Dist := "akka.kernel.Main org.mashupbots.plebify.kernel.PlebifyKernel",
        dist <<= (dist, sourceDirectory, state) map { (targetDir:File, srcDir:File, st) => {
            val log = st.log
            val fromDir = new File(srcDir, "examples")
            val toDir = new File(targetDir, "examples")
            ExtraWork.copyFiles(fromDir, toDir)
            log.info("Copied examples")
            targetDir
          }
        }
      )
  )  

}

//
// Extra build work that we have to do
//
object ExtraWork {
  def copyFiles(fromDir: File, toDir: File) = {
    val files = fromDir.listFiles.filter(f => f.isFile)
    for (f <- files) {
      IO.copyFile(f, new File(toDir, f.getName))
    }
  }
}

//
// Dependencies
//
object Dependencies {
  import Dependency._

  val core = Seq(
    Dependency.akkaActor, Dependency.akkaCamel, Dependency.akkaSlf4j, Dependency.akkaTestKit,
    Dependency.logback, Dependency.scalatest, Dependency.akkaKernel
  )
  
  val httpConnector = Seq(
    Dependency.camelJetty, Dependency.camelWebSocket, Dependency.logback, Dependency.scalatest, 
    Dependency.akkaTestKit, Dependency.akkaKernel
  )

  val fileConnector = Seq(
    Dependency.logback, Dependency.scalatest, Dependency.akkaTestKit, Dependency.akkaKernel
  )

  val mailConnector = Seq(
    Dependency.camelMail, Dependency.logback, Dependency.scalatest, Dependency.akkaTestKit, Dependency.akkaKernel
  )

  val dbConnector = Seq(
    Dependency.camelJDBC, Dependency.mysql, Dependency.postgresql, Dependency.commonsDbcp,
    Dependency.logback, Dependency.scalatest, Dependency.akkaTestKit, Dependency.akkaKernel
  )

  val kernel = Seq(
    Dependency.akkaKernel, Dependency.akkaSlf4j, Dependency.logback
  )  
}

object Dependency {
  object V {
    // Dont' forget to change the akka version in plugins.sbt too!
    val Akka         = "2.1.0"

    // Akka 2.1 uses camel 2.10
    val Camel        =  "2.10.0"
  }

  val akkaKernel     = "com.typesafe.akka" %% "akka-kernel"       % V.Akka
  val akkaActor      = "com.typesafe.akka" %% "akka-actor"        % V.Akka
  val akkaCamel      = "com.typesafe.akka" %% "akka-camel"        % V.Akka
  val akkaSlf4j      = "com.typesafe.akka" %% "akka-slf4j"        % V.Akka
  val akkaTestKit    = "com.typesafe.akka" %% "akka-testkit"      % V.Akka % "test"
  val logback        = "ch.qos.logback"    % "logback-classic"    % "1.0.9"
  val scalatest      = "org.scalatest"     % "scalatest_2.10"     % "2.0.M5b" % "test"
  
  val camelJetty     = "org.apache.camel"  % "camel-jetty"        % V.Camel
  val camelWebSocket = "org.apache.camel"  % "camel-websocket"    % V.Camel
  val camelMail      = "org.apache.camel"  % "camel-mail"         % V.Camel
  val camelJDBC      = "org.apache.camel"  % "camel-jdbc"         % V.Camel

  // Extras for database dependencies
  val commonsDbcp    = "commons-dbcp"      % "commons-dbcp"            % "1.4"
  val mysql          = "mysql"             % "mysql-connector-java"    % "5.1.21"
  val postgresql     = "postgresql"        % "postgresql"              % "9.1-901-1.jdbc4"
}




