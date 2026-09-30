#!/usr/bin/env bash
set -euo pipefail
cd /work
mkdir -p build/tests
python3 prepare_markdown.py --check
javac --release 8 -encoding UTF-8 -cp /toolchain/json.jar:downloads/markdown/* -d build/tests src/dev/androidagent/probe/{MarkdownDocument,PreviewPolicy,ExecutionJournal,GeneratedImages,WorkspaceFiles,WorkspaceStore,AppServerConnection,SessionController,ModelSelection,BatteryTool,RpcClient,AuthDiagnostics}.java tests/*.java
java -cp build/tests:/toolchain/json.jar:downloads/markdown/* WorkspaceStoreTest
java -cp build/tests:/toolchain/json.jar:downloads/markdown/* AppServerConnectionTest
java -cp build/tests:/toolchain/json.jar:downloads/markdown/* SessionControllerTest
java -cp build/tests:/toolchain/json.jar:downloads/markdown/* RealSessionTest
java -cp build/tests:/toolchain/json.jar:downloads/markdown/* ModelSelectionTest

java -cp build/tests:/toolchain/json.jar:downloads/markdown/* WorkspaceFilesTest

java -cp build/tests:/toolchain/json.jar:downloads/markdown/* GeneratedImagesTest

java -cp build/tests:/toolchain/json.jar:downloads/markdown/* ExecutionJournalTest
java -cp build/tests:/toolchain/json.jar:downloads/markdown/* RealRecoveryTest
java -cp build/tests:/toolchain/json.jar:downloads/markdown/* DocumentFeaturesTest
