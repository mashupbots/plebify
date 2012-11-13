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
package org.mashupbots.plebify.core

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.testkit.TestKit
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import org.mashupbots.plebify.core.config.ConnectorConfig
import akka.actor.ActorRef
import org.slf4j.LoggerFactory

object EngineSpec {

  val cfg = """
		class-not-found {
          connectors {
            notfound {
              factory-class-name = "org.mashupbots.plebify.fileConnector"
            }
          }
          jobs {
            job1 {
              on {
                notfound-event {
                  description = "on http request #1"
                  param1 = "aaa"
		  	    }
		      }
              do {
                notfound-task {
		  	    }
		      }
            }
          }
		}
    
		connector-not-found {
          connectors {
            dummy1 {
              factory-class-name = "org.mashupbots.plebify.core.DummyEngineSpecConnectorFactory"
            }
          }
          jobs {
            job1 {
              on {
                notfound-event {
                  description = "on http request #1"
                  param1 = "aaa"
		  	    }
		      }
              do {
                notfound-task {
		  	    }
		      }
            }
          }
		}
    
    
    """

}

class EngineSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpec with MustMatchers
  with BeforeAndAfterAll {

  val log = LoggerFactory.getLogger("EngineSpec")

  def this() = this(ActorSystem("EngineSpec", ConfigFactory.parseString(EngineSpec.cfg)))

  "Engine" must {

    "Fail when connector class is not found" in {
      val engine = system.actorOf(Props(new Engine(configName = "class-not-found")))
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          log.debug(m.toString)
          m.isSuccess must be(false)
          m.error.get.isInstanceOf[ClassNotFoundException] must be(true)
        }
      }
    }

    "Fail when job cannot find connector" in {
      val engine = system.actorOf(Props(new Engine(configName = "connector-not-found")))
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          log.debug(m.toString)
          //log.error("", m.error.get)
          m.isSuccess must be(false)
          m.error.get.getMessage must be ("Connector plebify-connector-notfound is not running!")
        }
      }

    }
  }
}

class DummyEngineSpecConnectorFactory extends ConnectorFactory {
  def create(system: ActorSystem, connectorConfig: ConnectorConfig): ActorRef = {
    system.actorOf(Props(new DummyEngineSpecConnector1(connectorConfig)), name = connectorConfig.actorName)
  }
}

class DummyEngineSpecConnector1(connectorConfig: ConnectorConfig) extends Actor with akka.actor.ActorLogging {
  log.info(s"DummyEngineSpecConnector1 instanced as ${context.self.path.toString}")
  def receive = {
    case x: StartRequest =>
      log.info(s"${context.self.path.toString} start")
      sender ! StartResponse()
    case x: StopRequest =>
      log.info(s"${context.self.path.toString} stop")
      sender ! StopResponse()
    case x => log.info(s"Unknown message ${x.toString}")
  }
}