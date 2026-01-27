#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" > /dev/null && pwd )"

LIB_DIR=${SCRIPT_DIR}/../lib

if [[ -d "$LIB_DIR" ]]; then
   #release directory layout
   LIB_DIR=`cd ${LIB_DIR} > /dev/null; pwd`
   ARTIFACT=`ls -1 ${LIB_DIR}/*.jar`
else
   #development directory layout
   EXAMPLES_DIR=${SCRIPT_DIR}/../../../
   if [[ -d "$EXAMPLES_DIR" ]]; then
      EXAMPLES_DIR=`cd ${EXAMPLES_DIR} > /dev/null; pwd`
   fi
   JAR_PREFIX=`basename ${EXAMPLES_DIR}`
   ARTIFACT=`ls -1 ${EXAMPLES_DIR}/target/${JAR_PREFIX}-*.jar | grep -v test | grep -v javadoc | grep -v sources | grep -v shaded`
   if [[ ! -f "$ARTIFACT" ]]; then
      echo "Jar file is missing. Please do a full build (mvn clean package -DskipTests) first."
      exit -1
   fi
fi

echo "Found ${ARTIFACT}"

QUORUM_OPTS="--peers n0:localhost:6000,n1:localhost:6001,n2:localhost:6002"

CONF_DIR="$DIR/../conf"
if [[ -d "${CONF_DIR}" ]]; then
  LOGGER_OPTS="-Dlog4j.configuration=file:${CONF_DIR}/log4j.properties"
else
  LOGGER_OPTS="-Dlog4j.configuration=file:${DIR}/../resources/log4j.properties"
fi


# for opentelemetry tests

OTEL_JAR_DEFAULT="${SCRIPT_DIR}/../../../../ratis-assembly/target/apache-ratis-3.3.0-SNAPSHOT-bin/lib/trace/opentelemetry-javaagent-2.23.0.jar"

if [[ -z "${OTEL_SERVICE_NAME}" ]]; then
  OTEL_SERVICE_NAME="ratis.server"
fi

if [[ -z "${OTEL_DEFAULT_OPTS}" ]]; then
  OTEL_DEFAULT_OPTS="-Dotel.traces.exporter=logging -Dotel.metrics.exporter=none"
fi

if [[ -n "${OTEL_JAR}" && -f "${OTEL_JAR}" ]]; then
  : # use OTEL_JAR from environment
elif [[ -f "${OTEL_JAR_DEFAULT}" ]]; then
  OTEL_JAR="${OTEL_JAR_DEFAULT}"
else
  echo "Warning: OpenTelemetry agent jar not found; OTEL disabled"
  OTEL_JAR=""
fi

if [[ -n "${OTEL_JAR}" ]]; then
  OTEL_OPTS="-javaagent:${OTEL_JAR} -Dotel.resource.attributes=service.name=${OTEL_SERVICE_NAME} ${OTEL_DEFAULT_OPTS}"
else
  OTEL_OPTS=""
fi

echo "Using OTEL_OPTS: ${OTEL_OPTS}"