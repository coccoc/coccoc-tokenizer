#!/bin/sh

if [ -z "$1" ]; then
	echo >&2 "Usage: $0 <BUILD_DIR>"
	exit 1
fi

SOURCE_DIR=`dirname $0`
BUILD_DIR="$1"

JAVA_COMPILER=`readlink -f \`which javac\``
JAVA_HOME=${JAVA_COMPILER%%/bin/javac}

mkdir -p ${BUILD_DIR}/java
javac -h ${BUILD_DIR}/java -d ${BUILD_DIR}/java ${SOURCE_DIR}/src/java/*.java

g++ -shared -Wall -Werror -std=c++11 -Wno-deprecated -O3 -DNDEBUG -ggdb -fPIC \
	-I ${SOURCE_DIR}/.. \
	-I ${BUILD_DIR}/auto \
	-I ${BUILD_DIR}/java \
	-I ${JAVA_HOME}/include \
	-I ${JAVA_HOME}/include/linux \
	-o ${BUILD_DIR}/libcoccoc_tokenizer_jni.so \
	${SOURCE_DIR}/src/jni/Tokenizer.cpp

jar -cf ${BUILD_DIR}/coccoc-tokenizer.jar -C ${BUILD_DIR}/java .
