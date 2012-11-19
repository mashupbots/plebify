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

import org.scalatest.BeforeAndAfterAll
import org.scalatest.GivenWhenThen
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider

class PlebifyConfigSpec extends WordSpec with ShouldMatchers with GivenWhenThen with BeforeAndAfterAll {

  val test1Config = """
	test1 {
      connectors = [{
          connector-id = "file"
          description = "file system"
          factory-class-name = "org.mashupbots.plebify.fileConnector"
          initialization-timeout = 6
          param1 = "a"
        },{
          connector-id = "http"
          factory-class-name = "org.mashupbots.plebify.httpConnector"
          param1 = "a"
          param2 = "b"
        }]
      jobs = [{
          job-id = "job1"
          description = "this is the first job"
          initialization-timeout = 6
          max-worker-count = 10
          max-worker-strategy = "reschedule"
          queue-size = 200
          reschedule-interval = 300
          on = [{
              connector-id = "http"
              connector-event = "request"
              description = "on http request #1"
              initialization-timeout = 6
              param1 = "aaa"
	  	    },{
              connector-id = "file"
              connector-event = "exists"
	  	    }]
          do = [{
              connector-id = "file"
              connector-task = "save"
              description = "save to folder 1"
              execution-timeout = 1
              on-success = "success"
              on-fail = "2"
              max-retry-count = 100
              retry-interval = 101
              param1 = "111"
	  	    },{
              connector-id = "file"
              connector-task = "delete"
	  	    }]
        },{
          job-id = "job2"
          on = [{
              connector-id = "file"
              connector-event = "exists"
              description = "when file is created"
              param1 = "aaa"
	        }]
          do = [{
              connector-id = "file"
              connector-task = "save"
              param1 = "111"
	  	    }]
        }]
    }
    """

  val noConnectorsConfig = """
    no-connectors {
      connectors = []
      jobs = []
    }
    """

  val noJobsConfig = """
    no-jobs {
      connectors = [{ connector-id="file", factory-class-name = "org.mashupbots.plebify.fileConnector" }]
      jobs = []
    }
    """

  val jobEventConnectorNotExistConfig = """
    job-event-connector-not-exist {
      connectors = [{ connector-id="file", factory-class-name = "org.mashupbots.plebify.fileConnector" }]
      jobs = [{
          job-id = "job1"
          on = [{ connector-id = "badconnectorid", connector-event = "event" }]
          do = [{ connector-id = "file", connector-task = "task" }]
        }]
    }
    """

  val jobTaskConnectgorNotExistConfig = """
    job-task-connector-not-exist {
      connectors = [{ connector-id="file", factory-class-name = "org.mashupbots.plebify.fileConnector" }]
      jobs = [{
          job-id = "job1"
          on = [{ connector-id = "file", connector-event = "event" }]
          do = [{ connector-id = "badconnectorid", connector-task = "task" }]
        }]
    }
    """

  val jobNoEventsConfig = """
    job-no-events {
      connectors = [{ connector-id="file", factory-class-name = "org.mashupbots.plebify.fileConnector" }]
      jobs = [{
          job-id = "job1"
          on = []
          do = [{ connector-id = "file", connector-task = "task" }]
        }]
    }
    """

  val jobNoTasksConfig = """
    job-no-tasks {
      connectors = [{ connector-id="file", factory-class-name = "org.mashupbots.plebify.fileConnector" }]
      jobs = [{
          job-id = "job1"
          on = [{ connector-id = "file", connector-event = "event" }]
          do = []
        }]
    }
    """

  val jobUnrecognisedTaskOnSuccessConfig = """
    job-unrecognised-task-on-success {
      connectors = [{ connector-id="file", factory-class-name = "org.mashupbots.plebify.fileConnector" }]
      jobs = [{
          job-id = "job1"
          on = [{ connector-id = "file", connector-event = "event" }]
          do = [{ connector-id = "file", connector-task = "task", on-success = "100" }]
        }]
    }
    """

  val jobUnrecognisedTaskOnFailConfig = """
    job-unrecognised-task-on-fail {
      connectors = [{ connector-id="file", factory-class-name = "org.mashupbots.plebify.fileConnector" }]
      jobs = [{
          job-id = "job1"
          on = [{ connector-id = "file", connector-event = "event" }]
          do = [{ connector-id = "file", connector-task = "task", on-fail = "100" }]
        }]
    }
    """

  val actorConfig = List(test1Config, noConnectorsConfig, noJobsConfig, jobEventConnectorNotExistConfig,
    jobTaskConnectgorNotExistConfig, jobNoEventsConfig, jobNoTasksConfig,
    jobUnrecognisedTaskOnSuccessConfig, jobUnrecognisedTaskOnFailConfig).mkString("\n")

  val actorSystem = ActorSystem("PlebifyConfigSpec", ConfigFactory.parseString(actorConfig))

