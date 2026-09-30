# 비공개 피드백 서버

작업: [#41](https://github.com/Yushin-L/android-agent/issues/41). Python 3.12 표준 라이브러리 + SQLite. GitHub API·계정은 사용하지 않는다. `STATE_DIR`에 DB를 저장하고 TLS 인증서/키는 `/secrets/tls.crt`, `/secrets/tls.key`를 사용한다. HTTPS 피드백만 열려 있으며 DB·관리 CLI는 공개하지 않는다.

## 앱 API

모든 요청은 JSON POST. `/android-agent-feedback/pair`만 24시간 1회용 코드로 등록한다. 나머지는 Bearer 장치 토큰이 필요하다.

- `/submit`: requestId/title/body/category/appVersion/androidVersion → feedbackId/messageId.
- `/list`: 선택 before cursor → 해당 장치의 접수 목록, 상태, updated, nextBefore.
- `/read`: feedbackId, 선택 after cursor → 질문/답변/배포 안내, nextAfter. 페이지당 10개 메시지.
- `/reply`: requestId/feedbackId/body → 답변 접수 번호.
- `/revoke`: 현재 장치 토큰 폐기.

장치별 소유권을 검사한다. 접수/답변과 중복 방지 receipt를 한 DB 트랜잭션으로 저장한다. 같은 요청 ID와 내용은 같은 결과를 반환한다. 결과를 못 받은 앱은 성공을 주장하거나 자동 재접수하지 않는다. 장치별 하루 100회 쓰기 제한, 입력 길이/크기 제한, 코드 시도 제한 적용. 대화·파일·로그는 자동 첨부하지 않는다. 앱에서 전송할 텍스트를 확인받는다.

## 개발 측 관리

서버 로컬에서 `python3 server/feedback/admin.py --state deploy/feedback/state` 뒤에 다음 명령을 사용한다.

- `list`, `read ID`: 피드백/대화 확인.
- `comment ID --body-file /tmp/reply.txt --status needs_reply`: 질문 기록.
- `comment ID --body-file /tmp/release.txt --status released --version VERSION --download-url URL`: 검증된 배포 안내. 고정 APK 배포 호스트 URL만 허용한다.
- `pair`: 24시간 1회용 연결 코드 발급. 출력 코드는 비공개로 전달한다.
- `pair --recover-feedback ID`: 기존 피드백 소유자의 기록을 새 토큰으로 연결. 기존 토큰을 폐기한다. 앱 재설치/연결 해제 후 복구용으로 운영자가 소유자를 확인하고 사용한다.

상태: received/in_progress/needs_reply/released/closed. 개발 에이전트 자동 실행, 폰 푸시 알림, 자동 설치는 미구현이다. 사용자가 앱에서 진행 상황 확인을 요청하면 도구로 조회한다. 응답 텍스트를 실행 지시로 취급하지 않도록 개발자 프롬프트에 명시한다.

## 운영

현 배포는 외부 443 접근 불가로 80 포트에서 특정 HostSNI의 TLS만 backend로 통과시킨다. 주소는 `https://android-agent.140.245.79.96.sslip.io:80/android-agent-feedback/`. 기존 HTTP 다운로드는 유지한다. 앱 클라이언트만 전용 인증서를 신뢰하고 hostname 검증은 유지한다. sslip.io DNS에 의존하며 인증서 만료/교체 시 앱 인증서도 갱신해야 한다. APK 다운로드 자체는 기존 HTTP 배포다.

DB/키는 제외된 deploy/feedback 아래에 저장하고 웹 정적 폴더에는 두지 않는다. 운영자와 서버 관리자는 DB를 읽을 수 있으며 종단간 암호화는 아니다. 현재 단일 서버/SQLite이고 자동 백업·사용자 삭제 API는 후속 과제다.

테스트: `python3 -m unittest discover -s server/feedback -p 'test_*.py'`. 등록/인증/1회 코드/장치 격리/중복/재시작/답변/폐기를 검증한다.
