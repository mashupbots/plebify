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
package org.mashupbots.plebify.core

import akka.actor.Actor
import org.mashupbots.plebify.core.config.JobConfig
import org.mashupbots.plebify.core.config.ConnectorConfig
import akka.pattern.ask
import scala.concurrent.duration._
import scala.util.Success
import scala.util.Failure
import akka.actor.Props
import java.util.UUID

/**
 * A job waits for connector events to fire and executing tasks.
 *
 * When you instance a job, it starts.  When you send a `PoisonPill` or `Kill` message, it stops.
 *
 * Upon starting, a job will subscribe to events by sending a [[org.mashupbots.plebify.core.EventSubsription]] to the
 * specified connector.
 *
 * When the event fires, the job will receive a [[org.mashupbots.plebify.core.EventNotification]] message.  This will
 * trigger the job to instance a [[org.mashupbots.plebify.core.JobWorker]] to execute the specified task(s).
 */
class Job(val config: JobConfig) extends Actor with akka.actor.ActorLogging {
  import context.dispatcher

  /**
   * Start connectors and jobs
   */
  override def preStart() {
    log.info(s"Starting job '${config.id}' as actor '${context.self.path.toString}'")

    config.events.foreach(eventConfig => {

      val connectorActorName = ConnectorConfig.createActorName(eventConfig.connectorId)
      val connectorActorRef = context.actorFor(s"../$connectorActorName")
      if (connectorActorRef.isTerminated) {
        throw new Error(s"Connector $connectorActorName is not running!")
      }

      val msg = s"Error subscribing to connector '${eventConfig.connectorId}' event '${eventConfig.eventName}' " +
        s"for job '${config.id}'."
      val future = connectorActorRef.ask(EventSubscription(context.self, eventConfig))(5 seconds)
      future.onComplete {
        case Success(result: EventSubscriptionResult) =>
          if (!result.isSuccess) {
            log.error(result.error.get, msg)
            throw new Error(msg, result.error.get)
          }
        case Failure(e: Throwable) => {
          log.error(e, msg)
          throw new Error(msg, e)
        }
        case x => {
          log.error(msg + x.toString)
          throw new Error(msg)
        }
      }

    })

  }

  /**
   * In restarting the engine after an error, stop all children and ourself.
   */
  override def preRestart(reason: Throwable, message: Option[Any]) {
    try {
      postStop()
    } finally {
      // Just in case we cannot gracefully stop the children, kill them!
      context.children.foreach(context.stop(_))
    }
  }

  /**
   * After stopping all children and ourself in `preRestart`, start up again.
   */
  override def postRestart(reason: Throwable) {
    preStart()
  }

  /**
   * Stop connectors and jobs
   */
  override def postStop() {
    log.info(s"Stopping job '${config.id}'")

    config.events.foreach(eventConfig => {
      val connectorActorName = ConnectorConfig.createActorName(eventConfig.connectorId)
      val connectorActorRef = context.actorFor(s"../$connectorActorName")
      if (!connectorActorRef.isTerminated) {
        val msg = s"Error cancelling subscription to connector '${eventConfig.connectorId}' event '${eventConfig.eventName}' " +
          s"for job '${config.id}'."
        val future = connectorActorRef.ask(EventSubscriptionCancellation(context.self, eventConfig))(5 seconds)
        future.onComplete {
          case Success(result: EventSubscriptionCancellationResult) =>
            if (!result.isSuccess) {
              log.error(result.error.get, msg)
              throw new Error(msg, result.error.get)
            }
          case Failure(e: Throwable) => {
            log.error(e, msg)
            throw new Error(msg, e)
          }
          case x => {
            log.error(msg + x.toString)
            throw new Error(msg)
          }
        }
      }
    })
  }

  /**
   * Incoming message processing
   */
  def receive = {
    // When we get an event notification, create a JobWork to do execute tasks
    case event: EventNotification => {
      val jobWorker = context.system.actorOf(Props[JobWorker], name = s"plebify-jobworker-${config.id}-" +
        UUID.randomUUID())
    }
  }
}

