#!/bin/sh

if [ -z "$1" ]; then
	echo >&2 "Usage: $0 <BUILD_DIR> [setup.py arguments]"
	exit 1
fi

cd `dirname $0`
BUILD_DIR="$1"
shift

CUSTOM_CFLAGS="-I.. -I${BUILD_DIR}/auto -O2 -march=native -Wno-cpp -Wno-unused-function -std=c++11"

case $OSTYPE in
	darwin*)
		CC="g++" CXX="g++" CFLAGS="${CUSTOM_CFLAGS} -stdlib=libc++" python3 setup.py "$@"
		;;
	*)
		CFLAGS="${CUSTOM_CFLAGS}" python3 setup.py "$@"
		;;
esac

