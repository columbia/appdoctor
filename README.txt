Overview
========
AppDoctor can automatically test Android applications for problems. It can run test sessions on multiple hosts and analyze logs from the sessions to find and verify bugs. It can also be used as a scripting tool for developers to write automatic test scripts.

Usage
=====
First:
* generate a keystore
* edit the config files to prepare for a test
On the testing hosts:
* upload AppDoctor to ~/andchecker
* ensure that Android NDK and SDK are available in the PATH
* ensure that JDK is in the PATH
* run tools/get.sh to get external tools
On the controlling host:
* use scripts to start/stop test sessions
* use analyze script to analyze logs

Config Files
============
andchecker.keystore: java key storage, used to sign apks
android_test.conf: test configuration file
apps.csv: applications to test
configs.csv: device configurations to test
hints.csv: hints for text boxes and other widgets
hosts.txt: hosts to run tests with
settings.py: some constants for the scripts

Scripts
=======
run.sh: start a test session on specified hosts
check.sh: start a session on local host
collect.sh: collect logs from hosts
list.sh: check for a clean environment
stop.sh: stop the test session on all the hosts
analyze.py: analyze logs of test sessions
reproduce.py: the underlying building block. Can be used to test applications, reproduce bugs or start a console session to run a step-by-step debug session.
controller.py: the test controller running on each host

Directories
===========
dynclass/: compile and run custom test commands
media/: media files to be transferred to the device for testing
tools/: external tools
instrument_server/: instrumentation application, run on the device