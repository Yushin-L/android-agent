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
python3 app/prepare_markdown.py
docker run --rm --network none --user "$(id -u):$(id -g)" -v "$PWD/app:/work" android-agent-builder
```

출력: `app/artifacts/android-agent-0.11.0-arm64.apk`. 빌드 과정에서 로컬 개발 서명키를 만든다.
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

## 0.8.1: Codex 기본 파일 실행 복원

0.8.0의 셸/patch 비활성 설명은 이 버전에서 대체된다. `features.shell_tool=true`, `workspace-write`, `on-request`로 새 대화와 재개 대화를 구성한다. Android에서는 `/system/bin/sh`와 `login=false`를 사용하도록 안내한다. Bash/Python/Node를 APK에 추가하지 않았으며 없는 실행 환경을 가정하지 않는다. Codex가 실행/파일 변경 승인을 요청하면 명령·위치·변경 내용을 보여주고 이번 작업의 허용/거절을 전달한다. 요청된 제한을 자동으로 해제하거나 모든 승인을 자동 수락하지 않는다.

Android 앱 UID의 접근 범위에서 실행한다. 참조 런타임의 플랫폼 sandbox 선택에는 Android용 별도 프로세스 격리가 없으므로 `workspace-write` 이름을 쓰레드 간 OS 보안 경계라고 설명하지 않는다. 다른 앱의 비공개 파일 접근 권한을 얻는 기능도 아니다.

앱 서버 연결 시 `command/exec`로 임시 폴더에 생성→복사→이동→읽기를 수행하고 정리한다. 설정/진단의 `shellProbe=PASS_CREATE_COPY_MOVE`는 폰의 이 작은 점검이 통과했다는 의미이며 모든 셸 도구/샌드박스/백그라운드 동작 검증을 뜻하지 않는다. 실패 시 sandbox 정책을 풀어 재시도하지 않는다.

`imageGeneration.savedPath`를 보존하고 생성 완료 시 해당 Codex 세션의 `CODEX_HOME/generated_images/<session>/`에서 쓰레드 폴더로 사본을 만든다. 원본을 보존하고 재개 시 같은 파일을 중복 생성하지 않는다. 경로 부재·실패·다른 세션 경로·심볼릭 링크·충돌은 오류로 표시한다. 명령 실행·파일 변경·생성 이미지 결과는 채팅에 표시되며 파일 메뉴와 연결된다. 이미 저장된 과거 생성 이미지도 대화 재개 시 경로가 유효하면 가져온다.

서버 검증에는 모델/계정 호출 없이 실제 Linux App Server `command/exec`의 생성·복사·이동 및 파일 바이트 검증을 포함한다. 이 테스트만 Docker `--network none`을 외부 sandbox로 지정하며 앱 정책은 `workspace-write`를 유지한다. Android 실기기 실행은 자동 점검 및 후속 사용자 사용 결과로 확인해야 한다.

## 0.8.2: 명령 출력 접기·펼치기

명령 실행은 기본적으로 상태 헤더만 표시한다. 탭하면 명령·출력·종료 코드·파일 메뉴가 나타난다. 세션/항목 ID별 펼침 상태는 화면 갱신·쓰레드 이동·회전에서 유지한다. 실행 승인 화면은 변경하지 않는다. 설계·검증 기록: [#33](https://github.com/Yushin-L/android-agent/issues/33).


## 0.9.0: 백그라운드 실행

앱을 열면 사용자 시작 foreground service가 유지된다. 설정/실행 알림에서 종료할 수 있다. API 34+에는 로컬 에이전트 조율 용도의 specialUse와 manifest 설명을 선언하고, 이전 API에는 dataSync를 사용한다. ARM64의 API 30 리소스 링커 호환을 위해 서비스 유형은 정수 리소스 `0x40000001`로 선언하되 실행 시 OS에 맞는 한 유형만 선택한다.

작업 중에는 최대 120초의 partial wake lock을 45초마다 갱신하고, 대기 상태와 종료 시 해제한다. 완료·중단·승인 요청 알림을 누르면 해당 대화로 이동한다. Android 13+ 알림 권한을 요청하며 설정에서 알림과 배터리 제한을 관리할 수 있다. Doze에서는 배터리 제한 제외가 필요할 수 있고 제조사 전원 정책도 적용된다.

START_STICKY와 부팅/업데이트 수신기는 켜 둔 서비스를 복원한다. 사용자가 서비스 종료를 선택하면 재부팅으로 되살리지 않는다. Android 설정의 강제 중지는 우회하지 않으며 예약 실행은 포함하지 않는다.

실행 전 원자적 저널에 세션·턴·도구 식별자 및 상태만 기록한다. 프롬프트·명령·인증은 저널에 기록하지 않는다. 재생성 시 Codex 기록을 조회하며 프롬프트/도구를 자동 재실행하지 않는다. 미완료 기록은 결과 미상으로 표시하고 사용자가 기록을 확인한 뒤 새 요청을 보낸다. 도구 반환 기록은 외부 부작용의 정확히 한 번 수행을 보장하지 않는다. 마지막 GUI 쓰레드와 대화 선택도 복원한다.

`ExecutionJournalTest`는 원자적 저장 실패·오래된 이벤트·완료 후 늦은 ACK·결과 미상 복원을 검사한다. `RealRecoveryTest`는 별도의 인증 없는 Linux Codex 자식 프로세스를 실행 중 강제 종료한 뒤, 재시작 후 read/resume이 새 턴을 만들지 않는지 검사한다. Android의 화면 꺼짐·최근 앱 제거·재부팅·OS 앱 종료와 삼성 배터리 정책 검증은 실기기 확인 전까지 미완료다. 설계와 증거는 [#20](https://github.com/Yushin-L/android-agent/issues/20)에 기록한다.


## 0.9.1: 쓰레드 파일 탐색기

쓰레드 파일은 경로 단계와 상위 폴더 이동을 갖춘 넓은 탐색 화면으로 표시한다. 폴더 우선 이름순 정렬, 크기순 정렬, 현재 폴더 이름 검색, 새 폴더 생성·새로고침을 제공한다. 파일 탭은 미리보기/열기, 더보기는 공유·폰에 저장 메뉴다. 종류 아이콘과 이름·크기를 분리하며 기존 색상과 글꼴을 재사용한다. 뒤로가기는 상위 폴더, 파일 홈에서는 대화로 돌아간다. 회전 시 현재 폴더·검색·정렬·목록 위치를 복원한다. 최대 1,000개 항목 표시 한계는 화면에 안내한다.

APK 컴파일·서명 및 정적 검토 범위이며 실기기 레이아웃·큰 글씨·TalkBack 검증은 별도다. 설계·증거: [#34](https://github.com/Yushin-L/android-agent/issues/34).


## 0.9.2: 공통 팝업과 키보드 스크롤

앱 소유 대화상자와 팝업 메뉴에 Pretendard 리소스 글꼴, 민트 라이트/다크 표면, 둥근 모서리와 터치 ripple을 적용한다. 대화상자는 짧은 등장/퇴장 효과를 사용하며 OS 애니메이션 설정을 따른다. 기존 assets의 라이선스된 글꼴을 빌드 시 Android font 리소스로도 묶어 기본 프레임워크 위젯에 적용한다. 사진/파일 선택기 등 시스템 소유 창은 시스템 스타일을 따른다.

새 쓰레드 제목과 입력 영역은 24dp 좌우 여백으로 맞췄다. 채팅 입력란을 탭하거나 키보드로 채팅 영역이 줄어들면 최근 메시지를 스크롤해 보여준다. 메시지 자동 스크롤은 입력 포커스를 빼앗는 fullScroll 대신 좌표 이동을 사용한다. 과거 대화를 읽을 때 계속 바닥에 고정하지 않는다.

APK 빌드·리소스 링크·서명과 정적 검토 범위다. 실기기 키보드 전환/글꼴/모서리/큰 글씨/TalkBack은 별도 확인이 필요하다. 기록: [#35](https://github.com/Yushin-L/android-agent/issues/35).


0.9.3은 우상단 메뉴의 명령어 표기를 제거하고 48dp 행 기준·16sp 글꼴·16dp 좌우 여백으로 정돈한다. 채팅의 `/new`, `/resume`, `/model` 명령은 유지한다. APK 빌드 검증과 실기기 시각 확인을 구분하며 기록은 #35를 따른다.


## 0.10.0: Markdown·문서 미리보기·파일 삭제

에이전트 답변과 Markdown 파일은 CommonMark 0.24.0 및 GFM 표/취소선 확장으로 해석하고 네이티브 View로 표시한다. 제목·강조·목록·인용·링크·코드 복사와 표/코드 가로 스크롤을 제공한다. 코드 하이라이팅·수식·Mermaid는 포함하지 않는다. Markdown 이미지 문법은 자동 다운로드 대신 이미지 링크로 표시한다. 사용자 메시지와 실행 로그는 원문을 유지한다. 갱신은 100ms 단위로 모으고 파싱은 별도 스레드에서 수행하며 완료된 메시지 뷰를 재사용한다. 20만 자 초과는 안내와 함께 원문으로 표시한다.

문서 미리보기에는 원문 전환을 제공한다. 기존 UTF-8 읽기 한계는 1MiB다. HTML은 JavaScript/폼/네트워크/file/content 접근을 막은 WebView에서 읽고, 검증한 같은 쓰레드의 CSS/이미지/폰트만 가상 HTTPS origin으로 제공한다. 외부 링크는 사용자 탭 시 브라우저로 연다. HTML 기반 앱 실행 기능은 아니다. 링크·리소스 정책 테스트와 Android WebView 실제 표시 검증은 구분한다.

파일/폴더 더보기에서 삭제할 수 있다. 확인 창을 거쳐 하위 항목까지 삭제하며, 실행 중인 쓰레드와 파일 작업이 있으면 거부한다. 작업 폴더 루트/경로 탈출을 차단하고 하위 symlink 대상은 따라가지 않는다. 삭제 후 첨부 초안을 정리하며, 생성 이미지가 재개 시 자동으로 다시 복사되지 않도록 삭제 경로를 저장한다. 부분 실패는 남은 목록을 확인하도록 표시한다. 외부 사본과 Codex 원본 기록은 유지한다.

의존성은 `markdown-dependencies.json`의 버전·SHA-256으로 고정하며 `prepare_markdown.py`로 준비한다. 오프라인 빌드는 무결성 검사를 통과해야 한다. 바이너리는 downloads에만 두고 Git에서 제외하며 BSD-2-Clause 고지는 `assets/COMMONMARK-LICENSE`에 포함한다. `DocumentFeaturesTest`는 Markdown 구문/불완전 스트림, URL/로컬 경계, 삭제/초안 정리/삭제 경로 지속성을 검증한다. 실제 Android 렌더링·복사·WebView·터치·큰 글씨는 별도 실기기 확인 대상이다.

## 0.10.1: 도구 그룹·파일 선택

연속 도구 2개 이상을 바깥 접기 그룹으로 묶는다. 파일을 길게 눌러 체크박스로 여러 항목을 선택해 삭제하거나, 왼쪽으로 밀어 삭제 확인을 연다. 현재 폴더의 표시 항목만 선택하며 폴더 이동 시 해제한다. 전체 경로를 검사한 뒤 순서대로 삭제하고 부분 실패 시 완료 개수를 안내한다. 원자적 일괄 삭제는 아니다. 설계·검증: [#40](https://github.com/Yushin-L/android-agent/issues/40). Android 제스처 실기기 확인은 별도 필요하다.

## 0.10.2: 채팅 파일 링크

현재 쓰레드 파일의 절대경로·file:/// URI·상대경로 링크를 채팅에서 직접 미리보기로 연다. 다른 쓰레드나 작업 폴더 밖 파일은 거절한다. 한글·공백 URL 인코딩을 처리하며 문서 fragment 위치 이동은 지원하지 않는다. [#39](https://github.com/Yushin-L/android-agent/issues/39).

## 0.11.0: 앱 피드백 접수

설정에서 일회용 코드로 피드백 서버를 연결한다. 새 대화에서 명시적으로 접수를 요청하면 submit_app_feedback 도구가 전송 내용을 확인받고 서버에 전달한다. 기본 포함 정보는 앱·Android 버전이며 대화/파일/로그 자동 첨부는 없다. 성공한 경우에만 피드백 접수 번호를 돌려준다. 접수 목록과 개발 측 질문·배포 안내를 조회하고 텍스트 답변을 보낼 수 있다. 자동 수정·배포·푸시 알림은 포함하지 않는다. GitHub 로그인은 폰에서 필요하지 않다. API는 전용 TLS 인증서를 사용하는 독립 HTTPS 연결이며 기존 ChatGPT 로그인에는 영향을 주지 않는다.
