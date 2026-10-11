@echo off
if not exist out call build.bat
java -cp "out;lib/*" ai.Main
