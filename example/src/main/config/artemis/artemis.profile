ARTEMIS_HOME='/maven'
ARTEMIS_INSTANCE='/opt/artemis'

# The logging config will need an URI
# this will be encoded in case you use spaces or special characters
# on your directory structure
ARTEMIS_INSTANCE_URI='file:/opt/artemis/'

# Cluster Properties: Used to pass arguments to ActiveMQ Artemis which can be referenced in broker.xml
#ARTEMIS_CLUSTER_PROPS="-Dactivemq.remoting.default.port=61617 -Dactivemq.remoting.amqp.port=5673 -Dactivemq.remoting.stomp.port=61614 -Dactivemq.remoting.hornetq.port=5446"


# Java Opts
JAVA_ARGS=" -XX:+PrintClassHistogram -XX:+UseG1GC -XX:+AggressiveOpts -XX:+UseFastAccessorMethods -Xms512M -Xmx2G"

#
# There might be options that you only want to enable on specifc commands, like setting a JMX port
# See https://issues.apache.org/jira/browse/ARTEMIS-318
#if [ "$1" = "run" ]; then
#  JAVA_ARGS="$JAVA_ARGS -Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.port=1099 -Dcom.sun.management.jmxremote.rmi.port=1098 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false"
#fi

#
# Logs Safepoints JVM pauses: Uncomment to enable them
# In addition to the traditional GC logs you could enable some JVM flags to know any meaningful and "hidden" pause that could
# affect the latencies of the services delivered by the broker, including those that are not reported by the classic GC logs
# and dependent by JVM background work (eg method deoptimizations, lock unbiasing, JNI, counted loops and obviously GC activity).
# Replace "all_pauses.log" with the file name you want to log to.
# JAVA_ARGS="$JAVA_ARGS -XX:+PrintSafepointStatistics -XX:PrintSafepointStatisticsCount=1 -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCApplicationConcurrentTime -XX:+LogVMOutput -XX:LogFile=all_pauses.log"

# Debug args: Uncomment to enable debug
#DEBUG_ARGS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
