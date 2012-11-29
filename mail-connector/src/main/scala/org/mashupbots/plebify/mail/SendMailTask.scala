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

import org.mashupbots.plebify.core.EventData
import org.mashupbots.plebify.core.TaskExecutionConfigReader
import org.mashupbots.plebify.core.TaskExecutionRequest
import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.config.TaskExecutionConfig

import akka.camel.CamelMessage
import akka.camel.Producer

/**
 * Send email task
 *
 * Sends an email to the specified address
 *
 * ==Parameters==
 *  - '''uri''': See [[http://camel.apache.org/mail.html Apache Camel mail component]] for options. You can also
 *    define defaults in the connector configuration and refer to them.
 *  - '''to''': The TO recipients (the receivers of the mail). Separate multiple email addresses with a comma.
 *  - '''from''': The FROM email address.
 *  - '''reply-to''': Optional Reply-To recipients (the receivers of the response mail).
 *    Separate multiple email addresses with a comma.
 *  - '''cc''': Optional CC recipients (the receivers of the mail). Separate multiple email addresses with a comma.
 *  - '''bcc''': Optional BCC recipients (the receivers of the mail). Separate multiple email addresses with a comma.
 *  - '''subject''': Optional subject of the email
 *  - '''template''': Optional template for the body. If not specified, the value of `Content` will
 *    be emailed.
 *
 * @param connectorConfig Connector configuration.
 * @param taskConfig Task configuration
 */
class SendMailTask(val connectorConfig: ConnectorConfig, val taskConfig: TaskExecutionConfig) extends Producer
  with TaskExecutionConfigReader with akka.actor.ActorLogging {

  def endpointUri: String = configValueFor("uri")
  require(endpointUri.startsWith("smtp"), "smtp needed to send email")

  val template = taskConfig.params.get("template")
  val headers = Map(
    ("to", configValueFor("to")),
    ("from", configValueFor("from")),
    ("replyTo", configValueFor("reply-to", "")),
    ("cc", configValueFor("cc", "")),
    ("bcc", configValueFor("bcc", "")),
    ("subject", configValueFor("subject", ""))).filter { case (k, v) => v.length > 0 }

  /**
   * Transforms TaskExecutionRequest into a CamelMessage
   */
  override def transformOutgoingMessage(msg: Any) = msg match {
    case msg: TaskExecutionRequest => {

      val body = if (template.isDefined) EventData.mergeTemplate(template.get, msg.eventNotification.data)
      else msg.eventNotification.data(EventData.Content)

      CamelMessage(body, headers)
    }
  }
}