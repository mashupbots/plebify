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

/**
 * Trait to identify connectors
 */
trait Connector

/**
 * Trait to identify messages sent to/from connectors
 */
trait ConnectorMessage

/**
 * Subscribes the job to the specified event
 *
 * Upon successful subscription, [[org.mashupbots.plebify.core.EventNotification]] messages will be sent to the
 * specified job every time an event fires.
 * 
 * The success or failure of subscription is returned to the sender in 
 * [[org.mashupbots.plebify.core.EventSubsriptionResult]]. 
 *
 * @param job Actor to which [[org.mashupbots.plebify.core.EventNotification]] will be sent when the event fires
 * @param config Event subscription configuration
 */
case class EventSubsription(job: ActorRef, config: EventSubscriptionConfig) extends ConnectorMessage

/**
 * The result of an event subscription.
 * 
 * This message is sent from a connector to the job that sent the [[org.mashupbots.plebify.core.EventSubsription]]
 *
 * @param error Optional error
 */
case class EventSubsriptionResult(error: Option[Throwable] = None) extends ConnectorMessage with ResultMessage

/**
 * Notification that an event has occurred. 
 * 
 * This message is sent from a connector to a job that has subscribed to the event on the connector. 
 * 
 * @param eventSubscriptionId Identifier of the event
 * @param data Data associated with the event
 */
case class EventNotification(eventSubscriptionId: String, data: Map[String, String]) extends ConnectorMessage

/**
 * Executes the specified task
 *
 * This message is sent form a job to 
 * The success or failure of execution is returned to the sender in [[org.mashupbots.plebify.core.TaskExecutionResult]]. 
 *
 * @param job Actor to which [[org.mashupbots.plebify.core.EventNotification]] will be sent when the event fires
 * @param config Task execution configuration detailing how the task is to be run
 */
case class TaskExecution(job: ActorRef, config: TaskExecutionConfig) extends ConnectorMessage

/**
 * The result of running a task
 * 
 * This message is sent from a connector to the job that sent the [[org.mashupbots.plebify.core.TaskExecution]]
 *
 * @param error Optional error
 */
case class TaskExecutionResult(error: Option[Throwable] = None) extends ConnectorMessage with ResultMessage

