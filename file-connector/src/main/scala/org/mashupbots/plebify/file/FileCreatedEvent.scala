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
package org.mashupbots.plebify.file

import java.util.Date

import scala.collection.immutable.List.apply

import org.mashupbots.plebify.core.EventData
import org.mashupbots.plebify.core.EventNotification
import org.mashupbots.plebify.core.EventSubscriptionRequest

import akka.camel.CamelMessage
import akka.camel.Consumer

/**
 * File created event.
 *
 * Looks for a file or files in a directory as specified by the `uri` configuration parameter.
 * When one is found, an event is triggered.
 *
 * ==Parameters==
 *  - '''uri''': See [[http://camel.apache.org/file2.html Apache Camel file component]] for options.
 *
 * ==Event Data==
 *  - '''Date''': Timestamp when event occurred
 *  - '''Content''': Contents of the file
 *  - '''Content-Length''': Length of the file
 *  - '''Last-Modified''': When the file was last changed
 *  - '''File-Name''': Name of file without path
 *  - '''Absolute-File-Name''': Full path to the file
 *
 * @param request Subscription request
 */
class FileCreatedEvent(request: EventSubscriptionRequest) extends Consumer with akka.actor.ActorLogging {

  def endpointUri = request.config.params("uri")

  def receive = {
    case msg: CamelMessage =>
      try {
        val fileName = msg.headers("CamelFileNameOnly").toString
        val contentLength = msg.headers("CamelFileLength").toString
        val lastModified = EventData.dateTimeToString(msg.headers("CamelFileLastModified").asInstanceOf[Date])
        val content = msg.bodyAs[String]
        val data = List(
          (EventData.Date, EventData.dateTimeToString(new Date())),
          (EventData.Content, content),
          (EventData.ContentLength, contentLength),
          (EventData.LastModified, lastModified),
          ("FileName", fileName)).toMap

        request.job ! EventNotification(request.config, data)
      } catch {
        case ex: Throwable =>
          log.error(ex, "Error processing {}", msg)
      }
  }
}