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

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import org.mashupbots.plebify.core.Connector
import org.mashupbots.plebify.core.EventSubscriptionRequest
import org.mashupbots.plebify.core.EventSubscriptionResponse
import org.mashupbots.plebify.core.EventUnsubscriptionRequest
import org.mashupbots.plebify.core.TaskExecutionRequest
import org.mashupbots.plebify.core.TaskExecutionResponse
import org.mashupbots.plebify.core.config.ConnectorConfig
import akka.actor.Actor
import akka.actor.PoisonPill
import akka.actor.Props
import akka.camel.CamelMessage
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout
import akka.actor.ActorRef
import org.mashupbots.plebify.core.StartRequest
import org.mashupbots.plebify.core.StartResponse

/**
 * Connector to the file system.
 *
 * ==Events==
 *  - '''created''': When the specified file is created. See [[[org.mashupbots.plebify.file.FileCreatedEvent]]]
 *
 * ==Tasks==
 *  - '''save''': Save data to the specified file. See [[[org.mashupbots.plebify.file.SaveFileTask]]].
 */
class FileConnector(connectorConfig: ConnectorConfig) extends Actor with akka.actor.ActorLogging with Connector {

  import context.dispatcher

  log.debug("FileConnector created with {}", connectorConfig)

  /**
   * Message processing
   */
  def receive = {
    case msg: StartRequest =>
      sender ! StartResponse()

    case req: EventSubscriptionRequest => {
      try {
        log.debug("{}", req)
        instanceEventActor(req)
        sender ! EventSubscriptionResponse()
      } catch {
        case ex: Throwable =>
          log.error(ex, "Error processing {}", req)
          sender ! new EventSubscriptionResponse(ex)
      }
    }

    case req: EventUnsubscriptionRequest => {
      log.debug("{}", req)
      val actorRef = context.actorFor(createActorName(req.config))
      actorRef ! PoisonPill
    }

    case req: TaskExecutionRequest => {
      try {
        log.debug("{}", req)

        // Create or get task actor
        val name = createActorName(req.config)
        val aa = context.actorFor(name)
        val taskActor = if (aa.isTerminated) instanceTaskActor(req) else aa

        // Extract the sender to prevent closure issues since future will be executed on a different thread  
        val replyTo = sender

        // Send request
        val future = taskActor.ask(req)(req.config.executionTimeout seconds).mapTo[CamelMessage]
        future.onComplete {
          case Success(m: CamelMessage) => replyTo ! TaskExecutionResponse()
          case Failure(ex: Throwable) =>
            log.error(ex, "Error in camel processing of {}", req)
            replyTo ! new TaskExecutionResponse(ex)
        }
      } catch {
        case ex: Throwable =>
          log.error(ex, "Error processing {}", req)
          sender ! new TaskExecutionResponse(ex)
      }

    }
  }

  private def instanceEventActor(req: EventSubscriptionRequest): ActorRef = {
    if (req.config.connectorEvent == FileConnector.FileCreatedEvent) {
      context.actorOf(Props(new FileCreatedEvent(req)), name = createActorName(req.config))
    } else {
      throw new Error(s"Unrecognised event ${req.config.connectorEvent}")
    }
  }

  private def instanceTaskActor(req: TaskExecutionRequest): ActorRef = {
    if (req.config.connectorTask == FileConnector.SaveFileTask) {
      context.actorOf(Props(new SaveFileTask(req.config)), createActorName(req.config))
    } else {
      throw new Error(s"Unrecognised task ${req.config.connectorTask}")
    }
  }
}

/**
 * Companion object of FileConnector class.
 */
object FileConnector {

  val FileCreatedEvent = "created"

  val SaveFileTask = "save"
}