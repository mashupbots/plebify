Plebify Developers' Guide
*************************

Concepts
========
Plebify is a rules based a data exchange for applications.

The rules are encapsulated in **jobs**.  Each job comprise of **events** and **tasks**.  When a 
job's event fires, its tasks are executed.

For example:

- when a file is created on the file system, email it to person A and HTTP post it to a REST endpoint 
  of elasticsearch.
- when a database is created, email the new record to a group email
- when and email is received, write it to a database

The firing of events and the execution of tasks are perform by **connectors**. Connectors are named 
as such because they connect Plebify with applications.  For example, the mail connector connects
Plebify with email. It fires the mail received event and executes the send mail task.


Implementation
==============
We wanted to use the `Actor Model <http://en.wikipedia.org/wiki/Actor_model>`_ to implement Plebify.

Each element of Plebify can be represented as an actor.

- Job
- Connector
- Event
- Task

At startup, Plebify instances connector actors and job actors as per its configuration.

When job actors start up, they send a message to the relevant connector actor to subscribe its event(s).
In response, the connector actor starts up a event actor to manage the subscription.

When an event fires, the event actor sends the job actor a message containing details of the event.

To execute its task, the job actor starts a job worker actor.  In this way, concurrent events can be 
processed concurrently; i.e. more than one job worker actor may be running at the same time.

A job worker actor executes tasks one at a time.  It does this by sending a message to the relevant
connector actor and waiting for a response.  The connector actor in turns instances a task actor
to perform the actual work. In this way, the connector can execute many tasks concurrently; i.e. more than
one task worker may be running at the same time.

We chose the Akka at the Actor Model framework because it has Apache Camel integration (in addition 
to scalability, fault tolerance, etc... that you should expect from any framework you use). Using Akka Camel
for events and triggers actors just seems to make sense to us.

In going with Akka, we decided to use its Scala interface because it looks cleaner and simpler and the Java
one.



Building Plebify
================
- Install Scala 2.10 RC2 and JDK7.

- Download and install `sbt <http://www.scala-sbt.org/>`_ 0.12.1.

- Clone our the code from github

- Run ``sbt clean dist`` from the plebify directory

- The distribution can be found in the ``target/plebify-X.Y.Z`` directory.



Setting up Eclipse
==================
- Complete the above steps to build Plebify

- Install Eclipse 4.2 Juno with `Scala IDE <http://scala-ide.org/>`_ 2.1.0 Milestone 2.

- Generate eclipse project files. Run ``sbt eclipse`` from the plebify directory

- Start ``Eclipse``

- From the top menu, select ``File`` | ``Import``

  - Select ``General`` | ``Existing Projects into Workspace``.
  - Click ``Next``.
  - Select the ``plebify`` directory as the root
  - Select all sub projects
  - Click ``Finish``



Running ScalaTests
==================

We run scala tests from eclipse.  From ``sbt`` there are errors where ``scala.actors.Actors`` cannot be found.
This is a temporary error with Scala 2.10 still being in RC and ScalaTest not fully updated (I think).

Before you run the scalatest, you will need to setup `plebify-test-config.txt` in your home directory.  It needs
to contain your credentials and other settings for access connector resources.

::

  #
  # Settings for org.mashupbots.plebify.mail.MailConnectorSpec
  #
  mail-send-from=user@gmail.com
  mail-send-to=user@gmail.com
  mail-send-uri=smtps://smtp.gmail.com:465?username=user@gmail.com&password=secret
  mail-receive-uri=imaps://imap.gmail.com:993?username=user@gmail.com&password=secret

  #
  # Settings for org.mashupbots.plebify.db.DbConnectorSpec
  #
  datasource-driver = com.mysql.jdbc.Driver
  datasource-url = jdbc:mysql://127.0.0.1:3306/test
  datasource-user = test
  datasource-password = test123








