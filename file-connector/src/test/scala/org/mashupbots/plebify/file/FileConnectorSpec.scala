//
// Copyright 2012 Vibul Imtarnasan and other Plebify contributors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package org.mashupbots.plebify.file

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

import scala.concurrent.duration.DurationInt

import org.mashupbots.plebify.core.Engine
import org.mashupbots.plebify.core.StartRequest
import org.mashupbots.plebify.core.StartResponse
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.ImplicitSender
import akka.testkit.TestKit

/**
 * Tests for [[org.mashupbots.plebify.file.FileConnector]]
 */
class FileConnectorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpec
  with MustMatchers with BeforeAndAfterAll {

  val log = LoggerFactory.getLogger("FileConnectorSpec")
  def this() = this(ActorSystem("FileConnectorSpec", ConfigFactory.parseString(FileConnectorSpec.cfg)))

  override def afterAll(configMap: Map[String, Any]) {
    FileConnectorSpec.deleteTempDir(FileConnectorSpec.inputDir1)
    FileConnectorSpec.deleteTempDir(FileConnectorSpec.ouputDir1)
    FileConnectorSpec.deleteTempDir(FileConnectorSpec.inputDir2)
    FileConnectorSpec.deleteTempDir(FileConnectorSpec.ouputDir2)
    FileConnectorSpec.deleteTempDir(FileConnectorSpec.inputDir3)
    FileConnectorSpec.deleteTempDir(FileConnectorSpec.ouputDir3)
  }

  "File Connector" must {

    "trigger events and execute tasks" in {
      val engine = system.actorOf(Props(new Engine(configName = "on-created-save")), name = "on-created-save")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          m.isSuccess must be(true)
        }
      }

      val content = "blah blah blah"
      FileConnectorSpec.writeTextFile(new File(FileConnectorSpec.inputDir1, "justcreated.txt"), content)
      Thread.sleep(3000)

      val outputs = FileConnectorSpec.ouputDir1.listFiles()
      outputs.size must be(1)
      val outputFile = outputs(0)
      log.info("Output file {}", outputFile.getAbsolutePath())
      FileConnectorSpec.readTextFile(outputFile) must be(content)
    }

    "ignore files unless it contains the specified words" in {
      val engine = system.actorOf(Props(new Engine(configName = "contain-words")), name = "contain-words")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          m.isSuccess must be(true)
        }
      }

      FileConnectorSpec.writeTextFile(new File(FileConnectorSpec.inputDir2, "A.txt"), "should not fire")
      FileConnectorSpec.writeTextFile(new File(FileConnectorSpec.inputDir2, "B.txt"), "does not contain our word")
      FileConnectorSpec.writeTextFile(new File(FileConnectorSpec.inputDir2, "C.txt"), "maceo parker\nmiles davis\n")
      FileConnectorSpec.writeTextFile(new File(FileConnectorSpec.inputDir2, "D.txt"), "rubbish")
      Thread.sleep(3000)

      val outputs = FileConnectorSpec.ouputDir2.listFiles()
      outputs.size must be(1)
    }

    "ignore files unless it matches the specified regex pattern" in {
      val engine = system.actorOf(Props(new Engine(configName = "matches-regex")), name = "matches-regex")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          m.isSuccess must be(true)
        }
      }

      FileConnectorSpec.writeTextFile(new File(FileConnectorSpec.inputDir3, "1.txt"), "ERROR test\nINFO test")
      FileConnectorSpec.writeTextFile(new File(FileConnectorSpec.inputDir3, "2.txt"), "blah blah blah")
      FileConnectorSpec.writeTextFile(new File(FileConnectorSpec.inputDir3, "3.txt"), "crap crap crap")
      FileConnectorSpec.writeTextFile(new File(FileConnectorSpec.inputDir3, "4.txt"), "\n\n test ERROR stuffed up")
      Thread.sleep(3000)

      val outputs = FileConnectorSpec.ouputDir3.listFiles()
      outputs.size must be(2)
    }

  }
}

/**
 * Companion object for [[org.mashupbots.plebify.file.FileConnectorSpec]]
 */
object FileConnectorSpec {

  def createTempDir(namePrefix: String): File = {
    val d = File.createTempFile(namePrefix, "")
    d.delete()
    d.mkdir()
    d
  }

  def deleteTempDir(f: File) {
    if (f.isDirectory) f.listFiles.foreach(file => deleteTempDir(file))
    else f.delete()
  }

  def writeTextFile(f: File, content: String) {
    Files.write(Paths.get(f.toURI), content.getBytes(Charset.forName("UTF-8")), StandardOpenOption.CREATE_NEW)
  }

  def readTextFile(f: File): String = {
    new String(Files.readAllBytes(Paths.get(f.toURI)), Charset.forName("UTF-8"))
  }

  val onCreatedSaveConfig = """
	on-created-save {
      connectors = [{
          connector-id = "file"
          factory-class-name = "org.mashupbots.plebify.file.FileConnectorFactory"
        }]
      jobs = [{
          job-id = "job-on-created-save"
          on = [{
              connector-id = "file"
              connector-event = "created"
              uri = "file:{input1}?initialDelay=500"
	        }]
          do = [{
              connector-id = "file"
              connector-task = "save"
              uri = "file:{output1}"
	        }]
        }]
	}
    
	akka {
	  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
	  loglevel = "DEBUG"
	}    
    """
  lazy val inputDir1 = FileConnectorSpec.createTempDir("input1")
  lazy val ouputDir1 = FileConnectorSpec.createTempDir("output1")

  val containsWordsConfig = """
	contain-words {
      connectors = [{
          connector-id = "file"
          factory-class-name = "org.mashupbots.plebify.file.FileConnectorFactory"
        }]
      jobs = [{
          job-id = "job-contain-words"
          on = [{
              connector-id = "file"
              connector-event = "created"
              uri = "file:{input2}?initialDelay=500"
              contains = "miles davis"
	        }]
          do = [{
              connector-id = "file"
              connector-task = "save"
              uri = "file:{output2}"
	        }]
        }]
	}
    """
  lazy val inputDir2 = FileConnectorSpec.createTempDir("input2")
  lazy val ouputDir2 = FileConnectorSpec.createTempDir("output2")

  val matchesRegexConfig = """
	matches-regex {
      connectors = [{
          connector-id = "file"
          factory-class-name = "org.mashupbots.plebify.file.FileConnectorFactory"
        }]
      jobs = [{
          job-id = "job-matches-regex"
          on = [{
              connector-id = "file"
              connector-event = "created"
              uri = "file:{input3}?initialDelay=500"
              matches = "^([\\s\\d\\w]*(ERROR|WARN)[\\s\\d\\w]*)$"
	        }]
          do = [{
              connector-id = "file"
              connector-task = "save"
              uri = "file:{output3}"
	        }]
        }]
	}
    """
  lazy val inputDir3 = FileConnectorSpec.createTempDir("input3")
  lazy val ouputDir3 = FileConnectorSpec.createTempDir("output3")

  lazy val cfg = List(onCreatedSaveConfig, containsWordsConfig, matchesRegexConfig).mkString("\n")
    .replace("{input1}", inputDir1.getAbsolutePath())
    .replace("{output1}", ouputDir1.getAbsolutePath())
    .replace("{input2}", inputDir2.getAbsolutePath())
    .replace("{output2}", ouputDir2.getAbsolutePath())
    .replace("{input3}", inputDir3.getAbsolutePath())
    .replace("{output3}", ouputDir3.getAbsolutePath())
}

