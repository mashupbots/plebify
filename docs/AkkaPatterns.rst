Akka Patterns
*************

We are not Akka gods. We are still learning Akka as well!

We thought it might be useful to list some of the things we have learnt to share.

If you know of a better way, please let us know.



Finite State Machine (FSM)
==========================

We think this is one of the best features of Akka. I really helps when your actor is complex.
It forces you to think what should happen at a certain state; and catch unexpected messages 
arriving. We've implemented it in several classes: 

- ``org.mashupbots.plebify.core.Engine``
- ``org.mashupbots.plebify.core.Job``
- ``org.mashupbots.plebify.core.JobWorker``
- ``org.mashupbots.plebify.db.SqlQueryEvent``



Using futures to wait without blocking
======================================

This took a while for us to get this right. We ended up combining futures with FSM.

The following example is taken from ``org.mashupbots.plebify.core.JobWorker``. 

**Starting Up**

We start at the *Idle* state.

::

  when(Idle) {
    case Event(msg: Start, Uninitialized) => {
      log.info(s"Executing tasks for job '${jobConfig.id}'")
      goto(Active) using executeCurrentTask(Progress())
    }
    case unknown =>
      log.debug("Received unknown message while Idle: {}", unknown.toString)
      stay
  }


When we get a start message, we ``executeCurrentTask()`` which sends a request to the connector to execute 
a task as defined in ``progress``. 

**Sending a Request Message**

In ``executeCurrentTask()``, we use ``ask`` when sending a message to the connector actor so that we have a
``future`` to wait on.  We specify the timeout in the ask and map the future to the expected type of the 
message that is to be returned.

Finally, we pipe the future to ourself.  This means that the future result will be passed back as a message
for processing.

::

    val future = ask(connector, msg)(taskConfig.executionTimeout seconds).mapTo[TaskExecutionResponse]
    future pipeTo self


**Waiting for a Response Message**

Without blocking, and in the *Active* state, we wait for a response or timeout in 30 seconds.

::

  when(Active) {
    case Event(result: TaskExecutionResponse, progress: Progress) => {
      processResult(progress, result.error)
    }
    case Event(msg: akka.actor.Status.Failure, progress: Progress) => {
      processResult(progress, Some(msg.cause))
    }
  }


If the future is successful, a message of type ``TaskExecutionResponse`` is send to the actor as per the 
mapping performed on the future.

If the future failed, a message of type ``akka.actor.Status.Failure`` is send to the actor..



Performed periodic work
=======================

To perform periodic working, we setup a schedule to send a periodic message to an actor.

See ``org.mashupbots.plebify.db.SqlQueryEvent`` where the actor sends a "tick" message to itself in order to
periodically trigger querying of an SQL database.

::

  override def preStart() {
    val worker = context.actorOf(Props(new SqlQueryEventWorker(connectorConfig, request)), "worker")
    context.system.scheduler.schedule(initialDelay seconds, interval seconds, self, "tick")
  }

  when(Idle) {
    case Event("tick", _) =>
      log.debug(s"Running sql for '${request.config.jobId}-${request.config.index}'")
      val worker = context.actorFor("worker")
      val future = ask(worker, queryMessage)(sqlTimeout seconds).mapTo[CamelMessage]
      future pipeTo self
      goto(Active)
    case unknown =>
      log.debug("Received message while Idle: {}", unknown.toString)
      stay
  }

We combined this with FSM so that a "tick" message when the actor is in an *Active* state is ignored.



Parsing Application Specific Settings
=====================================

Akka uses `HOCON <https://github.com/typesafehub/config/blob/master/HOCON.md>`_ format configuration file. 
See the code in ``org.mashupbots.plebify.config`` package on how we've parsed it.



Microkernel
===========

The microkernel is a quick and easy way to package and distribute your Akka application.

To use it, we wrote some startup code in ``org.mashupbots.plebify.kernel.PlebifyKernel``.

Then we used ``akka-sbt-plugin`` to package it up in ``build.scala``.

::

  lazy val kernel = Project(
    id = "plebify-kernel",
    base = file("kernel"),
    dependencies = Seq(
      core,
      httpConnector, fileConnector, 
      mailConnector, dbConnector
    ),
    settings = defaultSettings ++ 
      AkkaKernelPlugin.distSettings ++ 
      Seq(
        description := "Database events and actions",
        libraryDependencies ++= Dependencies.kernel,
        distJvmOptions in Dist := "-Xms256M -Xmx1024M",
        outputDirectory in Dist := file("target/plebify-" + plebifyVersion),
        distMainClass in Dist := "akka.kernel.Main org.mashupbots.plebify.kernel.PlebifyKernel",
        dist <<= (dist, sourceDirectory, state) map { (targetDir:File, srcDir:File, st) => {
            val log = st.log
            val fromDir = new File(srcDir, "examples")
            val toDir = new File(targetDir, "examples")
            ExtraWork.copyFiles(fromDir, toDir)
            log.info("Copied examples")
            targetDir
          }
        }
      )
  )  

Note the ``outputDirectory``, ``distMainClass`` and ``distJvmOptions`` settings for ``AkkaKernelPlugin``.

I also took us a while to find out how to extend the SBT ``dist`` task associated with ``AkkaKernelPlugin`` to 
copy our examples over to the target directory.

It was more SBT that anything else ... how to extend a task and access settings was obvious to us.



For System integration, use Akka Camel
======================================

There are so many Camel actors out there - makes sense to use them.

Here's a HTTP client to post to ``http://localhost:8877/test`` in 2 lines of code:

::

  class MyProducer extends Producer {
    def endpointUri = "jetty:http://localhost:8877/test"
  }

Just send a message to it and it will be posted to the URL.

Here's a HTTP server endpoint at ``http://localhost:9999/test``

::

  class MyConsumer extends Consumer {
    def endpointUri = "jetty:http://localhost:9999/test"
    def receive = {
      case msg: CamelMessage => {
        val body = if (msg.body != null) msg.bodyAs[String] else ""
        if (body == "return error")
          sender ! CamelMessage("", Map((Exchange.HTTP_RESPONSE_CODE, "500")))
        else
          sender ! "Hello"
      }
    }
  }




