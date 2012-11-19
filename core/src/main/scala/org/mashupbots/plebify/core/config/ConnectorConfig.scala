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
 * Connector configuration
 *
 * @param id Unique id of this connector
 * @param factoryClassName Full class path to factory class that will instance our actor
 * @param initializationTimeout Number of seconds the engine will wait for the
 *  [[org.mashupbots.plebify.core.StartResponse]] message after sending the [[org.mashupbots.plebify.core.StartRequest]]
 * @param params Parameters for the factory class
 */
case class ConnectorConfig(
  id: String,
  description: String,
  factoryClassName: String,
  initializationTimeout: Int,
  params: Map[String, String]) extends Extension {

  /**
   * Read configuration from AKKA's `application.conf`.
   * 
   * Defaults:
   *  - `description` = empty string.
   *  - `initialization-timeout` = 5 seconds.
   *
   * @param config Configuration
   */
  def this(config: Config) = this(
    config.getString("connector-id"),
    ConfigUtil.getString(config, "description", ""),
    config.getString("factory-class-name"),
    ConfigUtil.getInt(config, "initialization-timeout", 5),
    ConfigUtil.getParameters(config, 
        List("connector-id", "factory-class-name", "description", "initialization-timeout")))

  /**
   * Name of the actor representing this connector
   */
  val actorName = ConnectorConfig.createActorName(id)
  
  /**
   * Validate this configuration
   */
  def validate() {
    require(!id.isEmpty, "Connector Id must contain a value")
    require(!factoryClassName.isEmpty, "Id must contain a value")
    require(initializationTimeout > 0, "initialization-timeout must be greater than 0")
  }  
}

object ConnectorConfig {

  /**
   * Returns a unique name for a connector actor
   *
   * @param connectorId Connector id
   * @returns Unique name for a connector actor of the specified `id`.
   */
  def createActorName(connectorId: String): String = s"connector-$connectorId"
}