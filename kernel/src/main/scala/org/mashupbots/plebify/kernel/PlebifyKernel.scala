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
package org.mashupbots.plebify.kernel

import akka.kernel.Bootable
import akka.actor.ActorSystem
import akka.actor.Props
import org.mashupbots.plebify.core.Engine
import org.mashupbots.plebify.core.StartRequest

/**
 * Kernel to bootup the Plebify engine
 */
class PlebifyKernel extends Bootable {
  val system = ActorSystem("plebify")

  def startup = {
    val engine = system.actorOf(Props(new Engine()), "engine")
    engine ! StartRequest()
  }

  def shutdown = {
    system.shutdown()
  }
}