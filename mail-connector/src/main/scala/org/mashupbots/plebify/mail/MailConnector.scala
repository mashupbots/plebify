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

import org.mashupbots.plebify.core.DefaultConnector
import org.mashupbots.plebify.core.EventSubscriptionRequest
import org.mashupbots.plebify.core.TaskExecutionRequest
import org.mashupbots.plebify.core.config.ConnectorConfig

import akka.actor.ActorRef
import akka.actor.Props

/**
 * Connector to email
 *
 * ==Events==
 *  - '''received''': When new email arrives. See [[[org.mashupbots.plebify.mail.MailReceivedEvent]]]
 *
 * ==Tasks==
 *  - '''send''': Sends an email. See [[[org.mashupbots.plebify.mail.SendMailTask]]].
 */
class MailConnector(connectorConfig: ConnectorConfig) extends DefaultConnector {

  log.debug("MailConnector created with {}", connectorConfig)

  def instanceEventActor(req: EventSubscriptionRequest): ActorRef = {
    req.config.connectorEvent match {
      case MailConnector.MailReceivedEvent =>
        context.actorOf(Props(new MailReceivedEvent(connectorConfig, req)), name = createActorName(req.config))
      case unknown =>
        throw new Error(s"Unrecognised event $unknown")
    }
  }

  def instanceTaskActor(req: TaskExecutionRequest): ActorRef = {
    req.config.connectorTask match {
      case MailConnector.SendMailTask =>
        context.actorOf(Props(new SendMailTask(connectorConfig, req.config)), createActorName(req.config))
      case unknown =>
        throw new Error(s"Unrecognised task $unknown")
    }
  }
}

/**
 * Companion object of FileConnector class.
 */
object MailConnector {

  val MailReceivedEvent = "received"

  val SendMailTask = "send"
}