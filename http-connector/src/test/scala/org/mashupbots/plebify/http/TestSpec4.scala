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
import akka.camel.Producer

class TestSpec4 extends WordSpec with ShouldMatchers {
  "test4" should {

    "do something" in {
      val system = ActorSystem("ccc", ConfigFactory.parseString("""
        	akka {
			  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
			  loglevel = "DEBUG"
			}    
    		"""))
      system.actorOf(Props[MyWsProducer])
      Thread.sleep(500)
      system.actorOf(Props[MyWsConsumer])

      Thread.sleep(1000)
      
      system.shutdown()
    }

  }
}

class MyWsProducer extends Producer {
  def endpointUri = "websocket://localhost:8222/consumer"

}

class MyWsConsumer extends Consumer {
  def endpointUri = "websocket://localhost:8222/consumer"

  def receive = {
    case msg: CamelMessage => println(msg)

  }
}
