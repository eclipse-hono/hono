#!/bin/bash
SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
JMETER_HOME=${JMETER_HOME:=~/apache-jmeter-3.3}
HONO_HOST=$1
HONO_HOST=${HONO_HOST:=127.0.0.1}
OUT=$SCRIPTPATH/results
HONO_HOME=${HONO_HOME:=$SCRIPTPATH/../../..}
TRUST_STORE_PATH=$HONO_HOME/demo-certs/certs/trusted-certs.pem
SAMPLE_LOG=load-test-http-router.jtl
TEST_LOG=load-test-http-router.log

rm -rf $OUT
rm $SAMPLE_LOG

$JMETER_HOME/bin/jmeter -n -f \
-l $SAMPLE_LOG -j $TEST_LOG \
-t $SCRIPTPATH/http_messaging_throughput_test.jmx \
-Jplugin_dependency_paths=$HONO_HOME/jmeter/target/plugin \
-Jjmeterengine.stopfail.system.exit=true \
-Jrouter.host=$HONO_HOST -Jrouter.port=15672 \
-Jregistration.host=$HONO_HOST -Jregistration.http.port=28080 \
-Jhttp.host=$HONO_HOST -Jhttp.port=8080 \
-Lorg.eclipse.hono.client.impl=WARN -Lorg.eclipse.hono.jmeter=INFO \
-JdeviceCount=15

