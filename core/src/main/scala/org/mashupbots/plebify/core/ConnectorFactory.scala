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

import akka.actor.ActorContext
import org.mashupbots.plebify.core.config.ConnectorConfig
import akka.actor.ActorRef

/**
 * Factory to instance a new connector actor
 * 
 * This class is needed because I could not find a way to dynamically instance a class and pass it to `Props` when
 * instancing an actor.
 */
trait ConnectorFactory {

  /**
   * Instances a new connector actor
   *
   * The [[org.mashupbots.plebify.core.Engine]] will call instance and call this factory class to create a new
   * instance in the specified actor system.
   *
   * The actor must be created using the supplied `context` so it can be managed and named using
   * `connectorConfig.actorName`.
   *
   * After instancing, [[org.mashupbots.plebify.core.Engine]] will send a [[org.mashupbots.plebify.core.StartRequest]]
   * message to the actor.  [[org.mashupbots.plebify.core.StartResponse]] is expected in reply when the actor is
   * ready to process [[org.mashupbots.plebify.core.ConnectorMessage]]s.
   *
   * When stopping, the [[org.mashupbots.plebify.core.Engine]] will send a [[org.mashupbots.plebify.core.StopRequest]]
   * message. [[org.mashupbots.plebify.core.StopResponse]] is expected in reply.
   *
   * @param context Engine actor context
   * @param connectorConfig configuration for this connector
   */
  def create(context: ActorContext, connectorConfig: ConnectorConfig): ActorRef
}