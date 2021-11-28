# CloudBees File Leak Detector

## Introduction

Runtime diagnosis tool for "too many open files" problem.
This plugin watches file descriptor open/close activities of JVM and allow you to see the list of what's currently opened, and Java call stack that opened the file.
If you are suffering from the too many open files problem, this report enables the developers to fix the leak.

## Usage

This plugin adds "Open File Handles" item in the "Manage Jenkins" page.
Go to this page and click "activate" button to activate the monitoring.
Once this is done, a Java agent is installed on the JVM and starts monitoring new file open/close activities.
Revisit the "Open File Handles" page and you'll see the currently opened files.

To help the developers, activate this, and let Jenkins run for a while, then come back and capture the report and attach it to a ticket.
Do get a few reports with intervals of 10 mins or so, so that we can distinguish real leaks from false positives.
