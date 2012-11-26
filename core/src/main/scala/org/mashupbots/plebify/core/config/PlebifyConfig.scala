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
 * Plebify configuration file parser
 *
 * {{{
 *
 * plebify {
 *   connectors [
 *     {
 *       id = "file"
 *       description = ""
 *       factory-class-name = ""
 *       initialization-timeout = 30
 *       param1 = ""
 *     }
 *     {
 *       id = "http"
 *       description = ""
 *       factory-class-name = ""
 *       param1 = ""
 *       param2 = ""
 *     }]
 *
 *   jobs [
 *     {
 *       id = "job1"
 *       description = ""
 *       on [
 *         {
 *           connector-id = "http"
 *           connector-event = "request"
 *         }]
 *       do [
 *         {
 *           connector-id = "file"
 *           connector-task = "save"
 *         }
 *         {
 *           connector-id = "file"
 *           connector-task = "save"
 *         }]
 *     }]
 * }
 *
 * }}}
 */
case class PlebifyConfig(
  connectors: List[ConnectorConfig],
  jobs: List[JobConfig]) extends Extension {

  /**
   * Read configuration from AKKA's `application.conf`
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to this configuration
   */
  def this(config: Config, keyPath: String) = this(
    PlebifyConfig.loadConnectors(config, s"$keyPath.connectors"),
    PlebifyConfig.loadJobs(config, s"$keyPath.jobs"))

  /**
   * Validate this configuration
   */
  def validate() {
    require(!connectors.isEmpty, "No 'connectors' defined.")
    require(!jobs.isEmpty, "No 'jobs' defined.")

    connectors.foreach(c => c.validate())
    jobs.foreach(j => j.validate())

    // Check connector ids
    jobs.foreach(j => {
      j.events.foreach(e => {
        if (!connectors.exists(c => c.id == e.connectorId))
          throw new Error(s"Connector id '${e.connectorId}' in ${e.name} does not exist.")
      })

      j.tasks.foreach(t => {
        if (!connectors.exists(c => c.id == t.connectorId))
          throw new Error(s"Connector id '${t.connectorId}' in ${t.name} does not exist.")
      })
    })
  }

  validate();
}

object PlebifyConfig {

  /**
   * Load connectors from the configuration file
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to the connectors configuration
   * @returns List of [[org.mashupbots.plebify.core.config.ConnectorConfig]]
   */
  def loadConnectors(config: Config, keyPath: String): List[ConnectorConfig] = {
    val connectors = config.getObjectList(keyPath)
    (for (c <- connectors)
      yield new ConnectorConfig(c.toConfig())).toList
  }

  /**
   * Load jobs from the configuration file
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to the jobs configuration
   * @returns List of [[org.mashupbots.plebify.core.config.JobConfig]]
   */
  def loadJobs(config: Config, keyPath: String): List[JobConfig] = {
    val jobs = config.getObjectList(keyPath)
    (for (j <- jobs)
      yield new JobConfig(j.toConfig())).toList
  }

}