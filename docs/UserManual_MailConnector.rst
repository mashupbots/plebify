Plebify User Manual - Mail Connector
************************************

The mail connector provides email connectivity for Plebify

Connector Settings
==================

Parameters
----------

factory-class-name
  ``org.mashupbots.plebify.mail.MailConnectorFactory``


"received" Event
================

Fires with a new email is received.  Currently, only text based email is supported.

Settings
--------

uri
  `Apache Camel mail component <http://camel.apache.org/mail.html>`_ URI. Common and consumer options are
  applicable. For example: ``imaps://imap.gmail.com:993?username=user@gmail.com&password=secret``.

contains
  Optional comma separated list of words or phrases to match before the event fires. For example,
  ``error, warn`` to match files containing the word ``error`` or ``warn``.

matches
  Optional regular expression to match before the event fires. For example:
  ``"^([\\s\\d\\w]*(ERROR|WARN)[\\s\\d\\w]*)$"`` to match files containing the words ``ERROR`` or ``WARN``.


Event Data
----------

Date
  Timestamp when event occurred

Content
  Body of the email. Currently, only text email is supported. Attachments will be ignored.

ContentType
  MIME Type set to `text/plain` by default

SentOn
  Date the email was sent

From
  Sender's email address

To
  Receiver's email address

Subject
  Subject of the email



"send" Task
===========

Saves the event data to file.

Settings
--------

uri
  `Apache Camel mail component <http://camel.apache.org/mail.html>`_ URI. Common and producer options are
  applicable. For example: ``smtps://smtp.gmail.com:465?username=user@gmail.com&password=secret``

to
  The TO recipients (the receivers of the mail). Separate multiple email addresses with a comma.

from
  The FROM email address.

reply-to
  Optional Reply-To recipients (the receivers of the response mail). Separate multiple email addresses with a comma.

cc
  Optional CC recipients (the receivers of the mail). Separate multiple email addresses with a comma.

bcc
  Optional BCC recipients (the receivers of the mail). Separate multiple email addresses with a comma.

subject
  Optional subject of the email

template
  Optional template for the body. If not specified, the value of ``Content`` will be emailed.




