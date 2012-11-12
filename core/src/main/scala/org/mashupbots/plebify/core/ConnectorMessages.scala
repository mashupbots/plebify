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

import akka.actor.ActorSystem
import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.config.EventConfig
import akka.actor.Actor
import org.mashupbots.plebify.core.config.TaskConfig
import org.mashupbots.plebify.core.config.EventConfig

/**
 * Trait to identify messages handled by a connector actor
 */
trait ConnectorMessage

/**
 * Registers a trigger for the specified job.
 * 
 * Upon successful registration, [[org.mashupbots.plebify.core.TriggerEvent]] message will be sent to the specified 
 * job every time a trigger fires.
 * 
 * To check if registration was successful or not, use futures.   
 * 
 * @param job Actor to which [[org.mashupbots.plebify.core.TriggerEvent]] will be sent as notification that 
 * @param config Trigger configuration
 */
case class TriggerRegistration(job: Actor, config: EventConfig) extends ConnectorMessage

/**
 * Deregisters the specified trigger for the specified job
 * 
 * Upon successful deregistration, [[org.mashupbots.plebify.core.TriggerEvent]] message will NOT be sent to the 
 * specified job every time a trigger fires.
 * 
 * To check if deregistration was successful or not, use futures.   
 * 
 * @param job Actor to which [[org.mashupbots.plebify.core.TriggerEvent]] will be sent as notification that 
 * @param config Trigger configuration
 */
case class TriggerDeregistration(job: Actor, config: EventConfig) extends ConnectorMessage

/**
 * Deregisters the specified trigger for the specified job
 * 
 * Upon successful deregistration, [[org.mashupbots.plebify.core.TriggerEvent]] message will NOT be sent to the 
 * specified job every time a trigger fires.
 * 
 * To check if deregistration was successful or not, use futures.   
 * 
 * @param jobId Identifier of the job to which this trigger event is to be sent
 * @param triggerId Identifier of the trigger which fired 
 * @param data Data associated with the event 
 */
case class TriggerEvent(jobId: String, triggerId: String, data: Map[String, String]) extends ConnectorMessage


case class Action
