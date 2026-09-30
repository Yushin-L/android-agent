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

출력: `app/artifacts/android-agent-0.8.0-arm64.apk`. 빌드 과정에서 로컬 개발 서명키를 만든다.
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

## 파일 작업 (0.8.0)

입력창 `+` → 갤러리 / 파일 선택. 첨부는 최대 10개, 파일당 50 MiB이며 세션 초안에 보존한다. 전송 후 키보드는 닫힌다. 다른 앱의 공유 메뉴로 앱에 보내는 경로는 이번 범위에 포함하지 않는다.

각 GUI 쓰레드에 `files/workspaces/<GUI UUID>/` 하나를 생성한다. 미리 정한 하위 디렉토리나 공용 폴더는 없다. `/new`·`/resume`에서 같은 폴더를 사용한다. 기존 공통 런타임 폴더는 이동하지 않는다. 쓰레드 삭제 시 작업 파일도 삭제되며, Downloads·갤러리·사용자 지정 위치로 내보낸 사본은 유지된다. 앱 제거 시 내부 파일은 삭제된다.

새 세션에 `workspace_list/read/write/mkdir/download` 도구를 등록한다. **기존 Codex 세션은 `/new` 후 새 도구 사용 가능**. 셸·patch 실행은 계속 비활성이고 앱 어댑터가 경로 검증 후 작업한다. 디렉토리 분리는 OS sandbox가 아니다. UTF-8 읽기/쓰기는 1 MiB, 공개 HTTPS 다운로드는 50 MiB, 연결/읽기 타임아웃 15초, 스트림 처리 최대 90초. 취소는 스트림 확인 시점에 반영되며 이미 완료한 쓰기를 되돌리지는 않는다.

이미지는 localImage로 전달하며 HEIC 등은 Android 이미지 디코더가 지원하면 JPEG 사본으로 변환해 작업 폴더에 보관한다. PDF/Office 파서나 임의 스크립트 환경은 아직 없다. 파일명이 같으면 덮어쓰지 않고 새 이름을 붙이며 텍스트 덮어쓰기는 명시적 도구 인자가 필요하다.

결과 파일 카드 또는 쓰레드 파일 메뉴에서 미리보기·열기·공유·다른 이름으로 저장·Downloads 저장·갤러리 저장을 제공한다. 외부 열기는 읽기 전용 한 파일 URI 권한을 부여한다. 저장은 사본을 만들고 SHA-256 재조회로 검증한 뒤 완료 표시한다. 실패/취소 시 새 출력 정리를 시도한다.

서버 파일/세션 테스트와 Linux CLI 0.158.0 대조는 Android 런타임 0.156.1의 이미지 이해·Picker·Provider·MediaStore 검증을 대체하지 않는다. 해당 실기기 확인은 이슈 #29–#31에 남겨 둔다.
