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

## GitHub 빌드 및 APK 다운로드

현재 서명 빌드는 `layland/MyParkingAndroid`의 `build/myexercise` 브랜치에서 기존 Android Secrets를 사용합니다.
MyExercise 소스 저장소의 자체 서명 빌드는 동일한 Secrets를 등록한 후 사용할 수 있습니다.

- 테스트와 lint, 컴파일 통과 후 release APK를 빌드하고 서명을 검증합니다.
- 서명 빌드 시 `myexercise-v1.0.N` 태그의 최대 N 다음 번호를 예약합니다. 실패나 재시도 시 번호가 건너뛸 수 있으며 기존 번호를 재사용하지 않습니다.
- versionName은 `1.0.N`, versionCode는 `N+1`로 기존 첫 APK(versionCode 1) 위에 업데이트할 수 있습니다.
- 앱 이름 옆에 버전이 표시되며 `MyExercise-1.0.N.apk` 파일명과 APK 내부 버전이 일치하는지 CI에서 확인합니다.
- APK는 ZIP artifact로 배포하지 않고 GitHub Release에 직접 게시합니다. 빌드 보고서는 별도 artifact입니다.
- 주차앱과 구분된 태그를 사용하고 주차앱의 최신 Release 표시를 변경하지 않습니다.

다운로드: https://github.com/layland/MyParkingAndroid/releases 에서 **MyExercise** Release의 `.apk` 파일을 선택하세요.

GitHub Actions Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
서명 키는 저장소나 로그에 노출하지 않습니다. 앱 업데이트에는 같은 서명 키를 계속 사용해야 합니다.

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

## 화면 구성

Seconds의 큰 타이머·구간별 색상과 Material 3의 정보 계층을 참고했습니다.
타이머와 조작부는 고정하고 운동·음성 설정은 탭으로 분리했습니다. 숫자 입력은 짧은 폭으로, 멘트는 여러 줄로 표시하며 화면 폭과 글씨 크기에 따라 배치를 조정합니다. 저장 버튼은 시스템 UI와 키보드 여백 안에서 유지됩니다.

- https://www.intervaltimer.com/app
- https://m3.material.io/styles/color/the-color-system
- https://developer.android.com/develop/adaptive-apps

### 타이머 화면 시안 반영

승인된 화면 시안에 맞춰 원형 잔여 시간 표시, 고정된 시작·초기화 영역, 세그먼트 형태 탭과 짧은 숫자 입력 행을 사용합니다. 설정 저장은 스크롤 영역 안에서 설정 바로 아래에 배치합니다. 작은 화면·큰 글자·키보드 표시에서는 타이머를 축약하고 설정 영역을 스크롤합니다. 밝은 배경의 시스템 바는 어두운 아이콘을 사용합니다. Google Material 3의 컴포넌트·접근성 기준을 참고해 직접 구현했으며 외부 템플릿 코드를 복사하지 않았습니다.

### 호흡 운동

운동 설정에서 **호흡 운동**을 선택하고 설정을 저장합니다. 기본값은 들이마시기 5초, 내쉬기 10초이며, 시간과 반복 횟수를 바꿀 수 있습니다. 음성 코치 탭에서 들이마시기·내쉬기·완료 멘트를 수정할 수 있습니다. 모드 선택은 기본 시간·멘트를 불러오며 저장해야 적용됩니다.

각 구간의 시작 멘트 후 1초마다 ‘하나, 둘, 셋…’으로 경과 시간을 읽고 다음 구간에서 하나부터 다시 셉니다. 마지막 반복의 내쉬기까지 완료합니다. 구간 끝의 마지막 숫자와 다음 구간 멘트는 TTS 큐에 순서대로 넣어 서로 끊지 않게 합니다. 긴 멘트나 느린 기기 음성 엔진에서는 음성 큐가 실제 타이머보다 늦어질 수 있으므로 짧은 멘트를 권장합니다. 일반 운동의 역순 카운트다운과 응원 안내는 기존 방식대로 동작합니다.
