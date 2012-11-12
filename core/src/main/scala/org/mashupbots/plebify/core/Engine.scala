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

import org.mashupbots.plebify.core.config.PlebifyConfig

import akka.actor.Actor
import akka.actor.ExtendedActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider

/**
 * The engine manages connectors and jobs.
 */
class Engine extends Actor {

  /**
   * Plebify configuration
   */
  val config: PlebifyConfig = EngineConfigReader(context.system)
  
  def receive = {
    case x =>
  }
  

  //  /**
  //   * Plebify configuration
  //   */
  //  val config: PlebifyConfig = EngineConfigReader(context)
  //
  //  val connectors: Map[String, Connector] = {
  //
  //    def instanceConnector(connectorConfig: ConnectorConfig): Connector = {
  //      val classLoader = Thread.currentThread.getContextClassLoader
  //      val clazz = classLoader.loadClass(connectorConfig.className)
  //      val constructor = clazz.getConstructor(classOf[ActorSystem], classOf[ConnectorConfig])
  //      constructor.newInstance(system, connectorConfig).asInstanceOf[Connector]
  //    }
  //
  //    config.connectors.map { case (k, v) => (k, instanceConnector(v)) }
  //  }
  //  
  //  /**
  //   * Stops this engine.
  //   *
  //   * Terminates kills all connector and job actors.
  //   */
  //  def shutdown() {
  //
  //  }
}

/**
 * Extracts plebify configuration from an actor system. The plebify configuration must be under a root node named
 * `plebify`.
 *
 * See [[org.mashupbots.plebify.core.config.PlebifyConfig]].
 */
object EngineConfigReader extends ExtensionId[PlebifyConfig] with ExtensionIdProvider {
  override def lookup = EngineConfigReader
  override def createExtension(system: ExtendedActorSystem) = new PlebifyConfig(system.settings.config, "plebify")
}