  "PlebifyConfig" should {

    "load jobs and connectors" in {
      val cfg = new PlebifyConfig(actorSystem.settings.config, "test1")
      cfg.connectors.size should equal(2)
      cfg.jobs.size should equal(2)

      val fileConnector = cfg.connectors(0)
      fileConnector.id should equal("file")
      fileConnector.factoryClassName should equal("org.mashupbots.plebify.fileConnector")
      fileConnector.description should equal("file system")
      fileConnector.initializationTimeout should equal(6)
      fileConnector.params.size should equal(1)
      fileConnector.params("param1") should equal("a")

      val httpConnector = cfg.connectors(1)
      httpConnector.id should equal("http")
      httpConnector.description should equal("")
      httpConnector.factoryClassName should equal("org.mashupbots.plebify.httpConnector")
      httpConnector.initializationTimeout should equal(5)
      httpConnector.params.size should equal(2)
      httpConnector.params("param1") should equal("a")
      httpConnector.params("param2") should equal("b")

      val job1 = cfg.jobs(0)
      job1.id should equal("job1")
      job1.description should equal("this is the first job")
      job1.initializationTimeout should equal(6)
      job1.maxWorkerCount should be(10)
      job1.maxWorkerStrategy should be(MaxWorkerStrategy.Reschedule)
      job1.queueSize should be(200)
      job1.rescheduleInterval should be(300)
      job1.events.size should equal(2)
      job1.tasks.size should equal(2)

      val job1Event1 = job1.events(0)
      job1Event1.jobId should be("job1")
      job1Event1.index should be(0)
      job1Event1.connectorId should equal("http")
      job1Event1.connectorEvent should equal("request")
      job1Event1.description should equal("on http request #1")
      job1Event1.initializationTimeout should equal(6)
      job1Event1.params.size should equal(1)
      job1Event1.params("param1") should equal("aaa")

      val job1Event2 = job1.events(1)
      job1Event2.jobId should be("job1")
      job1Event2.index should be(1)
      job1Event2.connectorId should equal("file")
      job1Event2.connectorEvent should equal("exists")
      job1Event2.description should equal("")
      job1Event2.initializationTimeout should equal(5)
      job1Event2.params.size should equal(0)

      val job1Task1 = job1.tasks(0)
      job1Task1.jobId should be("job1")
      job1Task1.index should be(0)
      job1Task1.connectorId should equal("file")
      job1Task1.connectorTask should equal("save")
      job1Task1.description should equal("save to folder 1")
      job1Task1.executionTimeout should be(1)
      job1Task1.onSuccess should be("success")
      job1Task1.onFail should be("2")
      job1Task1.maxRetryCount should be(100)
      job1Task1.retryInterval should be(101)
      job1Task1.params.size should equal(1)
      job1Task1.params("param1") should equal("111")

      val job1Task2 = job1.tasks(1)
      job1Task2.jobId should be("job1")
      job1Task2.index should be(1)
      job1Task2.connectorId should equal("file")
      job1Task2.connectorTask should equal("delete")
      job1Task2.description should equal("")
      job1Task2.executionTimeout should be(5)
      job1Task2.onSuccess should be("next")
      job1Task2.onFail should be("fail")
      job1Task2.maxRetryCount should be(3)
      job1Task2.retryInterval should be(3)
      job1Task2.params.size should equal(0)

      val job2 = cfg.jobs(1)
      job2.id should equal("job2")
      job2.description should equal("")
      job2.initializationTimeout should equal(5)
      job2.maxWorkerCount should be(5)
      job2.maxWorkerStrategy should be(MaxWorkerStrategy.Queue)
      job2.queueSize should be(100)
      job2.rescheduleInterval should be(5)
    }

    "error when there are no connectors defined" in {
      val ex = intercept[IllegalArgumentException] {
        new PlebifyConfig(actorSystem.settings.config, "no-connectors")
      }
      ex.getMessage should include("No 'connectors' defined.")
    }

    "error when there are no jobs defined" in {
      val ex = intercept[IllegalArgumentException] {
        new PlebifyConfig(actorSystem.settings.config, "no-jobs")
      }
      ex.getMessage should include("No 'jobs' defined.")
    }

    "error when a job event subscription connector id is not found" in {
      val ex = intercept[Error] {
        new PlebifyConfig(actorSystem.settings.config, "job-event-connector-not-exist")
      }
      ex.getMessage should be("Connector id 'badconnectorid' in job 'job1' event #1 does not exist.")
    }

    "error when there a job task connector id is not found" in {
      val ex = intercept[Error] {
        new PlebifyConfig(actorSystem.settings.config, "job-task-connector-not-exist")
      }
      ex.getMessage should be("Connector id 'badconnectorid' in job 'job1' task #1 does not exist.")
    }

    "error when a job has no events defined" in {
      val ex = intercept[IllegalArgumentException] {
        new PlebifyConfig(actorSystem.settings.config, "job-no-events")
      }
      ex.getMessage should include("No 'events' defined in job 'job1'")
    }

    "error when a job has no tasks defined" in {
      val ex = intercept[IllegalArgumentException] {
        new PlebifyConfig(actorSystem.settings.config, "job-no-tasks")
      }
      ex.getMessage should include("No 'tasks' defined in job 'job1'")
    }

    "error when a job task has a on-success setting that is not recognised" in {
      val ex = intercept[Error] {
        new PlebifyConfig(actorSystem.settings.config, "job-unrecognised-task-on-success")
      }
      ex.getMessage should include("Unrecognised command '100' in 'on-success' of job 'job1' task #1")
    }

    "error when a job task has a on-fail setting that is not recognised" in {
      val ex = intercept[Error] {
        new PlebifyConfig(actorSystem.settings.config, "job-unrecognised-task-on-fail")
      }
      ex.getMessage should include("Unrecognised command '100' in 'on-fail' of job 'job1' task #1")
    }

  }
}

