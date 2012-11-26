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
package org.mashupbots.plebify.db

import org.mashupbots.plebify.core.EventData
import org.mashupbots.plebify.core.TaskExecutionRequest
import org.mashupbots.plebify.core.config.TaskExecutionConfig

import akka.camel.CamelMessage
import akka.camel.Producer

/**
 * Executes a SQL insert or update command
 *
 * ==Parameters==
 *  - '''datasource''': Name of datasource as specified in the connector config
 *  - '''sql''': SQL statement to execute
 *  - '''max-rows''': Optional maximum number of rows to be returned if this is a query query.
 *    Defaults to `0` if not supplied.
 *
 * ==Event Data==
 *  - '''Content''': to replace the placeholder in the sql statement
 *
 * @param config Task configuration
 */
class ExecuteSqlTask(config: TaskExecutionConfig) extends Producer with akka.actor.ActorLogging {

  val sqlTemplate = config.params("sql")
  val datasource = config.params("datasource")
  val maxRows = config.params.getOrElse("max-rows", "100")

  def endpointUri: String = config.params(s"jdbc:${datasource}?readSize=${maxRows}")

  /**
   * Transforms [[org.mashupbots.plebify.core.TaskExecutionRequest]] into a CamelMessage
   */
  override def transformOutgoingMessage(msg: Any) = msg match {
    case msg: TaskExecutionRequest => {

      val sqlStatement = sqlTemplate.replace("{{content}}", msg.eventNotification.data(EventData.Content))
      CamelMessage(sqlStatement, Map.empty)
    }
  }
}