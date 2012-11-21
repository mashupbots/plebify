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

import org.mashupbots.plebify.core.config.EventSubscriptionConfig
import akka.actor.ActorRef
import org.mashupbots.plebify.core.config.TaskExecutionConfig
import akka.actor.Actor
import akka.actor.ActorSystem
import org.mashupbots.plebify.core.config.ConnectorConfig
import akka.actor.ActorContext
import org.mashupbots.plebify.core.config.EventSubscriptionConfig

abstract class ConnectorFactory() {

  /**
   * Instances a new connector actor
   *
   * The [[org.mashupbots.plebify.core.Engine]] will call instance and call this factory class to create a new
   * instance in the specified actor system.
   *
   * The actor must be created using the supplied `context` so it can be managed and named using
   * `connectorConfig.actorName`.
   *
   * After instancing, [[org.mashupbots.plebify.core.Engine]] will send a [[org.mashupbots.plebify.core.StartRequest]]
   * message to the actor.  [[org.mashupbots.plebify.core.StartResponse]] is expected in reply when the actor is
   * ready to process [[org.mashupbots.plebify.core.ConnectorMessage]]s.
   *
   * When stopping, the [[org.mashupbots.plebify.core.Engine]] will send a [[org.mashupbots.plebify.core.StopRequest]]
   * message. [[org.mashupbots.plebify.core.StopResponse]] is expected in reply.
   *
   * @param context Engine actor context
   * @param connectorConfig configuration for this connector
   */
  def create(context: ActorContext, connectorConfig: ConnectorConfig): ActorRef
}

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
 * @param errorMessage Error message
 * @param error Optional error
 */
case class TaskExecutionResponse(errorMessage: String = "", error: Option[Throwable] = None)
  extends ConnectorMessage with ResponseMessage {

  def this(ex: Throwable) = this(ex.getMessage, Some(ex))
}

