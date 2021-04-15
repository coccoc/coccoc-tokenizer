#!/bin/sh

if [ -z "$1" ]; then
	echo >&2 "Usage: $0 <BUILD_DIR>"
	exit 1
fi


SOURCE_DIR=`dirname $0`
BUILD_DIR="$1"

OS="$(uname | tr '[:upper:]' '[:lower:]')"
if [ "${OS}" = "darwin" ]; then
    # macOS
    JAVA_HOME="$(/usr/libexec/java_home)"
		OUTPUT_FILE='libcoccoc_tokenizer_jni.dylib'
  else
    JAVA_HOME="$(dirname $(dirname $(readlink -f $(which javac))))"
		OUTPUT_FILE='libcoccoc_tokenizer_jni.so'
  fi


mkdir -p ${BUILD_DIR}/java
${JAVA_HOME}/bin/javac -h ${BUILD_DIR}/java -d ${BUILD_DIR}/java ${SOURCE_DIR}/src/java/*.java

g++ -shared -Wall -Werror -std=c++11 -Wno-deprecated -O3 -DNDEBUG -ggdb -fPIC \
	-I ${SOURCE_DIR}/.. \
	-I ${BUILD_DIR}/auto \
	-I ${BUILD_DIR}/java \
	-I ${JAVA_HOME}/include \
	-I ${JAVA_HOME}/include/${OS} \
	-o ${BUILD_DIR}/${OUTPUT_FILE} \
	${SOURCE_DIR}/src/jni/Tokenizer.cpp

jar -cf ${BUILD_DIR}/coccoc-tokenizer.jar -C ${BUILD_DIR}/java .
