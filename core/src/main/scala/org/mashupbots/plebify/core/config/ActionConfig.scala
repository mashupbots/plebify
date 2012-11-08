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
package org.mashupbots.plebify.core.config

import akka.actor.Extension
import com.typesafe.config.Config

/**
 * Action configuration
 *
 * An action is perform when an event sets off a trigger
 *
 * @param id Unique id of this trigger. Must be in the format `{connector id}-{action}[-optional-text]`.
 * @param description Description of this action
 * @param params Parameters for this action
 */
case class ActionConfig(
  id: String,
  description: String,
  params: Map[String, String]) extends Extension {

  /**
   * Read configuration from AKKA's `application.conf`
   *
   * @param id Unique identifier of this action. Must be in the format `{connector id}-{action}[-optional-text]`.
   * @param config Configuration
   * @param keyPath Dot delimited key path to this action configuration
   */
  def this(id: String, config: Config, keyPath: String) = this(
    id,
    ConfigUtil.getString(config, s"$keyPath.description", ""),
    ConfigUtil.getParameters(config, keyPath, List("description")))

  private val splitId = id.split("-")
  require(splitId.length >= 2, s"Action id '$id' must be in the format 'connector-action'")

  /**
   * Id of the connector that will perform the action
   */
  val connectorId = splitId(0)

  /**
   * Id of the action in the connector to perform
   */
  val actionId = splitId(1)

}
