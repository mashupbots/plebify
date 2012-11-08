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
import scala.collection.JavaConversions._

/**
 * Job configuration
 *
 * @param id Unique id of this job
 * @param description Description of this job
 * @param triggers Collection of triggers that will cause this job to run
 * @param actions Work to be performed by this job
 */
case class JobConfig(
  id: String,
  description: String,
  triggers: Map[String, TriggerConfig],
  actions: Map[String, ActionConfig]) extends Extension {

  /**
   * Read configuration from AKKA's `application.conf`
   *
   * @param id Unique id of this job
   * @param config Configuration
   * @param keyPath Dot delimited key path to this connector configuration
   */
  def this(id: String, config: Config, keyPath: String) = this(
    id,
    ConfigUtil.getString(config, s"$keyPath.description", ""),
    JobConfig.loadTriggers(config, s"$keyPath.on"),
    JobConfig.loadActions(config, s"$keyPath.do"))

}

object JobConfig {

  /**
   * Load triggers from the configuration file
   *
   * Note that trigger id forms the key. This implicitly means that a trigger id must be unique
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to the trigger configuration
   */
  def loadTriggers(config: Config, keyPath: String): Map[String, TriggerConfig] = {
    val triggers = config.getObject(keyPath)
    (for (id <- triggers.keySet())
      yield (id, new TriggerConfig(id, config, s"$keyPath.$id"))).toMap
  }

  /**
   * Load actions from the configuration file
   *
   * Note that action id forms the key. This implicitly means that an action id must be unique
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to the action configuration
   */
  def loadActions(config: Config, keyPath: String): Map[String, ActionConfig] = {
    val actions = config.getObject(keyPath)
    (for (id <- actions.keySet())
      yield (id, new ActionConfig(id, config, s"$keyPath.$id"))).toMap
  }

}