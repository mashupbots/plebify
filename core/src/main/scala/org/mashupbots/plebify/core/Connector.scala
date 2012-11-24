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

import scala.Some.apply
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.config.EventSubscriptionConfig
import org.mashupbots.plebify.core.config.TaskExecutionConfig

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.camel.CamelMessage
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout

/**
 * A connector provides events and executes tasks
 */
trait Connector {

  /**
   * Creates a unique actor name for a given event subscription
   */
  def createActorName(config: EventSubscriptionConfig): String = {
    s"${config.connectorId}-${config.connectorEvent}-${config.jobId}-${config.index}"
  }

  /**
   * Creates a unique actor name for a given task
   */
  def createActorName(config: TaskExecutionConfig): String = {
    s"${config.connectorId}-${config.connectorTask}-${config.jobId}-${config.index}"
  }

}

/**
 * Default connector
 *
 * Connector that instances child actors to process event subscription and task executions
 */
trait DefaultConnector extends Actor with akka.actor.ActorLogging with Connector {

  import context.dispatcher

  /**
   * Flag to determine if we should kill the task actor upon error.  If true, a `PoisoinPill` is sent to the
   * task actor if an error is received. It will be started again upon the next request.
   *
   * Defaults to `true`.
   *
   * We have found that an `AkkaCamelException` is thrown by Akka's Producer if there is an error.  This causes the
   * producer actor to stop and restart. However, restarting somehow does not work properly because messages are not
   * being sent.  This behaviour is present for camel-jetty
   */
  def killTaskActorOnFailure: Boolean = false

  /**
   * Message processing
   */
  final def receive = {
    case msg: StartRequest =>
      sender ! onStart(msg)

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
            if (killTaskActorOnFailure) taskActor ! PoisonPill
            replyTo ! new TaskExecutionResponse(ex)
        }
      } catch {
        case ex: Throwable =>
          log.error(ex, "Error processing {}", req)
          sender ! new TaskExecutionResponse(ex)
      }
    }

  }

  /**
   * Startup processing
   *
   * Override this method to execute your own startup processing
   *
   * @param message to process
   * @returns Start response to return to the sender
   */
  def onStart(msg: StartRequest): StartResponse = {
    StartResponse()
  }

  /**
   * Instance an event actor to process a subscription request
   *
   * @param req Request to process
   * @returns New event actor
   */
  def instanceEventActor(req: EventSubscriptionRequest): ActorRef

  /**
   * Instance a task actor to process a task execution request
   *
   * @param req Request to process
   * @returns New task actor
   */
  def instanceTaskActor(req: TaskExecutionRequest): ActorRef

}

/**
 * Trait to identify messages sent to/from connectors
 */
trait ConnectorMessage

/**
 * Subscribes the job to the specified event
 *
 * Upon successful subscription, [[org.mashupbots.plebify.core.EventNotification]] messages will be sent to the
 * caller every time an event fires.
 *
 * The success or failure of subscription is returned to the sender in
 * [[org.mashupbots.plebify.core.EventSubscriptionResponse]].
 *
 * @param config Event subscription configuration
 * @param job Job that is subscribing. We cannot rely on the `sender` because sending this message with a future causes
 *   the `sender` to be a temporary actor.
 */
case class EventSubscriptionRequest(
  config: EventSubscriptionConfig,
  job: ActorRef) extends ConnectorMessage with RequestMessage

/**
 * The result of an event subscription.
 *
 * This message is sent from a connector to the job that sent the
 * [[org.mashupbots.plebify.core.EventSubscriptionRequest]] message.
 *
 * @param errorMessage Error message
 * @param error Optional error
 */
case class EventSubscriptionResponse(errorMessage: String = "", error: Option[Throwable] = None)
  extends ConnectorMessage with ResponseMessage {

  def this(ex: Throwable) = this(ex.getMessage, Some(ex))
}

/**
 * Cancels a job's subscription to the specified event
 *
 * Upon successful unsubscription, [[org.mashupbots.plebify.core.EventNotification]] messages will '''NOT''' be sent to
 * the caller every time an event fires.
 *
 * The success or failure of cancellation is returned to the sender in
 * [[org.mashupbots.plebify.core.EventUnsubscriptionResponse]].
 *
 * @param config Event subscription configuration
 * @param job Job that is subscribing. We cannot rely on the `sender` because sending this message with a future causes
 *   the `sender` to be a temporary actor.
 */
case class EventUnsubscriptionRequest(
  config: EventSubscriptionConfig,
  job: ActorRef) extends ConnectorMessage with RequestMessage

/**
 * The result of a cancellation of an event subscription.
 *
 * This message is sent from a connector to the job that sent the
 * [[org.mashupbots.plebify.core.EventUnsubscriptionRequest]] message.
 *
 * @param errorMessage Error message
 * @param error Optional error
 */
case class EventUnsubscriptionResponse(errorMessage: String = "", error: Option[Throwable] = None)
  extends ConnectorMessage with ResponseMessage {

  def this(ex: Throwable) = this(ex.getMessage, Some(ex))
}

/**
 * Notification that an event has occurred.
 *
 * This message is sent from a connector to a job that has subscribed to the event on the connector.
 *
 * @param config Configuration of this event subscription
 * @param data Data associated with the event
 */
case class EventNotification(config: EventSubscriptionConfig, data: Map[String, String]) extends ConnectorMessage
  with NotificationMessage

/**
 * Executes the specified task
 *
 * This message is sent form a [[org.mashupbots.plebify.core.JobWorker]] to a connector. The success or failure of
 * execution is returned to the job in [[org.mashupbots.plebify.core.TaskExecutionResponse]].
 *
 * @param jobId Id of the job
 * @param config Task execution configuration detailing how the task is to be run
 * @param eventNotification Message that triggered this request
 */
case class TaskExecutionRequest(
  config: TaskExecutionConfig,
  eventNotification: EventNotification) extends ConnectorMessage with RequestMessage

/**
 * The result of executing a task.
 *
 * This message is sent from a connector to the [[org.mashupbots.plebify.core.JobWorker]] that sent the
 * [[org.mashupbots.plebify.core.TaskExecutionRequest]] message.
 *
 * @param data Response data for incorporating into data
 * @param errorMessage Error message
 * @param error Optional error
 */
case class TaskExecutionResponse(
  data: Map[String, String] = Map.empty,
  errorMessage: String = "",
  error: Option[Throwable] = None)
  extends ConnectorMessage with ResponseMessage {

  def this(ex: Throwable) = this(Map.empty, ex.getMessage, Some(ex))
}

