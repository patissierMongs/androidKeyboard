# 한글 키보드 (Android IME · Jetpack Compose)

영문 QWERTY 와 한글 두벌식을 모두 지원하는 안드로이드 입력기(IME)입니다.
UI 는 Jetpack Compose 로 그리고, 한글 조합은 직접 구현한 오토마타로 처리합니다.

## 기능

- **한글 두벌식** 입력 — 초성/중성/종성 조합, 겹받침(ㄳ ㄵ ㄺ ㄼ …), 복합 모음(ㅘ ㅙ ㅢ …)
- **받침 자동 이동** — 받침 뒤에 모음이 오면 다음 글자의 초성으로 이동 (예: `안`+`ㅏ` → `아나`)
- **겹받침 분리 이동** — 예: `닭`+`ㅏ` → `달가`
- **영문 QWERTY** — Shift 로 대문자, 한 글자 입력 후 자동 해제
- **한/영 전환** 및 **숫자·기호 자판**
- **백스페이스** — 조합 중에는 자모 단위로, 아니면 글자 삭제
- 숫자/전화 입력칸에서는 자동으로 기호 자판으로 시작

### 폴드/대화면

- **접힘·펼침 프로파일** — smallestScreenWidthDp 600 기준으로 커버/메인 화면을
  구분해 분할 간격·키 높이·상단 보조줄 범위를 각각 저장. 접거나 펼치면 즉시 전환.
- **분할 키보드** — 각 줄 가운데(weight 절반 지점, 동률이면 왼손 글쇠 ㅎ·ㅍ·g 가
  왼쪽에 남는 지점)에 공백을 끼우되 키 폭·배치는 원래 그대로. 그 빈 칸 안에
  줄별로 내용이 키처럼 박힌다(양옆 8dp 오터치 데드존): 기본은 **커서 미니 키**
  (아래부터 선택 / ◀▶ / ▲▼ / esc·전체선택(Ctrl+A) / home·end / pgup·pgdn),
  📋 키를 켜면 **줄당 클립 항목 하나**로 전환. 분할 중에는 액션줄 방향키 자리가
  `! ( ) /` 로 바뀐다(가운데 미니 방향키가 대신함). 접은 화면에서는 📋 가
  가운데 대신 **키보드 위 한 줄 스트립**으로 뜬다.
- **선택 모드** — 커서 패드의 ‘선택’을 켜면 이동 키에 Shift 가 실려
  원격 데스크탑/터미널에서도 텍스트 선택이 된다.

### 원격 데스크탑 / 터미널

- **스티키 Ctrl/Alt** — 다음 키를 조합(META)으로 전송, 한글 자판에서도 자모→QWERTY 매핑
- **스페이스 슬라이드** — 스페이스를 누른 채 좌우로 끌면 즉시 커서 이동(지연 없음),
  탭이면 일반 공백. 선택 모드와 조합하면 끌어서 선택.
- **클립보드 히스토리** — 최근 20개 + 길게 눌러 고정(📌), 재부팅 후에도 유지
- **핀 모드** — 하드웨어 키보드 연결·뒤로 키에도 키보드 유지

## 구조

```
app/src/main/
├── AndroidManifest.xml                IME 서비스 + 런처 액티비티 등록
├── res/xml/method.xml                 입력기 메타데이터(한국어/English 서브타입)
└── java/com/example/hangulkeyboard/
    ├── ImeService.kt                  InputMethodService — Compose 를 IME 윈도우에 호스팅
    ├── MainActivity.kt                키보드 활성화 안내 런처 화면
    ├── hangul/HangulComposer.kt       두벌식 한글 오토마타(자모 조합 상태머신)
    └── ui/
        ├── KeyboardLayouts.kt         한글/영문/기호 자판 레이아웃 정의
        └── KeyboardView.kt            Compose 키보드 UI
```

### IME 안에서 Compose 를 띄우는 방법

IME 윈도우는 일반 `Activity` 가 아니라서 Compose 가 요구하는 오너가 없습니다.
그래서 `ImeService` 가 `LifecycleOwner`, `ViewModelStoreOwner`,
`SavedStateRegistryOwner` 를 직접 구현하고, `onCreateInputView()` 에서 만든
`ComposeView` 에 `setViewTree*Owner` 로 연결합니다.

## 빌드

Android Studio(Giraffe 이상)로 `android-keyboard/` 폴더를 열거나 CLI 로 빌드합니다.

```bash
cd android-keyboard
# Gradle Wrapper jar 이 없다면 한 번 생성
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

> 저장소에는 `gradle-wrapper.jar` 바이너리를 포함하지 않았습니다.
> Android Studio 로 열면 자동 생성되며, CLI 라면 위 `gradle wrapper` 로 만듭니다.

## 사용 (기기/에뮬레이터)

1. 앱 설치 후 **한글 키보드** 런처 아이콘 실행
2. **키보드 켜기** → 시스템 설정에서 이 입력기 활성화
3. **키보드 선택** → 입력기를 이 키보드로 전환
4. 아무 입력칸에서 한/영을 입력

## 한글 오토마타 검증

조합 로직은 다음 케이스로 검증했습니다(모두 통과):

| 입력(자모) | 결과 |
|---|---|
| ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ | 안녕하세요 |
| ㄷㅏㄹㄱ | 닭 |
| ㄱㅏㅂㅅ | 값 |
| ㅇㅗㅐ | 왜 |
| ㅇㅡㅣ | 의 |
| ㅇㅏㄴㅏ | 아나 (받침 이동) |
| ㄷㅏㄹㄱㅏ | 달가 (겹받침 분리) |
| ㄲㅗㅊ | 꽃 |
