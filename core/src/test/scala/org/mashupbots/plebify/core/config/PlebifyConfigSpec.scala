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
package org.mashupbots.plebify.core.config

import org.scalatest.BeforeAndAfterAll
import org.scalatest.GivenWhenThen
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers

import com.typesafe.config.ConfigFactory

import Test1Config.apply
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider

class PlebifyConfigSpec extends WordSpec with ShouldMatchers with GivenWhenThen with BeforeAndAfterAll {

  val actorConfig = """
		test1 {
          connectors {
            file {
              description = "file system"
              factory-class-name = "org.mashupbots.plebify.fileConnector"
              initialization-timeout = 5
              param1 = "a"
            }
    
            http {
              factory-class-name = "org.mashupbots.plebify.httpConnector"
              param1 = "a"
              param2 = "b"
            }
          }
    
          jobs {
            job1 {
              description = "this is the first job"
              initialization-timeout = 5   
              on {
                http-request {
                  description = "on http request #1"
                  initialization-timeout = 5   
                  param1 = "aaa"
		  	    }
    
                http-request-2 {
		  	    }
		      }
              do {
                file-save-1 {
                  description = "save to folder 1"
                  param1 = "111"
		  	    }
    
                file-save-2 {
		  	    }
		      }
            }
    
            job2 {
              on {
                file-exists {
                  description = "when file is created"
                  param1 = "aaa"
		  	    }
		      }
              do {
                file-save-1 {
                  param1 = "111"
		  	    }
		      }
            }
          }
		}"""

  val actorSystem = ActorSystem("PlebifyConfigSpec", ConfigFactory.parseString(actorConfig))

  "PlebifyConfig" should {

    "load jobs and connectors" in {
      val cfg = Test1Config(actorSystem)
      cfg.connectors.size should equal(2)
      cfg.jobs.size should equal(2)

      val fileConnector = cfg.connectors.find(e => e.id == "file").get
      fileConnector.id should equal("file")
      fileConnector.factoryClassName should equal("org.mashupbots.plebify.fileConnector")
      fileConnector.description should equal("file system")
      fileConnector.initializationTimeout should equal(5)
      fileConnector.params.size should equal(1)
      fileConnector.params("param1") should equal("a")

      val httpConnector = cfg.connectors.find(e => e.id == "http").get
      httpConnector.id should equal("http")
      httpConnector.description should equal("")
      httpConnector.factoryClassName should equal("org.mashupbots.plebify.httpConnector")
      httpConnector.initializationTimeout should equal(3)
      httpConnector.params.size should equal(2)
      httpConnector.params("param1") should equal("a")
      httpConnector.params("param2") should equal("b")

      val job1 = cfg.jobs.find(e => e.id == "job1").get
      job1.id should equal("job1")
      job1.description should equal("this is the first job")
      job1.initializationTimeout should equal(5)
      job1.events.size should equal(2)
      job1.tasks.size should equal(2)

      val job1Event1 = job1.events.find(e => e.id == "http-request").get
      job1Event1.id should equal("http-request")
      job1Event1.connectorId should equal("http")
      job1Event1.eventName should equal("request")
      job1Event1.description should equal("on http request #1")
      job1Event1.initializationTimeout should equal(5)
      job1Event1.params.size should equal(1)
      job1Event1.params("param1") should equal("aaa")

      val job1Event2 = job1.events.find(e => e.id == "http-request-2").get
      job1Event2.id should equal("http-request-2")
      job1Event2.connectorId should equal("http")
      job1Event2.eventName should equal("request")
      job1Event2.description should equal("")
      job1Event2.initializationTimeout should equal(3)
      job1Event2.params.size should equal(0)

      val job1Task1 = job1.tasks.find(t => t.id == "file-save-1").get
      job1Task1.id should equal("file-save-1")
      job1Task1.connectorId should equal("file")
      job1Task1.taskName should equal("save")
      job1Task1.description should equal("save to folder 1")
      job1Task1.params.size should equal(1)
      job1Task1.params("param1") should equal("111")

      val job1Task2 = job1.tasks.find(t => t.id == "file-save-2").get
      job1Task2.id should equal("file-save-2")
      job1Task2.connectorId should equal("file")
      job1Task2.taskName should equal("save")
      job1Task2.description should equal("")
      job1Task2.params.size should equal(0)

      val job2 = cfg.jobs.find(e => e.id == "job2").get
      job2.id should equal("job2")
      job2.description should equal("")
      job2.initializationTimeout should equal(3)

    }

  }
}

object Test1Config extends ExtensionId[PlebifyConfig] with ExtensionIdProvider {
  override def lookup = Test1Config
  override def createExtension(system: ExtendedActorSystem) = new PlebifyConfig(system.settings.config, "test1")
}
