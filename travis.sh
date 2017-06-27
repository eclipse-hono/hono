#!/bin/bash

#
# For the time being, we are NOT using this script to build Hono anymore
# instead, we are simply invoking mvn install ... from .travis.yml
#

# Abort on Error
set -e

export PING_SLEEP=30s
export WORKDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
export BUILD_OUTPUT=$WORKDIR/build.out

touch $BUILD_OUTPUT

dump_output() {
   echo Tailing the last 500 lines of output:
   tail -500 $BUILD_OUTPUT
}
error_handler() {
  echo ERROR: An error was encountered with the build.
  dump_output
  exit 1
}
# If an error occurs, run our error handler to output a tail of the build
trap 'error_handler' ERR

# Set up a repeating loop to send some output to Travis.
bash -c "while true; do echo \$(date) - building ...; sleep $PING_SLEEP; done" &
PING_LOOP_PID=$!

if false && [ "$TRAVIS_EVENT_TYPE" = "cron" ]
then
  # Build Qpid Dispatch Router Docker image
  cd $TRAVIS_BUILD_DIR/dispatchrouter
  mvn process-resources
  bash target/qpid-dispatch/build-qpid-dispatch-image.sh
  cd $TRAVIS_BUILD_DIR
fi

mvn install -DcreateJavadoc=true -Pbuild-docker-image,run-tests >> $BUILD_OUTPUT 2>&1

# The build finished without returning an error so dump a tail of the output
dump_output

# nicely terminate the ping output loop
kill $PING_LOOP_PID