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

import org.mashupbots.plebify.core.TaskExecutionConfigReader
import org.mashupbots.plebify.core.TaskExecutionRequest
import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.config.TaskExecutionConfig

import akka.actor.Actor

/**
 * Starts a websocket server to which clients can subscribe to events
 *
 * ==Parameters==
 *  - '''websocket-server''': Name of the websocket server as specified in the connector config
 *  - '''template''': Optional template for the data to send. If not specified, value of `Content` will be sent.
 *
 *
 * @param connectorConfig Connector configuration.
 * @param taskConfig Task configuration
 */
class SendFrameTask(val connectorConfig: ConnectorConfig, val taskConfig: TaskExecutionConfig) extends Actor 
  with TaskExecutionConfigReader with akka.actor.ActorLogging {

  def receive = {
    case msg: TaskExecutionRequest => {
      val ws = context.actorFor("../" + configValueFor("websocket-server"))
      ws.forward(msg)
    }
  }
  
}