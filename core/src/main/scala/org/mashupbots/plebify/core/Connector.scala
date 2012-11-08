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
import org.mashupbots.plebify.core.config.TriggerConfig
import akka.actor.Actor
import org.mashupbots.plebify.core.config.ActionConfig

/**
 * Base class for all connectors
 *
 * Decided to use an abstract class instead of a Trait because we wanted to pass
 * the parameters in on the constructor.
 *
 * @param actorSystem System in which trigger and action actors will be created
 * @param params Parameters from the configuration file
 */
abstract class Connector(val actorSystem: ActorSystem, val config: ConnectorConfig) {

  /**
   * Unique id of this connector
   */
  val id = config.id
  
  /**
   * Supported triggers
   */
  def triggers: Set[String]

  /**
   * Instance the specified trigger actor.
   * 
   * Trigger actors send [[org.mashupbots.plebify.core.TriggerMessage]] to the `job` actor when an event of interest
   * occurs.
   * 
   * @parameter job Job to which the new trigger belongs
   * @parameter config Trigger configuration
   * @returns Trigger actor
   */
  def triggerActor(job: Actor, config: TriggerConfig): Actor
  
  /**
   * Checks if a trigger id is valid
   *
   * @param id Id to check
   * @returns true if valid, false if invalid
   */
  def isValidTrigger(id: String): Boolean = triggers.contains(id)

  /**
   * Supported actions
   */
  def actions: Set[String]

  /**
   * Instance the specified action actor.
   * 
   * Action actors receive [[org.mashupbots.plebify.core.ActionRequestMessage]] when it is time to perform the action.
   * When the action has been completed, a [[org.mashupbots.plebify.core.ActionResponseMessage]] is returned to 
   * the caller.
   * 
   * The caller of an action is normally a job worker actor.
   * 
   * @parameter config Action configuration
   * @returns Action actor
   */
  def actionActor(config: ActionConfig): Actor

  /**
   * Checks if an action id is valid
   *
   * @param id Id to check
   * @returns true if valid, false if invalid
   */
  def isValidAction(id: String): Boolean = actions.contains(id)

}