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

import org.mashupbots.plebify.core.config.EventSubscriptionConfig

import akka.actor.ActorRef

/**
 * Result of processing
 */
trait ResultMessage {

  /**
   * Error (if any)
   */
  def error: Option[Throwable]

  /**
   * True if success, false if error.
   */
  val isSuccess = error.isEmpty
}

/**
 * State for use in a FSM
 */
sealed trait State

/**
 * FSM is not working
 */
case object Idle extends State

/**
 * FSM is working
 */
case object Active extends State

/**
 * Data for use with states in a FSM
 */
trait StateData

/**
 * No state data present
 */
case object Uninitialized extends StateData

