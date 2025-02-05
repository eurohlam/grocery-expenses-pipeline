#!/bin/bash

PORT=8080

read -r -d '' RESPONSE <<EOM
HTTP/1.1 200 OK
Content-Type: application/json; charset="utf-8"
Content-Length: 17

{"status": "ok"}
EOM

while true
do
  echo 'Mock REST Endpoint is started and waiting for incoming message'
  echo "$RESPONSE" | nc -l ${PORT} || break
done &