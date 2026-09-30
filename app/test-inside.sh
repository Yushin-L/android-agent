#!/usr/bin/env bash
set -euo pipefail
cd /work
mkdir -p build/tests
javac --release 8 -encoding UTF-8 -cp /toolchain/json.jar -d build/tests src/dev/androidagent/probe/{GeneratedImages,WorkspaceFiles,WorkspaceStore,AppServerConnection,SessionController,ModelSelection,BatteryTool,RpcClient,AuthDiagnostics}.java tests/*.java
java -cp build/tests:/toolchain/json.jar WorkspaceStoreTest
java -cp build/tests:/toolchain/json.jar AppServerConnectionTest
java -cp build/tests:/toolchain/json.jar SessionControllerTest
java -cp build/tests:/toolchain/json.jar RealSessionTest
java -cp build/tests:/toolchain/json.jar ModelSelectionTest

java -cp build/tests:/toolchain/json.jar WorkspaceFilesTest

java -cp build/tests:/toolchain/json.jar GeneratedImagesTest
