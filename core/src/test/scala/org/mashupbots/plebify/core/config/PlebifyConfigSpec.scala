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

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.BeforeAndAfterAll
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem

class PlebifyConfigSpec extends WordSpec with ShouldMatchers with GivenWhenThen with BeforeAndAfterAll {

  val actorConfig = """
		test1 {
          connectors {
            file {
              description = "file system"
              class-name = "org.mashupbots.plebify.fileConnector"
              param1 = "a"
            }
    
            http {
              class-name = "org.mashupbots.plebify.httpConnector"
              param1 = "a"
              param2 = "b"
            }
          }
    
          jobs {
            job1 {
              description = "this is the first job"
              on {
                http-request {
                  description = "on http request #1"
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

      val fileConnector = cfg.connectors("file")
      fileConnector.id should equal("file")
      fileConnector.className should equal("org.mashupbots.plebify.fileConnector")
      fileConnector.description should equal("file system")
      fileConnector.params.size should equal(1)
      fileConnector.params("param1") should equal("a")

      val httpConnector = cfg.connectors("http")
      httpConnector.id should equal("http")
      httpConnector.description should equal("")
      httpConnector.className should equal("org.mashupbots.plebify.httpConnector")
      httpConnector.params.size should equal(2)
      httpConnector.params("param1") should equal("a")
      httpConnector.params("param2") should equal("b")

      val job1 = cfg.jobs("job1")
      job1.id should equal("job1")
      job1.description should equal("this is the first job")
      job1.triggers.size should equal(2)
      job1.actions.size should equal(2)

      val job1Trigger1 = job1.triggers("http-request")
      job1Trigger1.id should equal("http-request")
      job1Trigger1.connectorId should equal("http")
      job1Trigger1.triggerId should equal("request")
      job1Trigger1.description should equal("on http request #1")
      job1Trigger1.params.size should equal(1)
      job1Trigger1.params("param1") should equal("aaa")

      val job1Trigger2 = job1.triggers("http-request-2")
      job1Trigger2.id should equal("http-request-2")
      job1Trigger2.connectorId should equal("http")
      job1Trigger2.triggerId should equal("request")
      job1Trigger2.description should equal("")
      job1Trigger2.params.size should equal(0)

      val job1Action1 = job1.actions("file-save-1")
      job1Action1.id should equal("file-save-1")
      job1Action1.connectorId should equal("file")
      job1Action1.actionId should equal("save")
      job1Action1.description should equal("save to folder 1")
      job1Action1.params.size should equal(1)
      job1Action1.params("param1") should equal("111")

      val job1Action2 = job1.actions("file-save-2")
      job1Action2.id should equal("file-save-2")
      job1Action2.connectorId should equal("file")
      job1Action2.actionId should equal("save")
      job1Action2.description should equal("")
      job1Action2.params.size should equal(0)

      val job2 = cfg.jobs("job2")
      job2.id should equal("job2")
      job2.description should equal("")

    }

  }
}

object Test1Config extends ExtensionId[PlebifyConfig] with ExtensionIdProvider {
  override def lookup = Test1Config
  override def createExtension(system: ExtendedActorSystem) = new PlebifyConfig(system.settings.config, "test1")
}
