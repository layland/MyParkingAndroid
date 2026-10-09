# MyExercise — 나의 운동 코치

Kotlin 및 Jetpack Compose 기반 Android 운동 보조앱입니다. Android 8.0 이상을 지원합니다.

## 사용 방법

1. 운동 시간, 휴식 시간, 세트 수를 입력하고 설정을 저장합니다.
2. 마지막 음성 카운트다운 시간과 운동 중 응원 간격을 지정합니다. 0은 해당 안내를 끕니다.
3. 시작·휴식·완료·응원 멘트를 편집합니다. 빈 멘트는 읽지 않습니다.
4. 시작 버튼으로 운동을 시작하고 필요하면 일시정지·계속·초기화를 사용합니다.

설정 변경은 준비 또는 완료 상태에서 가능합니다. 저장한 설정은 다음 운동에 적용됩니다.
TTS는 기기의 한국어 음성 엔진을 사용합니다. 한국어 음성 데이터가 없으면 설정 안내가 표시되고 타이머는 계속 동작합니다.
화면을 꺼도 foreground service와 wake lock으로 세션을 유지하며 알림에서 앱 열기와 초기화를 지원합니다.
앱 강제 종료나 기기 재시작 후 진행 중이던 운동은 자동 복구하지 않습니다. 제조사 배터리 정책에 따른 실제 기기 검증이 필요합니다.

## 구조

- `WorkoutEngine`: Android와 독립적인 세트 전환 및 일시정지 로직
- `SettingsRepository`: 설정 저장 계약과 SharedPreferences 구현
- `SpeechCoach`: 음성 안내 계약과 Android TTS 구현
- `WorkoutService`: 단조 시계 기준 타이밍, 알림, 백그라운드 실행
- `MainActivity`: Compose 화면과 사용자 입력 검증

UI 문자열은 Android 리소스에, 정책 범위와 기본 수치는 이름 있는 상수에 분리했습니다.

## GitHub Actions

`Android build` 워크플로는 단위 테스트, lint, debug 컴파일을 수행합니다.
main 푸시 또는 수동 실행 시 검증 성공 후 release APK를 빌드하고 서명을 검증합니다.
PR에서는 서명용 Secrets를 사용하지 않습니다.

저장소의 **Settings → Secrets and variables → Actions**에 다음 Secrets가 필요합니다.

| Secret | 내용 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 본인이 관리하는 release 키스토어의 Base64 값 |
| `ANDROID_KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `ANDROID_KEY_ALIAS` | 서명 키 별칭 |
| `ANDROID_KEY_PASSWORD` | 서명 키 비밀번호 |

키스토어와 비밀번호를 채팅이나 저장소에 올리지 마세요. 같은 앱의 업데이트에는 동일한 서명 키가 필요하므로 키를 안전하게 보관하세요.
Secrets가 누락되면 release 작업은 명확하게 실패하고 서명되지 않은 APK를 배포하지 않습니다.
성공한 실행의 **Artifacts → MyExercise-signed-apk**에서 APK를 내려받을 수 있습니다.

## 개발 및 검증

JDK 17, Android SDK 35, Gradle 8.11.1을 사용합니다. CI에서 해당 Gradle 버전을 설치하므로 wrapper 없이 실행합니다.

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

실제 기기에서는 한국어 TTS, 화면 꺼짐, 앱 전환, 일시정지, 알림 초기화 및 서명 APK 설치를 확인하세요.

## 서명 정보 자동 설정

MyParkingAndroid와 동일한 Secret 이름을 사용합니다. Secret 값은 저장소별로 별도 등록해야 합니다.
인증된 GitHub CLI와 JDK가 있는 본인 컴퓨터에서 다음 명령으로 새 키 생성, Secrets 등록, 실패한 서명 작업 재실행을 수행합니다.

```sh
bash scripts/setup-signing.sh
```

키와 비밀번호는 저장소 밖 사용자 설정 폴더에 권한을 제한해 저장됩니다. 스크립트가 표시하는 백업 폴더를 안전하게 보관하세요.
이 스크립트는 기존 Android 서명 Secrets가 있으면 덮어쓰지 않고 중단합니다.
