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
import akka.actor.ActorContext

object EngineStartupSpec {

  val cfg = """
		connector-class-not-found {
          connectors {
            notfound {
              factory-class-name = "org.mashupbots.plebify.fileConnector"
            }
          }
          jobs {
            job1 {
              on {
                notfound-event {
		  	    }
		      }
              do {
                notfound-task {
		  	    }
		      }
            }
          }
		}
    
		connector-no-response {
          connectors {
            dummy1 {
              factory-class-name = "org.mashupbots.plebify.core.DummyEngineSpecConnectorFactory"
              initialization-timeout = 1
              no-start-response = true
            }
          }
          jobs {
            job1 {
              on {
                dummy1-event {
		  	    }
		      }
              do {
                dummy1-task {
		  	    }
		      }
            }
          }
		}   
    
        job-not-subscribe {
          connectors {
            dummy1 {
              factory-class-name = "org.mashupbots.plebify.core.DummyEngineSpecConnectorFactory"
              no-subscription-response = true
            }
          }
          jobs {
            job1 {
              on {
                dummy1-event {
                  initialization-timeout = 1
		  	    }
		      }
              do {
                dummy1-task {
		  	    }
		      }
            }
          }
		}    
    """

}

class EngineStartupSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpec 
  with MustMatchers with BeforeAndAfterAll {

  val log = LoggerFactory.getLogger("EngineSpec")

  def this() = this(ActorSystem("EngineSpec", ConfigFactory.parseString(EngineStartupSpec.cfg)))

  "Engine Startup" must {

    "fail when connector class is not found" in {
      val engine = system.actorOf(Props(new Engine(configName = "connector-class-not-found")),
        name = "connector-class-not-found")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          log.debug(m.toString)
          m.isSuccess must be(false)
          m.error.get.getMessage.contains("org.mashupbots.plebify.fileConnector") must be(true)
        }
      }
    }

    "fail when connector does not response with a StartResponse" in {
      val engine = system.actorOf(Props(new Engine(configName = "connector-no-response")),
        name = "connector-no-response")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          log.debug(m.toString)
          m.isSuccess must be(false)
          m.error.get.getMessage.contains("Error while waiting for connector start futures. Timed out") must be(true)
        }
      }
    }

    "fail when job connector does not respond with a EventSubscriptionResponse" in {
      val engine = system.actorOf(Props(new Engine(configName = "job-not-subscribe")), name = "job-not-subscribe")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          log.debug(m.toString)
          m.isSuccess must be(false)
          m.error.get.getMessage.contains("Error starting one or more jobs") must be(true)
        }
      }
    }

  }
}

class DummyEngineSpecConnectorFactory extends ConnectorFactory {
  def create(context: ActorContext, connectorConfig: ConnectorConfig): ActorRef = {
    context.actorOf(Props(new DummyEngineSpecConnector1(connectorConfig)), name = connectorConfig.actorName)
  }
}

class DummyEngineSpecConnector1(connectorConfig: ConnectorConfig) extends Actor with akka.actor.ActorLogging {
  log.info(s"DummyEngineSpecConnector1 instanced as ${context.self.path.toString}")
  def receive = {
    case x: StartRequest =>
      if (!connectorConfig.params.contains("no-start-response"))
        sender ! StartResponse()
    case x: EventSubscriptionRequest =>
      if (!connectorConfig.params.contains("no-subscription-response"))
        sender ! EventSubscriptionResponse()
  }
}