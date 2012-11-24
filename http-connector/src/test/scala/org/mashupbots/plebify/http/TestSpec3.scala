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
package org.mashupbots.plebify.http

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.BeforeAndAfterAll
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import akka.camel.{ CamelMessage, Consumer }
import akka.actor.Props
import org.apache.camel.Exchange
import akka.camel.Producer
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import org.scalatest.matchers.MustMatchers
import scala.concurrent.duration.DurationInt
import org.slf4j.LoggerFactory
import akka.camel.AkkaCamelException
import akka.actor.Status.Failure

class TestSpec3(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpec
  with MustMatchers {

  val log = LoggerFactory.getLogger("TestSpec3")

  def this() = this(ActorSystem("TestSpec3", ConfigFactory.parseString("""
        	akka {
			  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
			  loglevel = "DEBUG"
			}    
    		""")))

  "jetty http producer" must {

    "recover from a 500 being returned" in {
      val consumer = system.actorOf(Props[MyConsumer])
      val producer = system.actorOf(Props[MyProducer])

      info("Should get http status 200 return")
      producer ! ""
      expectMsgPF(5 seconds) {
        case m: CamelMessage => log.info("{}", m)
      }

      info("Should get http status 200 return")
      producer ! "some data"
      expectMsgPF(5 seconds) {
        case m: CamelMessage => log.info("{}", m)
      }

      info("Should get http status 500 return")
      producer ! "return error"
      expectMsgPF(5 seconds) {
        case m: CamelMessage => log.info("{}", m)
        case Failure(e) => log.error("Error", e)
        case x => log.info("Unknown message {} {}", x.getClass.getName, x)
      }

      info("Should get http status 200 return")
      producer ! ""
      expectMsgPF(5 seconds) {
        case m: CamelMessage => log.info("{}", m)
      }
    }

  }
}

class MyConsumer extends Consumer {
  def endpointUri = "jetty:http://localhost:8877/test"
  def receive = {
    case msg: CamelMessage => {
      val body = if (msg.body != null) msg.bodyAs[String] else ""
      if (body == "return error")
        sender ! CamelMessage("", Map((Exchange.HTTP_RESPONSE_CODE, "500")))
      else
        sender ! "Hello"
    }
  }
}

class MyProducer extends Producer {
  def endpointUri = "jetty:http://localhost:8877/test"
}

