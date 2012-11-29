Plebify
*******

A simple event triggered task runner.


What does Plebify do?
=====================
Most large applications that we've worked on have associated manual tasks such as:

  - checking log files for errors
  - running queries and emailing out the results
  - boring data entry

Due to resource and budget constraints, these tasks seem to never get automated.  Rather, they 
tend to get delegated to `Plebs <http://en.wikipedia.org/wiki/Plebs>`_.  The lower you are in 
the team, the more likely it is you will be doing these tasks.

Plebify aims to be your virtual pleb.

For example:

1. Scan a directory for log files and email it to you if the file contains the word "error".

2. When a new record is added to a products database table, send email notifications to product
   managers and add an entry to a new products RSS feed.

3. When an email arrives containing an order form, add it to the orders database table.


Road Map
========
Plebify is still in proof of concept.

We would like to add a few more features and error handling before we call it alpha.

Currently, it is only customisable via its configuration files.  It is envisaged that, eventually, 
web based configuration will be available.  Changes will take effect immediately and plebify will
not have to be restarted.

Currently, it only supports system integration via HTTP, Web Sockets, file system and email. We
want to add more connectors to more systems.

Currently, it only supports basic templating when converting data to text. We want to add a better
templating engine.

If you got an idea for a connector, please let us know by opening a ticket; or better still, send us
a pull request.


Links
=====
 - `Download <https://github.com/mashupbots/plebify/downloads>`_

 - `User Manual <https://github.com/mashupbots/plebify/blob/master/docs/UserManual.rst>`_
   How to install and run Plebify.
    
 - `Developer's Guide <https://github.com/mashupbots/plebify/blob/master/docs/DevelopersGuide.rst>`_ - 
    architecture, design concepts and how to build.

 - `Issue Tracker <https://github.com/mashupbots/plebify/issues>`_


