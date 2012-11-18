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
import org.mashupbots.plebify.core.config.EventSubscriptionConfig
import scala.collection.mutable.ListBuffer

object EngineProcessingSpec {

  val cfg = """
		single-connector-single-task {
          connectors {
            conn1 {
              factory-class-name = "org.mashupbots.plebify.core.EngineProcessingSpecConnectorFactory"
              task-execution-count = 1
            }
          }
          jobs {
            job1 {
              on {
                conn1-event1 {
		  	    }
		      }
              do {
                conn1-task1 {
                  description = "description of conn1-task1"
		  	    }
		      }
            }
          }
		}
    """
}

class EngineProcessingSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpec
  with MustMatchers with BeforeAndAfterAll {

  val log = LoggerFactory.getLogger("EngineProcessingSpec")

  def this() = this(ActorSystem("EngineProcessingSpec", ConfigFactory.parseString(EngineProcessingSpec.cfg)))

  "Engine processing" must {

    "be able to process single connector and single task" in {
      // Start
      val engine = system.actorOf(Props(new Engine(configName = "single-connector-single-task")),
        name = "single-connector-single-task")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          m.isSuccess must be(true)
        }
      }

      // Trigger and wait
      val connector = system.actorFor("akka://EngineProcessingSpec/user/single-connector-single-task/connector-conn1")
      connector ! "Trigger"
      expectMsgPF(5 seconds) {
        case m: List[_] => {
          m.size must be(1)
          val req = m(0).asInstanceOf[TaskExecutionRequest]
          req.jobId must be("job1")
          req.config.description must be("description of conn1-task1")
          req.config.id must be("conn1-task1")
        }
      }
    }

  }

}

class EngineProcessingSpecConnectorFactory extends ConnectorFactory {
  def create(context: ActorContext, connectorConfig: ConnectorConfig): ActorRef = {
    context.actorOf(Props(new EngineProcessingSpecConnector(connectorConfig)), name = connectorConfig.actorName)
  }
}

/**
 * Designed for only single use per test case.
 * Do not send "Trigger" message from multiple test cases at the same time
 */
class EngineProcessingSpecConnector(connectorConfig: ConnectorConfig) extends Actor with akka.actor.ActorLogging {
  log.info(s"EngineProcessingSpecConnector started")

  val subscriptions = ListBuffer[EventSubscriptionRequest]()
  val taskExecutions = ListBuffer[TaskExecutionRequest]()
  val taskExecutionCount = connectorConfig.params("task-execution-count").toInt
  var triggerSender: Option[ActorRef] = None

  def receive = {
    case msg: StartRequest =>
      sender ! StartResponse()
    case msg: EventSubscriptionRequest =>
      log.info("subscriber " + sender.path)
      subscriptions += msg
      sender ! EventSubscriptionResponse()
    case "Trigger" =>
      log.info("got trigger " + subscriptions.toString)
      triggerSender = Some(sender)
      subscriptions.foreach(s => s.job ! EventNotification(s.config.id, Map[String, String]()))
    case msg: TaskExecutionRequest => {
      taskExecutions += msg
      sender ! TaskExecutionResponse()
      if (taskExecutions.size == taskExecutionCount) {
        triggerSender.get ! taskExecutions.toList
      }
    }

  }
}