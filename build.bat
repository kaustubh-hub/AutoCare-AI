@echo off
if not exist out mkdir out
javac -encoding UTF-8 -cp "lib/*" -d out src\ai\*.java && echo Build OK - now run run.bat
