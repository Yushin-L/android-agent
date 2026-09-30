package dev.androidagent.probe;
import java.util.Locale;
/** Classify locally; no arbitrary server text is exported. */
public final class AuthDiagnostics {
 public static String category(String raw) {
  String s=raw==null?"":raw.toLowerCase(Locale.ROOT);
  if(s.contains("dns")||s.contains("resolve host")||s.contains("name resolution")||s.contains("lookup address"))return "DNS";
  if(s.contains("certificate")||s.contains("tls")||s.contains("ssl"))return "TLS";
  if(s.contains("address already in use")||s.contains("bind"))return "LOCAL_CALLBACK_BIND";
  if(s.contains("device")&&(s.contains("disabled")||s.contains("not enabled")))return "DEVICE_LOGIN_DISABLED";
  if(s.contains("403")||s.contains("forbidden"))return "HTTP_FORBIDDEN";
  if(s.contains("429")||s.contains("too many requests"))return "RATE_LIMIT";
  if(s.contains("timed out")||s.contains("timeout"))return "TIMEOUT";
  if(s.contains("connect")||s.contains("error sending request")||s.contains("network"))return "NETWORK_REQUEST";
  if(s.contains("401")||s.contains("unauthorized"))return "UNAUTHORIZED";
  return "UNCLASSIFIED";
 }
 public static String hint(String category) {
  switch(category){
   case "DNS":return "Codex의 주소 조회 실패. 폰 네트워크와 Linux 바이너리의 DNS 경로를 확인해야 합니다.";
   case "TLS":return "HTTPS 인증서 또는 TLS 연결 실패입니다.";
   case "LOCAL_CALLBACK_BIND":return "로그인 콜백 포트를 열지 못했습니다. 다른 로그인을 종료하거나 기기 코드 방식을 시도하세요.";
   case "DEVICE_LOGIN_DISABLED":return "계정의 기기 코드 로그인이 비활성화되어 있습니다. 브라우저 로그인을 시도하세요.";
   case "HTTP_FORBIDDEN":return "인증 서버가 요청을 거절했습니다(403). 계정 설정 문제라고 단정할 수 없습니다.";
   case "RATE_LIMIT":return "인증 서버 요청 한도에 도달했습니다. 잠시 후 다시 시도하세요.";
   case "NETWORK_REQUEST":return "인증 서버 연결에 실패했습니다. 로그인 화면을 열기 전 통신 오류일 수 있습니다.";
   case "TIMEOUT":return "인증 요청 또는 사용자 로그인을 기다리다 시간이 초과됐습니다.";
   default:return "진단 결과를 복사해 주세요. 이 오류만으로 원인을 확정할 수 없습니다.";
  }
 }
}
