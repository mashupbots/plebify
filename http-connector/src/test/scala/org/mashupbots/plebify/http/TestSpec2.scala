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
import akka.actor.Actor
import akka.actor.ActorRef
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await

class TestSpec2 extends WordSpec with ShouldMatchers with GivenWhenThen with BeforeAndAfterAll {
  "test" should {

    "start 2 endpoints on same port" in {
      val system = ActorSystem("some-system")
      val b2 = system.actorOf(Props[B2])
      val a1 = system.actorOf(Props(new A1(b2)))

      a1 ! Msg1()
      Thread.sleep(2000000)

    }

  }
}

case class Msg1()
case class Msg2()
case class Msg3()

class A1(val b2: ActorRef) extends Actor {
  import context.dispatcher

  def receive = {
    case msg: Msg1 => {
      //b2 ! Msg2()

      println("aaa")
      println("aaa")
      val future = b2.ask(Msg2())(5 seconds)
      future.onSuccess {
        case m: Msg3 => println("msg333")
      }
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")
      println("aaa")

    }
    case msg: Msg3 => {
      println("msg3")
    }
  }
}

class B2 extends Actor {
  import context.dispatcher

  def receive = {
    case msg: Msg2 => {
      //sender ! Msg3()
      context.system.scheduler.scheduleOnce(1 milliseconds, sender, Msg3())
    }
  }
}