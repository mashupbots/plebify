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
package org.mashupbots.plebify.mail

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
import org.mashupbots.plebify.core.ConnectorFactory
import akka.actor.ActorContext
import org.mashupbots.plebify.core.config.ConnectorConfig
import akka.actor.ActorRef
import akka.actor.Actor
import org.mashupbots.plebify.core.EventSubscriptionRequest
import org.mashupbots.plebify.core.EventSubscriptionResponse
import org.mashupbots.plebify.core.TaskExecutionRequest
import org.mashupbots.plebify.core.TaskExecutionResponse
import scala.collection.mutable.ListBuffer
import org.mashupbots.plebify.core.EventNotification
import scala.collection.JavaConversions._
import org.mashupbots.plebify.core.EventData

object MailConnectorSpec {

  val sendReceiveEmail = """
	send-receive-email {
      connectors = [{
          connector-id = "mail"
          factory-class-name = "org.mashupbots.plebify.mail.MailConnectorFactory"
          my-smtp-uri = "{send-uri}"
        },{
          connector-id = "test"
          factory-class-name = "org.mashupbots.plebify.mail.MailSpecConnectorFactory"
        }]
      jobs = [{
        job-id = "job-send-email-without-template"
          on = [{
              connector-id = "test"
              connector-event = "event"
	        }]
          do = [{
              connector-id = "mail"
              connector-task = "send"
              from = "{send-from}"
              to = "{send-to}"
              subject = "send email without template"
              uri = "lookup:my-smtp-uri"
	        }]
        },{
        job-id = "job-send-email-with-template"
          on = [{
              connector-id = "test"
              connector-event = "event"
	        }]
          do = [{
              connector-id = "mail"
              connector-task = "send"
              from = "{send-from}"
              to = "{send-to}"
              subject = "send email with template"
              template = "{{one}} {{two}} {{three}}"
              uri = "lookup:my-smtp-uri"
	        }]
        },{
          job-id = "job-receive-email"
          on = [{
              connector-id = "mail"
              connector-event = "received"
              uri = "{receive-uri}&consumer.initialDelay=3000&consumer.delay=2000"
	        }]
          do = [{
              connector-id = "test"
              connector-task = "task"
	        }]
        }]
	}
    
	akka {
	  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
	  loglevel = "DEBUG"
	}    
    """

  val mailSettingsFile = Paths.get(System.getProperty("user.home"), "plebify-tests-config.txt")

  val mailSettings: Map[String, String] = Files.readAllLines(mailSettingsFile, Charset.forName("UTF-8"))
    .filter(s => s.length() > 0 && !s.trim().startsWith("#"))
    .map(s => {
      val idx = s.indexOf("=")
      (s.substring(0, idx).trim(), s.substring(idx + 1).trim())
    }).toMap

  val cfg = List(sendReceiveEmail).mkString("\n")
    .replace("{send-from}", mailSettings("mail-send-from"))
    .replace("{send-to}", mailSettings("mail-send-to"))
    .replace("{send-uri}", mailSettings("mail-send-uri"))
    .replace("{receive-uri}", mailSettings("mail-receive-uri"))
}

class MailConnectorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpec
  with MustMatchers with BeforeAndAfterAll {

  println(MailConnectorSpec.cfg)
  val log = LoggerFactory.getLogger("MailConnectorSpec")
  def this() = this(ActorSystem("MailConnectorSpec", ConfigFactory.parseString(MailConnectorSpec.cfg)))

  "Mail Connector" must {

    "be able to send and receive email" in {
      val engine = system.actorOf(Props(new Engine(configName = "send-receive-email")), name = "send-receive-email")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          m.isSuccess must be(true)
        }
      }

      val connector = system.actorFor("akka://MailConnectorSpec/user/send-receive-email/connector-test")
      connector ! "Trigger"

      Thread.sleep(15000)

      connector ! "Dump"
      expectMsgPF(5 seconds) {
        case m: List[_] => {
          // There may be > 2 emails downloaded if the mail box is full of other mail
          m.size must be >=(2)
          
          // Check that we have the 2 emails that we expect
          val rList = m.asInstanceOf[List[TaskExecutionRequest]]
          rList.exists(r => r.eventNotification.data("Subject") == "send email with template") must be (true)
          rList.exists(r => r.eventNotification.data(EventData.Content).contains("1 2 3")) must be (true)
          rList.exists(r => r.eventNotification.data("Subject") == "send email without template") must be (true)
          rList.exists(r => r.eventNotification.data(EventData.Content).contains("this is some data to send")) must be (true)
        }
      }

    }

  }
}

class MailSpecConnectorFactory extends ConnectorFactory {
  def create(context: ActorContext, connectorConfig: ConnectorConfig): ActorRef = {
    context.actorOf(Props(new MailSpecConnector(connectorConfig)), name = connectorConfig.actorName)
  }
}

/**
 * Designed for only single use per test case.
 */
class MailSpecConnector(connectorConfig: ConnectorConfig) extends Actor with akka.actor.ActorLogging {
  log.info(s"EngineProcessingSpecConnector started")

  val subscriptions = ListBuffer[EventSubscriptionRequest]()
  val taskExecutions = ListBuffer[TaskExecutionRequest]()

  def receive = {
    case msg: StartRequest =>
      sender ! StartResponse()
    case msg: EventSubscriptionRequest => {
      subscriptions += msg
      sender ! EventSubscriptionResponse()
    }
    case msg: TaskExecutionRequest => {
      log.info("got " + msg.toString)
      taskExecutions += msg
      sender ! TaskExecutionResponse()
    }
    case "Trigger" => {
      log.info("Got TRIGGER")
      val data = Map(
        ("Content", "this is some data to send"),
        ("one", "1"),
        ("two", "2"),
        ("three", "3"))

      subscriptions.foreach(s => s.job ! EventNotification(s.config, data))
    }
    case "Dump" => {
      log.info("Got DUMP")
      sender ! taskExecutions.toList
    }

  }
}

