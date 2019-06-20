#!/bin/sh

if [ -z "$1" ]; then
	echo >&2 "Usage: $0 <BUILD_DIR> [setup.py arguments]"
	exit 1
fi

cd `dirname $0`
BUILD_DIR="$1"
shift

CFLAGS="-I.. -I${BUILD_DIR}/auto" python3 setup.py "$@"

