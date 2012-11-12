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
 *   connectors {
 *     file {
 *       factory-class = ""
 *       param1 = ""
 *     }
 *
 *     http {
 *       factory-class = ""
 *       param1 = ""
 *       param2 = ""
 *     }
 *   }
 *
 *   jobs {
 *     job1 {
 *       description = ""
 *
 *       on {
 *         http-request {
 *           endpoint = "endpoint1"
 *         }
 *       }
 *
 *       do {
 *         file-save-1 {
 *           endpoint = "endpoint1"
 *         }
 *
 *         file-save-2 {
 *           endpoint = "endpoint2"
 *         }
 *       }
 *     }
 *   }
 * }
 *
 * }}}
 */
case class PlebifyConfig(
  connectors: Map[String, ConnectorConfig],
  jobs: Map[String, JobConfig]) extends Extension {

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
   * Validate the configuration
   */
  private def validate() {
    // Make sure we have connectors defined
    require(connectors.size > 0, "No 'connectors' defined.")

    // Make sure we have jobs defined
    require(jobs.size > 0, "No 'jobs' defined.")
  }

  validate();

}

object PlebifyConfig {

  /**
   * Load connectors from the configuration file
   *
   * Note that connector id forms the key. This implicitly means that a connector id must be unique
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to the connectors configuration
   */
  def loadConnectors(config: Config, keyPath: String): Map[String, ConnectorConfig] = {
    val connectors = config.getObject(keyPath)
    (for (id <- connectors.keySet())
      yield (id, new ConnectorConfig(id, config, s"$keyPath.$id"))).toMap
  }

  /**
   * Load jobs from the configuration file
   *
   * Note that job id forms the key. This implicitly means that a job id must be unique
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to the jobs configuration
   */
  def loadJobs(config: Config, keyPath: String): Map[String, JobConfig] = {
    val jobs = config.getObject(keyPath)
    (for (id <- jobs.keySet())
      yield (id, new JobConfig(id, config, s"$keyPath.$id"))).toMap
  }

}