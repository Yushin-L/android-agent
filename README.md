# Android Agent

Android 휴대폰을 에이전트의 실제 작업 환경으로 사용하는 개인용 앱이다. 폰 안에서 Codex App Server를 실행하고 모델 추론은 원격으로 수행한다.

현재 배포: **0.11.1 / versionCode25** · [APK 다운로드](http://140.245.79.96/android-agent/).
2026-10-01 기준 현황과 다음 작업 순서는 [기준선 정리 #44](https://github.com/Yushin-L/android-agent/issues/44)에 기록했다.

## 현재 사용할 수 있는 기능

| 영역 | 구현 내용 | 기록 |
|---|---|---|
| 대화 | GUI 쓰레드 생성·선택·삭제, /new·/resume, 쓰레드별 /model, 스트리밍·중단·명령 자동완성 | [M2](https://github.com/Yushin-L/android-agent/milestone/3) |
| 채팅 UI | 민트 라이트/다크·Pretendard, 사용자 우측 말풍선, Markdown·표·코드 복사, 개별/연속 도구 접기 | [#35](https://github.com/Yushin-L/android-agent/issues/35), [#37](https://github.com/Yushin-L/android-agent/issues/37), [#40](https://github.com/Yushin-L/android-agent/issues/40), [#43](https://github.com/Yushin-L/android-agent/issues/43) |
| 파일 | 사진/파일 첨부, 쓰레드별 작업 폴더, 파일 도구·Android 셸, 다운로드·저장·공유·다중 삭제 | [#29](https://github.com/Yushin-L/android-agent/issues/29), [#30](https://github.com/Yushin-L/android-agent/issues/30), [#31](https://github.com/Yushin-L/android-agent/issues/31), [#38](https://github.com/Yushin-L/android-agent/issues/38) |
| 탐색·미리보기 | 폴더 이동·검색·정렬, Markdown/HTML 읽기, 채팅 로컬 파일 링크 | [#34](https://github.com/Yushin-L/android-agent/issues/34), [#39](https://github.com/Yushin-L/android-agent/issues/39) |
| 실행 유지 | foreground service, 작업 알림, 실행 저널·Codex 기록 기반 복원 | [#18–#20](https://github.com/Yushin-L/android-agent/milestone/5) |
| 피드백 | 비공개 DB에 접수·질문·답변·배포 안내. 일회용 등록 코드, GitHub 로그인 불필요 | [#41](https://github.com/Yushin-L/android-agent/issues/41) |

알람·타이머 앱 연결, Notion/remote MCP, 홈 런처, 예약 작업, 자동 개발 실행·푸시 알림·자동 업데이트 설치는 아직 구현하지 않았다.

## 구조와 경계

- GUI 쓰레드는 `workspaces/<GUI UUID>/` 작업 폴더와 여러 Codex 대화 세션을 포함한다. GUI UUID와 Codex thread ID는 서로 다르다.
- Codex는 대화 원본·맥락·실행 이력을, 앱은 GUI 매핑·화면 상태·실행 상태를 관리한다.
- `/new`와 `/resume`은 같은 GUI 작업 폴더를 사용한다. 새로운 dynamic 도구는 `/new`로 생성한 대화에서 사용한다.
- 앱 파일 도구는 작업 폴더 경계를 검사한다. 네이티브 셸은 같은 Android 앱 UID로 실행되며 쓰레드 간 OS 격리를 보장하지 않는다.
- 서브에이전트는 자연어 요청으로 사용하며 별도 관리 UI는 두지 않는다. Android에서의 실제 서브에이전트 실행은 별도 검증 대상이다.
- 파일 삭제는 되돌릴 수 없고 쓰레드 삭제 시 내부 작업 폴더도 제거한다. 외부로 내보낸 사본은 유지한다.
- HTML은 읽기용 미리보기다. JavaScript 실행이나 임의 HTML 앱 실행 환경이 아니다.
- 피드백 내용은 공개 GitHub에 올리지 않는다. 앱에서 조회를 요청하면 서버 기록을 읽으며 개발 에이전트 자동 실행·실시간 알림은 없다.

## 검증 상태

Samsung SM-S931N / Android 16에서 Codex 기동·ChatGPT 인증 재사용·모델 응답·배터리 도구 왕복·후속 대화를 확인했다([#8](https://github.com/Yushin-L/android-agent/issues/8)). 제품 UI는 후속 사용자 사용 결과를 거쳐 0.11.1 기준 수용되었다. 실제 사용자 피드백 접수도 확인했다.

서버에서는 파일/세션/모델/실행 저널/문서 경계 등 10개 테스트 묶음과 비공개 피드백 API 왕복을 검증했다. APK 컴파일·서명과 공개 다운로드 바이트 일치도 확인했다. 각 실행 시점과 범위는 이슈 기록을 따른다. 이번 기준선 정리에서 테스트를 다시 실행한 것은 아니다.

남은 확인: Android 화면 꺼짐·OS 종료·재부팅·삼성 전원 정책, 파일 picker/저장/삭제 실패 조합, WebView와 링크, 큰 글씨·TalkBack·회전, 타 기기 및 16KB 페이지 호환성. 일반적인 UI 수용이나 서버 테스트를 이 항목들의 검증 완료로 해석하지 않는다.

## 마일스톤과 다음 단계

| 마일스톤 | 0.11.1 기준 상태 |
|---|---|
| [M0 · 제품 방향과 참조 조사](https://github.com/Yushin-L/android-agent/milestone/1) | 완료된 조사 기준선 |
| [M1 · Android Codex 실행 경로 검증](https://github.com/Yushin-L/android-agent/milestone/2) | 기동·인증·모델·배터리·후속 대화 기준선 완료 |
| [M2 · 쓰레드·세션 관리와 대화 UI](https://github.com/Yushin-L/android-agent/milestone/3) | 기반 구현 완료 · 현재 UI 수용 · 접근성/기기별 확인 별도 |
| [M3 · Android 행동 도구 확장](https://github.com/Yushin-L/android-agent/milestone/4) | 파일·문서·피드백 구현 · 앱 행동/외부 서비스 연결 미구현 |
| [M4 · 실행 수명과 중단 복구](https://github.com/Yushin-L/android-agent/milestone/5) | 실행 유지·복원 구현 · Android 수명/장애 검증 남음 |
| [M5 · 홈 통합과 배포 범위](https://github.com/Yushin-L/android-agent/milestone/6) | 홈·지원 범위·배포/운영 정책 후속 |

다음 구현 후보는 [알람·타이머와 앱 확인 화면 #36](https://github.com/Yushin-L/android-agent/issues/36)이다. [실행 안정성 M4](https://github.com/Yushin-L/android-agent/milestone/5) 검증과 [배포·피드백 운영 #22](https://github.com/Yushin-L/android-agent/issues/22)를 함께 관리한다. 이후 [Notion/MCP #24](https://github.com/Yushin-L/android-agent/issues/24), [홈 통합 #21](https://github.com/Yushin-L/android-agent/issues/21)로 확장한다. 이 순서는 제안이며 아직 다음 기능에 착수한 것은 아니다.

## 개발과 운영

- [앱 소스·빌드·버전별 구현 기록](app/README.md): `app/`. Android 10+ ARM64 빌드, targetSdk35, 런타임 `@mmmbuto/codex-cli-termux 0.156.1-termux.1`. 빌드 조건은 모든 기기 지원 약속이 아니다.
- [비공개 피드백 서버·관리 CLI](server/feedback/README.md): `server/feedback/`. SQLite, HTTPS, 장치별 인증. 자동 백업·데이터 삭제 정책·인증서 갱신은 후속 과제다.
- APK는 현재 HTTP로 배포하며 기존 개발 서명키를 유지해야 업데이트 설치가 가능하다. 피드백 API는 별도 전용 인증서를 사용하는 HTTPS다.
- [작업 규칙](AGENTS.md). 요구사항·설계·검증의 원본은 GitHub 마일스톤/이슈, 사용자 피드백 대화는 비공개 DB에서 관리한다.

`deploy/`, `docs/`, `docx/`, `reference/`, `experiments/`, `experiemtns/`는 로컬 자료로 보존하고 Git에서 제외한다. 기존 docs 9개는 기록 이슈 #1–#9에 이관했다. 새 계획을 docs에 쌓지 않는다. 인증정보·장치 토큰·서명키·DB·APK는 커밋하지 않는다.
