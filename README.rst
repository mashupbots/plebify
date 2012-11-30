Plebify
*******

A simple event triggered task runner written in Scala and Akka.



What does Plebify do?
=====================
Most large applications that we've worked on have associated manual tasks such as:

- checking log files for errors
- running queries and emailing out the results
- consoliation files from various systems for batch upload

Due to resource and budget constraints, these tasks never get automated.  Rather, they 
tend to get delegated to `Plebs <http://en.wikipedia.org/wiki/Plebs>`_.  The lower you are in 
the team, the more likely it is that you will perform these tasks.

Plebify aims to be your virtual pleb.

For example:

1. Scan a directory for log files and email it to you if the file contains the word "error".

2. When a new record is added to a products database table, send email notifications to product
   managers and add an entry to a new products RSS feed.

3. When an email arrives containing an order form, add it to the orders database table.



Road Map
========
We would consider Plebify as being in "alpha" and usable for non-critial purposes.

Currently, Plebify is only customisable via its configuration files.  It is envisaged that, eventually, 
a web based configuration will be available.  Changes will take effect immediately and Plebify will
not have to be restarted.

Currently, Plebify only supports system integration via HTTP, Web Sockets, file system and email. We
will be adding more connectors.

Currently, it only supports basic templating when converting data to text. We will add a better
templating engine.

If you got an idea for an improvement or found a bug, please let us know by opening a ticket; or better 
still, send us a pull request.



Links
=====
- `Download <https://github.com/mashupbots/plebify/downloads>`_
   Download the latest version of Plebify.

- `User Manual <https://github.com/mashupbots/plebify/blob/master/docs/UserManual.rst>`_
   Installation. Getting Started. Connector Reference.
    
- `Developer's Guide <https://github.com/mashupbots/plebify/blob/master/docs/DevelopersGuide.rst>`_
   Architecture, design concepts and how to build. 

- `Akka Patterns <https://github.com/mashupbots/plebify/blob/master/docs/AkkaPatterns.rst>`_
   Learning Akka? Here are a few lessons that we've learnt.

- `Issue Tracker <https://github.com/mashupbots/plebify/issues>`_
   Got a bug or question? Log it here.


