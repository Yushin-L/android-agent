# Android app

제품 소스의 빌드 진입점이다. GUI 작업 공간·Codex 세션·지속 연결·네이티브 화면을 포함한다. M2 구현을 완료했고 검증 범위와 남은 실기기 확인 항목은 GitHub 이슈에 기록했다.
진행과 완료 증거: https://github.com/Yushin-L/android-agent/milestone/3

## 빌드

Linux ARM64와 Docker가 필요하다. 프로젝트 루트에서 실행한다.

```sh
docker build -t android-agent-builder app
mkdir -p app/downloads
curl --fail --location https://registry.npmjs.org/@mmmbuto/codex-cli-termux/-/codex-cli-termux-0.156.1-termux.1.tgz -o app/downloads/runtime.tgz
python3 app/prepare_android_payload.py app/downloads/runtime.tgz
docker run --rm --network none --user "$(id -u):$(id -g)" -v "$PWD/app:/work" android-agent-builder
```

출력: `app/artifacts/android-agent-0.7.4-arm64.apk`. 빌드 과정에서 로컬 개발 서명키를 만든다.
런타임 아카이브의 SHA-512를 검사한 뒤 APK용 helper 이름만 동일 길이로 교체한다.
출처·원본/수정 SHA-256·패치 위치는 `assets/runtime-provenance.json`에 기록된다.
Codex의 Apache-2.0 고지는 `assets/CODEX-LICENSE`, `CODEX-NOTICE`에 포함된다.

## 기존 진단 앱과 업데이트

`dev.androidagent.probe` 패키지 및 앱 내부 `online-runtime/codex-home` 경로를 유지한다.
이전 APK와 같은 서명키를 `app/artifacts/debug.keystore`에 배치하면 기존 인증을 유지하며 업데이트할 수 있다.
새 키로 빌드한 APK는 기존 설치 위에 업데이트할 수 없다. 기존 키는 저장소에 포함하지 않는다.
진단 앱의 메모리 전용 대화 선택 상태는 이전 대상이 아니며 Codex 원본 기록은 삭제하지 않는다.
인증 파일을 외부로 내보내거나 다른 장치에서 복사하지 않는다.

Android 실기기 검증은 이전 진단 버전까지의 증거이며 이 제품 버전의 실기기 검증을 의미하지 않는다.

## 서버 검증

```sh
docker run --rm --network none --user "$(id -u):$(id -g)" -v "$PWD/app:/work" --entrypoint bash android-agent-builder test-inside.sh
```

작업 공간 소유권·저장 실패 롤백, RPC 응답 분리, 세션별 스트리밍·중단·기록 조회를 검증한다. `RealSessionTest`는 `CODEX_CONTROL_BINARY`로 지정한 Linux 실행 파일이 있을 때만 실행한다. 별도 읽기 전용 볼륨으로 파일을 제공하고 `--network none`을 유지한다. 임시 CODEX_HOME을 사용하고 호스트 인증은 사용하지 않는다. Linux 결과는 Android 실행 증거가 아니다.

Pretendard 1.3.9 글꼴 원본과 SIL OFL은 assets에 포함된다. 폰트 출처와 무결성은 `assets/FONT-PROVENANCE`에 기록한다.
