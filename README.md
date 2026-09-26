# Android Keyboard — 한글 + 개발자용 키보드

한글 두벌식과 영문 QWERTY를 지원하는 안드로이드 입력기(IME)입니다.
터미널, 코딩, 원격 데스크탑에서 쓰기 편하도록 Ctrl·Alt·Tab·방향키·Home/End 같은 키를 기본 자판에 넣었습니다.
UI는 Jetpack Compose로 그리고, 한글 조합은 직접 구현한 오토마타로 처리합니다.

## 기능

### 입력
- **한글 두벌식**: 겹받침(ㄳ ㄵ ㄺ …), 복합 모음(ㅘ ㅙ ㅢ …), 받침 자동 이동(`안`+`ㅏ` → `아나`)
- **영문 QWERTY**, 한/영 전환
- **Shift 3단계**: 해제 → 한 글자만 → 고정
- **Shift+Enter**: 전송하지 않고 줄바꿈

### 개발자·터미널 키
- 맨 윗줄: `tab` `alt` `del` `home` `end` `📋` `pgup` `pgdn` `+` `-` `=`
- 특수문자 줄 `` ~ ` | / \ { } [ ] _ `` 과 숫자 줄
- **Ctrl / Alt**: 누르면 다음 키 하나에 조합됩니다(스티키 방식). 한글 자판에서도 같은 자리의 영문 키로 바꿔 보내므로 `Ctrl+C` 같은 조합이 그대로 동작합니다.
- **방향키**: 하단 줄에 배치했고, 꾹 누르면 반복 입력됩니다.
- **기호 자판(?123)**: 오른쪽 4열이 계산기식 숫자패드입니다.

### 원격 데스크탑 대응
- Backspace, Delete, 방향키, Home/End 등은 **실제 키 이벤트**로 전송해서 원격 앱에서도 동작합니다.
- **한/A 길게 누르기**: 오른쪽 Alt(한/영 키)를 보내 원격 PC의 한/영을 전환합니다.

### 분할 키보드 · 클립보드
- 앱 설정에서 가운데 공백 폭을 조절하면 분할 키보드로 쓸 수 있습니다.
- `📋` 버튼을 누르면 클립보드 기록이 표시되고, 항목을 누르면 붙여넣어집니다. 분할 중에는 가운데 빈 공간에, 분할하지 않을 때는 키보드 위에 표시됩니다.

### 편의
- 키 사이 간격은 보이기만 할 뿐, 누르면 가장 가까운 키가 입력됩니다(데드존 없음).
- 키를 누를 때 햅틱 피드백이 있습니다(시스템 키보드 진동 설정을 따름).
- 숫자·전화번호 입력칸에서는 기호 자판으로 시작합니다.

## 개인정보

- **인터넷 권한이 없습니다.** 입력한 내용이 기기 밖으로 나가지 않습니다.
- 클립보드 기록은 **메모리에만** 보관하고, 저장하거나 전송하지 않습니다. 키보드 프로세스가 종료되면 사라집니다.
- 기기에 저장하는 것은 분할 공백 폭 같은 키보드 설정값뿐입니다.

## 설치와 사용

1. 앱을 설치하고 **한글 키보드**를 실행합니다.
2. **키보드 켜기**를 눌러 시스템 설정에서 입력기를 활성화합니다.
3. **키보드 선택**을 눌러 이 키보드로 전환합니다.
4. 앱 화면에서 분할 간격을 조절하고, 테스트 입력칸에서 바로 써볼 수 있습니다.

## 빌드

Android Studio로 이 폴더를 열거나, 명령줄에서 빌드합니다.

```bash
# Gradle Wrapper jar가 없으면 한 번 생성합니다
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
# 결과물: app/build/outputs/apk/debug/app-debug.apk
```

> 저장소에는 `gradle-wrapper.jar` 바이너리를 넣지 않았습니다. Android Studio로 열면 자동으로 생성됩니다.

요구 사항: JDK 17, Android SDK 34 (minSdk 24)

## 구조

```
app/src/main/
├── AndroidManifest.xml            IME 서비스와 런처 액티비티 등록
├── res/xml/method.xml             입력기 메타데이터(한국어/English 서브타입)
└── java/com/example/hangulkeyboard/
    ├── ImeService.kt              InputMethodService. 입력 처리, Ctrl/Alt, 클립보드, 키 이벤트 전송
    ├── MainActivity.kt            설정 화면(활성화 안내, 분할 폭 조절, 테스트 입력칸)
    ├── hangul/HangulComposer.kt   두벌식 한글 오토마타
    └── ui/
        ├── KeyboardLayouts.kt     한글/영문/기호 자판 정의
        └── KeyboardView.kt        Compose 키보드 UI(분할, 클립보드 패널 포함)
```

### IME에서 Compose를 쓰는 방법

IME 윈도우는 `Activity`가 아니라서 Compose에 필요한 오너가 없습니다.
그래서 `ImeService`가 `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner`를 직접 구현하고,
`onCreateInputView()`에서 만든 `ComposeView`에 연결합니다.

## 라이선스

[MIT](LICENSE)
