#!/bin/bash
echo "Running metrics extraction for BOOKKEEPER..."
mvn clean compile exec:java "-Dexec.mainClass=it.mantimetrics.App" "-Dexec.args=config/config_bookkeeper.properties"