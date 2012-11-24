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

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.StringRequestEntity
import org.mashupbots.plebify.core.Engine
import org.mashupbots.plebify.core.StartRequest
import org.mashupbots.plebify.core.StartResponse
import org.scalatest.GivenWhenThen
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.slf4j.LoggerFactory
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import akka.camel.CamelMessage
import akka.camel.Consumer
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import org.apache.camel.Exchange
import org.eclipse.jetty.websocket.WebSocketClientFactory
import java.net.URI
import org.eclipse.jetty.websocket.WebSocket
import java.util.concurrent.TimeUnit
import org.eclipse.jetty.websocket.WebSocket.Connection
import akka.camel.Producer

object WebsocketSpec {

  val onReceiveSendConfig = """
	on-receive-send-frame {
      connectors = [{
          connector-id = "http"
          factory-class-name = "org.mashupbots.plebify.http.HttpConnectorFactory"
          websocket-server-1 = "websocket://localhost:9999/out"
        }]
      jobs = [{
          job-id = "job1"
          on = [{
              connector-id = "http"
              connector-event = "frame-received"
              uri = "websocket://localhost:9998/in"
	        }]
          do = [{
              connector-id = "http"
              connector-task = "send-frame"
              websocket-server = websocket-server-1
	        }]
        }]
	}
    
	akka {
	  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
	  loglevel = "DEBUG"
	}    
    """

  lazy val cfg = List(onReceiveSendConfig).mkString("\n")

  val wsFactory = new WebSocketClientFactory()
  wsFactory.start()

}

class WebsocketSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpec
  with MustMatchers with GivenWhenThen {

  val log = LoggerFactory.getLogger("WebsocketSpec")
  def this() = this(ActorSystem("WebsocketSpec", ConfigFactory.parseString(WebsocketSpec.cfg)))

  "Http Connector" must {

    "be able to receive and send an websocket frames" in {
      
      info("Start source ws-server on 9998. Text frames will be generated from this server")
      val sourseServer = system.actorOf(Props[MyWebSocketServer], "SourceServer")
      Thread.sleep(1000)

      info("Start plebify. Will consume frames from source ws-server 9998 and publish to dest ws-server on 9999")
      val engine = system.actorOf(Props(new Engine(configName = "on-receive-send-frame")), "on-receive-send-frame")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          m.isSuccess must be(true)
        }
      }      
      Thread.sleep(1000)

      info("Start sourceClient ws-client subscribing to 9998. Send message that will be broadcasted to all subscribes; including plebify")
      val sourceWebsocket = new MyWebSocket("sourceWebsocket")
      val sourceClient = WebsocketSpec.wsFactory.newWebSocketClient()
      val sourceConnection = sourceClient.open(new URI("ws://localhost:9998/in"), sourceWebsocket).get(5, TimeUnit.SECONDS)
      Thread.sleep(500)
      
      info("Start ws-client subscribing to plebify 9999. Wait for messages sent from sourceClient")
      val destWebsocket = new MyWebSocket("deskWebsocket")
      val destClient = WebsocketSpec.wsFactory.newWebSocketClient()
      val destConnection = destClient.open(new URI("ws://localhost:9999/out"), destWebsocket).get(5, TimeUnit.SECONDS)
      Thread.sleep(500)

      info("Send message: sourceClient > plebify > destClient ")
      val msg = "Hello, anybody home?"
      sourceConnection.sendMessage(msg)
      sourceConnection.sendMessage(msg)
      sourceConnection.sendMessage(msg)
      Thread.sleep(500)
      destWebsocket.messages.size must be (3)
      
    }
  }
}

class MyWebSocket(val name: String) extends WebSocket.OnTextMessage {

  val log = LoggerFactory.getLogger("MyWebSocket")
  val messages = ListBuffer[String]()

  def onOpen(connection: Connection) {
    log.info("Open {}", name)
  }

  def onClose(closeCode: Int, message: String) {
    log.info("Close {}", name)
  }

  def onMessage(data: String) {
    log.info("Data " + name + " " + data)
    messages += data
  }
}

class MyWebSocketServer extends Producer {
  def endpointUri = "websocket://localhost:9998/in?sendToAll=true"

}



