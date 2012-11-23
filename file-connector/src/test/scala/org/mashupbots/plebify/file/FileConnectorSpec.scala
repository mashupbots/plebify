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

object FileConnectorSpec {

  val onCreatedSaveConfig = """
	on-created-save {
      connectors = [{
          connector-id = "file"
          factory-class-name = "org.mashupbots.plebify.file.FileConnectorFactory"
        }]
      jobs = [{
          job-id = "job1"
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

  lazy val inputDir1 = FileConnectorSpec.createTempDir("input1")
  lazy val ouputDir1 = FileConnectorSpec.createTempDir("output1")

  lazy val cfg = List(onCreatedSaveConfig).mkString("\n")
    .replace("{input1}", inputDir1.getAbsolutePath())
    .replace("{output1}", ouputDir1.getAbsolutePath())
}

class FileConnectorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpec
  with MustMatchers with BeforeAndAfterAll {

  val log = LoggerFactory.getLogger("FileConnectorSpec")
  def this() = this(ActorSystem("FileConnectorSpec", ConfigFactory.parseString(FileConnectorSpec.cfg)))

  override def afterAll(configMap: Map[String, Any]) {
    FileConnectorSpec.deleteTempDir(FileConnectorSpec.inputDir1)
    FileConnectorSpec.deleteTempDir(FileConnectorSpec.ouputDir1)
  }

  "File Connector" must {

    "trigger events and execute tasks" in {
      // Start
      val engine = system.actorOf(Props(new Engine(configName = "on-created-save")), name = "on-created-save")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          m.isSuccess must be(true)
        }
      }

      // Do it
      val content = "blah blah blah"
      FileConnectorSpec.writeTextFile(new File(FileConnectorSpec.inputDir1, "justcreated.txt"), content)
      Thread.sleep(2000)

      val outputs = FileConnectorSpec.ouputDir1.listFiles()
      outputs.size must be(1)
      val outputFile = outputs(0)
      log.info("Output file {}", outputFile.getAbsolutePath())
      FileConnectorSpec.readTextFile(outputFile) must be(content)
    }

  }
}

