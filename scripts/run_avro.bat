@echo off
echo Running metrics extraction for AVRO (remote GitHub)...
mvn clean compile exec:java "-Dexec.mainClass=it.mantimetrics.App" "-Dexec.args=config/config_avro.properties"
pause