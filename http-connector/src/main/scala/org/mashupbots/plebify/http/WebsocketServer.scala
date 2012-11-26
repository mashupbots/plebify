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

import org.mashupbots.plebify.core.EventData
import org.mashupbots.plebify.core.TaskExecutionRequest
import org.mashupbots.plebify.core.config.TaskExecutionConfig
import akka.camel.CamelMessage
import akka.camel.Oneway
import akka.camel.Producer
import org.apache.camel.Exchange
import akka.camel.CamelMessage
import scala.concurrent.Await
import scala.concurrent.duration._
import org.apache.camel.component.websocket.WebsocketConstants
/**
 * A websocket server to which clients can subscribe to events.
 * 
 * This is started by the [[org.mashupbots.plebify.http.HttpConnector]] at startup and represents a websocket
 * provider.
 * 
 * We don't start up a websocket provider within a task because it then requires the task to be executed before the
 * server is started.
 *
 * @param endpointUri Camel websocket URI
 */
class WebsocketServer(val endpointUri: String) extends Producer with akka.actor.ActorLogging {

  require(endpointUri.startsWith("websocket:"), s"$endpointUri must start with 'websocket:'")

  /**
   * Transforms TaskExecutionRequest into a CamelMessage
   */
  override def transformOutgoingMessage(msg: Any) = msg match {
    case msg: TaskExecutionRequest => {
      log.info("{}", msg)
      val header = Map((WebsocketConstants.SEND_TO_ALL, "true"))
      val content = msg.eventNotification.data.getOrElse(EventData.Content, "")
      CamelMessage(content, header)
    }
    case m => log.debug("Unexpected message {}", m)
  }
}