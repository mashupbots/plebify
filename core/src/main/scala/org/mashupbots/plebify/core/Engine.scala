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
import akka.event.Logging
import org.mashupbots.plebify.core.config.ConnectorConfig
import akka.actor.Props
import akka.actor.PoisonPill

/**
 * The engine manages connectors and jobs.
 */
class Engine extends Actor with akka.actor.ActorLogging {

  /**
   * Plebify configuration
   */
  val config: PlebifyConfig = EngineConfigReader(context.system)

  /**
   * Start connectors and jobs
   */
  override def preStart() {
    log.info("Plebify Engine starting...")

    // Start connectors
    config.connectors.foreach {
      case (id, connectorConfig) => {
        log.info(s"  Starting connector '$id'")

        // Try to instance with config argument. If class does not take config parameter, default constructor
        // will be called
        val clazz = Class.forName(connectorConfig.className)
        val instance: Actor = {
          try {
            val constructor = clazz.getConstructor(classOf[ConnectorConfig])
            constructor.newInstance(connectorConfig).asInstanceOf[Actor]
          } catch {
            case _: NoSuchMethodException => clazz.newInstance().asInstanceOf[Actor]
          }
        }
        val connectorActor = context.system.actorOf(Props(instance), name = connectorConfig.actorName)
      }
    }

    // Start jobs
    config.jobs.foreach {
      case (id, jobConfig) => {
        log.info(s"  Starting job '$id'")
        val jobActor = context.system.actorOf(Props(new Job(jobConfig)), name = jobConfig.actorName)
      }
    }
  }

  /**
   * In restarting the engine after an error, stop all children and ourself.
   */
  override def preRestart(reason: Throwable, message: Option[Any]) {
    postStop()

    // Just incase we cannot gracefully stop any actors, kill them
    context.children.foreach(context.stop(_))
  }

  /**
   * After stopping all children and ourself in `preRestart`, start up again.
   */
  override def postRestart(reason: Throwable) {
    preStart()
  }

  /**
   * Stop connectors and jobs
   */
  override def postStop() {
    log.info("Plebify Engine stopping...")

    // Stop jobs
    config.jobs.foreach {
      case (id, jobConfig) => {
        context.actorFor(jobConfig.actorName) ! PoisonPill
      }
    }

    // Stop connectors
    config.connectors.foreach {
      case (id, connectorConfig) => {
        context.actorFor(connectorConfig.actorName) ! PoisonPill
      }
    }
  }

  /**
   * The engine does not handle any messages.
   */
  def receive = {
    case x => throw new Error(s"Unrecognised message${x.toString}")
  }

}

/**
 * Extracts Plebify configuration from an actor system. The Plebify configuration must be under a root node named
 * `plebify`.
 *
 * See [[org.mashupbots.plebify.core.config.PlebifyConfig]].
 */
object EngineConfigReader extends ExtensionId[PlebifyConfig] with ExtensionIdProvider {
  override def lookup = EngineConfigReader
  override def createExtension(system: ExtendedActorSystem) = new PlebifyConfig(system.settings.config, "plebify")
}
