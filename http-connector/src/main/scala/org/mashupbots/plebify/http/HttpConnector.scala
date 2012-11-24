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

import org.mashupbots.plebify.core.DefaultConnector
import org.mashupbots.plebify.core.EventSubscriptionRequest
import org.mashupbots.plebify.core.TaskExecutionRequest
import org.mashupbots.plebify.core.config.ConnectorConfig
import akka.actor.ActorRef
import akka.actor.Props
import org.mashupbots.plebify.core.StartRequest
import org.mashupbots.plebify.core.StartResponse

/**
 * Connector for HTTP events and tasks
 *
 * ==Events==
 *  - '''request-received''': HTTP request is received. See [[[org.mashupbots.plebify.http.HttpRequestRecievedEvent]]]
 *  - '''frame-received''': WebSocket text frame is received. See
 *    [[[org.mashupbots.plebify.http.HttpWebSocketRecievedEvent]]]
 *
 * ==Tasks==
 *  - '''send-request''': Send the data as the specified HTTP request.
 *    See [[[org.mashupbots.plebify.http.SendRequestTask]]].
 *  - '''send-frame''': Send the data as a web socket text frame.
 *    See [[[org.mashupbots.plebify.http.SendFrameTask]]].
 */
class HttpConnector(connectorConfig: ConnectorConfig) extends DefaultConnector {

  log.debug("HttpConnector created with {}", connectorConfig)

  override def onStart(msg: StartRequest): StartResponse = {
    // Start websocket servers (if any) so that clients can subscribe to it
    connectorConfig.params
      .filter { case (key, value) => key.startsWith("websocket-server") }
      .foreach {
        case (key, value) =>
          context.actorOf(Props(new WebsocketServer(value)), key)
      }

    StartResponse()
  }

  def instanceEventActor(req: EventSubscriptionRequest): ActorRef = {
    req.config.connectorEvent match {
      case HttpConnector.RequestReceived =>
        context.actorOf(Props(new RequestReceivedEvent(req)), name = createActorName(req.config))
      case HttpConnector.FrameReceived =>
        context.actorOf(Props(new FrameReceivedEvent(req)), name = createActorName(req.config))
      case x =>
        throw new Error(s"Unrecognised event ${req.config.connectorEvent}")
    }
  }

  def instanceTaskActor(req: TaskExecutionRequest): ActorRef = {
    req.config.connectorTask match {
      case HttpConnector.SendRequest =>
        context.actorOf(Props(new SendRequestTask(req.config)), createActorName(req.config))
      case HttpConnector.SendFrame =>
        context.actorOf(Props(new SendFrameTask(req.config)), createActorName(req.config))
      case x =>
        throw new Error(s"Unrecognised task ${req.config.connectorTask}")
    }
  }
}

/**
 * Companion object of HttpConnector class.
 */
object HttpConnector {

  val RequestReceived = "request-received"
  val FrameReceived = "frame-received"

  val SendRequest = "send-request"
  val SendFrame = "send-frame"
}