#!/bin/sh
# Downloads the official MySQL JDBC driver into ./lib
cd "$(dirname "$0")"
mkdir -p lib
curl -L -o lib/mysql-connector-j-8.4.0.jar https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar \
  && echo "Driver downloaded to lib/" || echo "Download failed - get the jar manually (see README, step 3)"
