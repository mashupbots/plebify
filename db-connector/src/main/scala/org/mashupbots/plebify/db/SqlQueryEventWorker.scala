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

import org.mashupbots.plebify.core.EventSubscriptionRequest
import akka.camel.Producer
import org.mashupbots.plebify.core.EventSubscriptionConfigReader
import org.mashupbots.plebify.core.config.ConnectorConfig

/**
 * Performs the running of the query for [[org.mashupbots.plebify.db.SqlQueryEvent]].
 *
 * See [[org.mashupbots.plebify.db.SqlQueryEvent]] for description of parameters
 *
 * @param connectorConfig Connector configuration.
 * @param request Subscription request
 */
class SqlQueryEventWorker(val connectorConfig: ConnectorConfig, val request: EventSubscriptionRequest)
  extends Producer with EventSubscriptionConfigReader with akka.actor.ActorLogging {

  val maxRows = configValueFor("max-rows", "100")
  val datasource = configValueFor("datasource")

  def endpointUri: String = s"jdbc:${datasource}?readSize=${maxRows}"
}