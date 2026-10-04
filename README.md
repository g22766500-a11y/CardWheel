# CardWheel 1.2.0 · MariaDB 직접 연결

기존 XML Views + Kotlin + Room Android 앱에 MariaDB JDBC 직접 백업·복원을 적용했습니다.
버전은 요청대로 **1.2.0 / versionCode 9** 그대로 유지합니다.

## 시작

1. ZIP을 풀고 Android Studio에서 CardWheel 폴더를 엽니다.
2. `database/setup.sql`과 `database/create-account.example.sql`로 기존 MariaDB에 전용 테이블·계정을 준비합니다.
3. 앱 상단 ‘백업’에서 DB 주소·포트(기본 3306)·DB 이름·계정·비밀번호를 입력합니다.
4. 일반 연결 또는 TLS를 선택하고 ‘연결 확인 및 저장’을 누릅니다.

자세한 설정은 **database/README.md**를 확인하세요. 추가 API 서버나 Docker 컨테이너는 필요하지 않습니다.

## 이번 변경

- API 방식에서 MariaDB JDBC 직접 연결로 변경했습니다. 연결은 백그라운드에서 열고 작업 후 닫습니다.
- 일반 연결을 기본값으로 두고 TLS를 선택할 수 있습니다. 일반 연결의 공개 인터넷 통신은 암호화되지 않습니다.
- TLS는 인증서 체인·호스트·유효기간을 확인하고 자체 서명/사설 CA용 PEM 인증서를 선택할 수 있습니다. 실패 시 일반 연결로 자동 전환하지 않습니다.
- DB 비밀번호와 접속 정보는 Android Keystore AES-GCM으로 암호화해 저장하며 시스템 백업에서 제외합니다. 비밀번호를 JDBC URL/오류/연결 정보 문자열에 출력하지 않습니다.
- 목록 확인 후 서버 백업, 목록 확인 후 기기 복원, 복원 전 목록으로 되돌리기를 유지합니다.
- 서버 revision 조건과 DB 트랜잭션으로 동시 덮어쓰기를 방지합니다. 로컬 복원도 Room 트랜잭션으로 처리합니다.
- 원래 Room schema version=2와 applicationId=com.example.cardwheel, DB 파일명=cardwheel_database를 유지합니다. 기존 카드의 모든 컬럼을 보존합니다.

가로형 카드/IC 칩, 카드사 드롭다운·배경색, 고정 카드 크기, FAB 전용 영역, 스와이프 햅틱, 날짜 선택, 상세 수정·삭제, 기본 검증 기능도 유지합니다.

## 빌드와 검증

원래 프로젝트의 SDK Platform 37.0, Build Tools 36.0.0, Gradle 9.6.0, AGP 9.4.1, JDK 25를 유지합니다. 새 드라이버는 MariaDB Connector/J 3.5.10이며 minSdk 24를 위해 Java 라이브러리 desugaring을 활성화했습니다.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease
```

검증 결과 (2026-10-04):

- 디버그·릴리즈 빌드 통과. 릴리즈 APK는 서명 전 파일입니다.
- Android 단위/화면/Room 회귀 테스트 25개 통과, 실패 0.
- lintDebug 오류 0, 경고 35개. 남은 경고는 의존성 업데이트·KTX/레이아웃/문자열 등 개선 제안입니다.
- 임시 MariaDB 11.4.9와 앱의 JVM 클래스/JDBC 드라이버로 일반 연결, 모든 필드 보존, 이전 revision 거부, 동시 저장, 잘못된 계정, PEM 인증서를 검증한 TLS, 신뢰하지 않는 TLS 인증서 거부, 빈 백업 등 8개 항목 통과.
- 두 APK의 TLS 서비스 프로바이더 리소스 포함 확인.

Robolectric 테스트는 Android 15(API 35) 환경입니다. 실제 Android 기기/사용자의 공개 NAS 서버 연결은 아직 확인하지 않았습니다. 작업 환경에서 Android 도구 캐시의 실행 제한을 피하기 위해 동일한 공식 AAPT2 실행 파일을 작업 폴더로 복사해 검증했으며 이 임시 경로는 프로젝트에 포함하지 않았습니다.

기존 앱을 업데이트하려면 같은 서명키로 빌드하세요. 앱 삭제/데이터 초기화 시 기기 Room 데이터와 복원 전 목록은 사라집니다.

ZIP에는 Android Studio 프로젝트, DB 준비 SQL·안내와 테스트 소스가 들어 있습니다. 빌드 캐시·SDK·서명키·실제 접속 정보·API 서버 프로그램은 포함하지 않습니다.
