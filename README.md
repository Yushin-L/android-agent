# Android Agent

Android 휴대폰 자체를 에이전트의 작업 환경으로 만드는 프로젝트다. 사용자가 원하는 결과를 말하면 폰의 상태를 관찰하고, 허용된 Android 기능과 도구를 실행하고, 결과를 확인하며 작업을 이어가는 것을 목표로 한다.

## 작업 관리

요구사항·설계·진행 상황의 원본은 [GitHub 마일스톤](https://github.com/Yushin-L/android-agent/milestones)과 연결된 [이슈](https://github.com/Yushin-L/android-agent/issues)다. 새로운 작업은 해당 마일스톤의 이슈에서 시작하고, 결정과 완료 증거도 그 이슈에 기록한다. 로컬 docs는 과거 기록으로 보존하며 새 작업 문서를 쌓지 않는다.

| 마일스톤 | 상태 |
|---|---|
| [M0 · 제품 방향과 참조 조사](https://github.com/Yushin-L/android-agent/milestone/1) | 완료된 기준선 |
| [M1 · Android Codex 실행 경로 검증](https://github.com/Yushin-L/android-agent/milestone/2) | 완료된 기준선 |
| [M2 · 쓰레드·세션 관리와 대화 UI](https://github.com/Yushin-L/android-agent/milestone/3) | 구현 완료 · APK 0.7.5 |
| [M3 · Android 행동 도구 확장](https://github.com/Yushin-L/android-agent/milestone/4) | 파일 작업 구현 · APK 0.8.2 · 실기기 확인 대기 |
| [M4 · 실행 수명과 중단 복구](https://github.com/Yushin-L/android-agent/milestone/5) | 백그라운드·기록 복원 구현 · APK 0.9.0 · 실기기 확인 대기 |
| [M5 · 홈 통합과 배포 범위](https://github.com/Yushin-L/android-agent/milestone/6) | 후속 작업 |

## 현재 합의

- GUI 쓰레드는 여러 Codex 대화 세션을 포함하는 작업 공간이다.
- GUI에서 쓰레드를 생성·선택하고, 쓰레드 안에서 `/new`·`/resume`으로 대화를 관리한다.
- 대화 본문·맥락·실행 이력은 Codex가 관리한다. 앱은 GUI 매핑과 화면 상태를 저장한다.
- 서브에이전트는 사용자의 대화 명령으로 활용하며, 기본 UI에 별도의 관리 화면이나 하위 작업 트리를 두지 않는다.
- 디자인은 Linear의 차분한 구성, Material 3의 Android 상호작용, 민트 회색·녹색, Pretendard를 기준으로 한다.

첨부·작업 폴더·파일 활용·팔레트의 설계와 검증은 [#29](https://github.com/Yushin-L/android-agent/issues/29), [#30](https://github.com/Yushin-L/android-agent/issues/30), [#31](https://github.com/Yushin-L/android-agent/issues/31), [#32](https://github.com/Yushin-L/android-agent/issues/32)에서 관리한다. 입력창 `+`로 사진/파일을 고르고, 대화 메뉴의 **쓰레드 파일**에서 열기·저장·공유한다. 0.8.1은 기존 대화 재개 시에도 Codex 기본 셸·파일 실행 설정을 적용하고 생성 이미지 보관을 연결한다. 앱 자체 dynamic 파일 도구는 `/new`로 만든 대화부터 제공한다.

글꼴·화면 구조는 [설계 합의 #9](https://github.com/Yushin-L/android-agent/issues/9)를 따르고 색상은 [#32](https://github.com/Yushin-L/android-agent/issues/32)로 갱신했다. 제품 소스와 빌드 방법은 [app/README.md](app/README.md), 진행 증거는 [제품 앱 소스·빌드 구조 #10](https://github.com/Yushin-L/android-agent/issues/10)을 참고한다.

M3의 앱·서비스 연결 설계와 실제 구현 근거는 [#23](https://github.com/Yushin-L/android-agent/issues/23), Notion MCP 후속 구현은 [#24](https://github.com/Yushin-L/android-agent/issues/24)에 있다. [APK 다운로드](http://140.245.79.96/android-agent/)에서 0.9.2를 받을 수 있다. 공통 팝업·키보드 UI 개선은 [#35](https://github.com/Yushin-L/android-agent/issues/35)에서 추적한다. 파일 탐색기 UI는 [#34](https://github.com/Yushin-L/android-agent/issues/34)에서 추적한다. 백그라운드 실행과 종료 복원은 [#20](https://github.com/Yushin-L/android-agent/issues/20), [#18](https://github.com/Yushin-L/android-agent/issues/18), [#19](https://github.com/Yushin-L/android-agent/issues/19)에서 추적한다.

## 검증된 범위

로컬 진단 APK를 통해 Samsung SM-S931N / Android 16에서 Codex 기동, 저장된 ChatGPT 인증을 통한 모델 응답, 배터리 도구 왕복, 동일 Codex 스레드의 후속 대화를 확인했다. [검증 기록 #8](https://github.com/Yushin-L/android-agent/issues/8)에 버전별 결과와 미검증 범위를 보존했다.

M2 제품 UI는 구현·빌드 및 서버 테스트를 완료했다. 새 UI의 실기기 렌더링·TalkBack·서브에이전트의 실제 Android 실행은 미검증이다. 다른 기기 호환성, 셸·code-mode-host, Android 앱의 OS 종료 복구까지 검증됐다는 뜻은 아니다. 닫힌 조사·기록 이슈는 기준선 이관 완료를 뜻하며, 후속 기능은 열린 구현 이슈로 추적한다.

## 저장소 범위

제품 앱 소스는 `app/`에 있다. 고정 버전 런타임을 내려받아 무결성을 확인한 뒤 ARM64 Linux의 Docker 환경에서 APK를 빌드한다. 기존 진단 소스는 로컬 experiments에 보존한다.

다음 디렉토리는 로컬에 유지하고 Git에 올리지 않는다.

- `deploy/`
- `docs/` 및 `docx/`
- `reference/`
- `experiments/` 및 `experiemtns/`

기존 docs의 문서 9개는 각 마일스톤의 기록 이슈 #1–#9로 이전했다. 서버 절대 경로는 일반화하고, 저장소에 없는 상대 링크는 이슈 링크 또는 로컬 자료 표시로 바꿨다. 참조 저장소 원본 주소·기준 커밋과 실기기 보고서 주요 필드도 해당 이슈에 보존했다. 계정 설정·인증 토큰·서명키·빌드 산출물은 추적하지 않는다.

개발 작업 규칙은 [AGENTS.md](AGENTS.md)를 참고한다.
