plebify
=======

A simple event triggered task runner.



What does Plebify do?
---------------------
In my IT experience, we usually give menial jobs to new graduates - more affectionaly 
known as `Plebs <http://en.wikipedia.org/wiki/Plebs>`_.

In handing over such tasks, we usually give plebs a job description along the lines of
``when event A happens do X, Y and Z tasks``.

We thought it would be a good idea to see if we could improve the quality of life for plebs by 
automating these menial jobs.

For example:

1. When a new record is added to a products database table, send email notifications to product
   managers and add an entry to a new products RSS feed.

2. When an email arrives containing an order form, add it to the orders database table.

In addition, we should also be able to filter and transform data. For example:

1. Aggregate HTTP requests, body of emails and contents of files into a real time websocket
   broadcast.

2. Check log files from batch jobs on the (file system or emailed to a inbox) for errors. If
   there are errors, email the error to the support team. Also, write the log into elastic search.



High-level Design and Architecture
----------------------------------
At its core, Plebify performs system to system integration.  As such, it makes sense to use 
`Apache Camel <http://camel.apache.org/>`_.  It is a proven product that supports many different types 
of transports and data formats.

From a modelling view point, we wanted to model jobs as actors:

- A job has clear states: started or stopped.
- A job is triggered to execute tasks by an event message from one or more source systems
- A job is execute tasks by sending messages to one or more destination systems that perform the tasks
- In executing tasks, a job may need to filter and/or transform the data

We decided to implemented Plebify using Scala 2.10 and Akka 2.1 because they supported Camel and the actor 
model.

MORE INFO TO COME....



Using Plebify from the Command Line
-----------------------------------
TO DO



Using Plebify as a Library
--------------------------
TO DO




Building Plebify
----------------
- Download and install `sbt <http://www.scala-sbt.org/>`_ 0.12.1.

- Get our the code from github

- Run ``sbt`` from the root source directory



Setting up Eclipse
------------------
- We are currently using Scala 2.10 RC2 and JDK7.

- We are currently using Eclipse 4.2 Juno with `Scala IDE <http://scala-ide.org/>`_ 2.1.0 Milestone 2.

- Generate eclipse project files: ``sbt eclispse``

- Start ``Eclipse``

- From the top menu, select ``File`` | ``Import``

  - Select ``General`` | ``Existing Projects into Workspace``.
  - Click ``Next``.
  - Select the ``plebify`` directory as the root
  - Select all sub projects
  - Click ``Finish``


