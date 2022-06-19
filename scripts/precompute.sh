#!/bin/sh

LOADER="adf.impl.DefaultLoader"
PARAMS="-pre 1 -t 1,0,1,0,1,0 -local&&PID=$$;sleep 120;kill $PID"

cd "$(dirname "$0")"
cd ../

./gradlew launch --args="${LOADER} ${PARAMS}"