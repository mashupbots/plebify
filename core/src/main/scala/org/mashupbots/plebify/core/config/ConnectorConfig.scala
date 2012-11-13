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
 * @param params Parameters for the factory class
 */
case class ConnectorConfig(
  id: String,
  description: String,
  factoryClassName: String,
  params: Map[String, String]) extends Extension {

  /**
   * Read configuration from AKKA's `application.conf`
   *
   * @param id Unique id of this connector
   * @param config Configuration
   * @param keyPath Dot delimited key path to this connector configuration
   */
  def this(id: String, config: Config, keyPath: String) = this(
    id,
    ConfigUtil.getString(config, s"$keyPath.description", ""),
    config.getString(s"$keyPath.factory-class-name"),
    ConfigUtil.getParameters(config, keyPath, List("factory-class-name", "description")))

  /**
   * Name of the actor representing this connector
   */
  val actorName = ConnectorConfig.createActorName(id)

}

object ConnectorConfig {

  /**
   * Returns a unique name for a connector actor
   *
   * @param connectorId Connector id
   * @returns Unique name for a connector actor of the specified `id`.
   */
  def createActorName(connectorId: String): String = s"plebify-connector-$connectorId"
}