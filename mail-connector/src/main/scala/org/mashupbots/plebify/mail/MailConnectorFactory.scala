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

import org.mashupbots.plebify.core.ConnectorFactory
import akka.actor.ActorContext
import org.mashupbots.plebify.core.config.ConnectorConfig
import akka.actor.ActorRef
import akka.actor.Props

/**
 * Instances the [[org.mashupbots.plebify.mail.MailConnector]] actor
 */
class MailConnectorFactory extends ConnectorFactory {
  def create(context: ActorContext, connectorConfig: ConnectorConfig): ActorRef = {
    context.actorOf(Props(new MailConnector(connectorConfig)), name = connectorConfig.actorName)
  }
}