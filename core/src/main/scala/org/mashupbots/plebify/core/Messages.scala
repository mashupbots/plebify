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
 * Identifies message sent to/from actors
 */
trait Message

/**
 * Identifies messages sent to actors to initiate processing. A [[org.mashupbots.plebify.core.ResponseMessage]] will
 * be sent to the sender when processing is finished.
 */
trait RequestMessage extends Message

/**
 * Identifies messages sent from actors to indicate the result of processing in response to a
 * [[org.mashupbots.plebify.core.RequestMessage]].
 */
trait ResponseMessage extends Message {

  /**
   * Error message
   */
  def errorMessage: String

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
 * One way notification messages. No response is expected.
 */
trait NotificationMessage extends Message

/**
 * Request to starting an FSM actor
 */
case class StartRequest() extends RequestMessage

/**
 * Response to starting an FSM actor
 */
case class StartResponse(errorMessage: String = "", error: Option[Throwable] = None) extends ResponseMessage

/**
 * Request to stopping an FSM actor
 */
case class StopRequest() extends RequestMessage

/**
 * Response to stopping an FSM actor
 */
case class StopResponse(errorMessage: String = "", error: Option[Throwable] = None) extends ResponseMessage

/**
 * Message to start an FSM actor
 */
case object Start extends NotificationMessage

/**
 * Message to stop an FSM actor
 */
case object Stop extends NotificationMessage
