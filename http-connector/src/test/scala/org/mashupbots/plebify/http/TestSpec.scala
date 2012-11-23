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

class TestSpec extends WordSpec with ShouldMatchers with GivenWhenThen with BeforeAndAfterAll {
	"test" should {
	  
	  "start 2 endpoints on same port" in {
	    val system = ActorSystem("some-system")
		val endpoint1 = system.actorOf(Props[MyEndpoint1])
		val endpoint2 = system.actorOf(Props[MyEndpoint2])
		
		Thread.sleep(2000000)
	  }
	  
	}
}

class MyEndpoint1 extends Consumer {
  def endpointUri = "jetty:http://localhost:8877/example1"
  def receive = {
    case msg: CamelMessage => {
      //sender ! "Hello1: " + msg.bodyAs[String] + "\n" 
      sender ! CamelMessage("", Map.empty)
      println("1:" + msg)
    }
  }
}

class MyEndpoint2 extends Consumer {
  def endpointUri = "jetty:http://localhost:8877/example2"

  def receive = {
    case msg: CamelMessage => {
      sender ! "Hello2: " + msg + "\n" 
      println("2:" + msg)
    }
  }
}