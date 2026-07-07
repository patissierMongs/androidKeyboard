package com.example.hangulkeyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.hangulkeyboard.hangul.HangulComposer
import com.example.hangulkeyboard.ui.ActionType
import com.example.hangulkeyboard.ui.AuxRows
import com.example.hangulkeyboard.ui.Key
import com.example.hangulkeyboard.ui.KeyboardMode
import com.example.hangulkeyboard.ui.KeyboardView
import com.example.hangulkeyboard.ui.ShiftState
import org.json.JSONArray

/**
 * Jetpack Compose 로 그리는 한/영 입력기(IME).
 *
 * IME 윈도우는 일반 Activity 가 아니므로 Compose 를 띄우려면
 * Lifecycle / ViewModelStore / SavedStateRegistry 오너를 직접 제공해야 한다.
 */
class ImeService : InputMethodService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val composer = HangulComposer()

    private val prefs by lazy {
        getSharedPreferences("ime_prefs", Context.MODE_PRIVATE)
    }

    // Compose 상태
    private var mode by mutableStateOf(KeyboardMode.KOREAN)
    // 시프트 3단계(해제/단일/지속). shifted 는 대문자·쌍자음 적용 여부.
    private var shiftState by mutableStateOf(ShiftState.OFF)
    private val shifted: Boolean get() = shiftState != ShiftState.OFF
    // 기호 모드 진입 전 자판을 기억해 돌아갈 수 있게 한다.
    private var previousMode = KeyboardMode.KOREAN
    // 핀(자동숨김 방지). 켜 두면 앱을 닫아도 유지된다.
    private var pinned by mutableStateOf(false)
    // Ctrl/Alt 스티키 모디파이어. 켜지면 다음 키를 조합(META)으로 전송한다.
    private var ctrlActive by mutableStateOf(false)
    private var altActive by mutableStateOf(false)
    // 분할 키보드 가운데 공백 폭(키 폭 단위). 0 이면 분할 안 함. 앱에서 조절.
    private var splitGap by mutableStateOf(0f)
    // 접힘/펼침 프로파일별 레이아웃: 상단 보조줄 범위, 키 높이(dp).
    private var auxRows by mutableStateOf(AuxRows.ALL)
    private var keyHeight by mutableStateOf(52f)
    // 접은(커버) 화면 여부. 접은 화면에선 클립보드를 상단 한 줄 스트립으로 띄운다.
    private var foldedProfile by mutableStateOf(false)
    // 선택 모드: 켜져 있는 동안 커서 이동 키에 Shift 를 실어 텍스트를 선택한다.
    private var selectActive by mutableStateOf(false)
    // 에디터에 선택영역이 있는지(onUpdateSelection 으로 추적). 조합 입력 전에
    // 선택영역을 명시적으로 지우는 데 쓴다.
    private var hasSelection = false

    // 클립보드: 자체 히스토리(최근 항목) + 고정 항목 + 표시 여부.
    private val clipboard by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    private val clipHistory = mutableStateListOf<String>()
    private val pinnedClips = mutableStateListOf<String>()
    private var showClipboard by mutableStateOf(false)
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    // 설정 앱에서 값을 바꾸면 키보드를 다시 열지 않아도 즉시 반영한다.
    // (prefs 는 리스너를 약참조로 들고 있으므로 필드로 강참조를 유지해야 한다.)
    private val prefsListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && (key.startsWith(PROFILE_FOLDED) || key.startsWith(PROFILE_UNFOLDED))) {
                loadProfile()
            }
        }

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        pinned = prefs.getBoolean(KEY_PINNED, false)
        loadClips()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        runCatching { clipboard.addPrimaryClipChangedListener(clipListener) }
    }

    override fun onCreateInputView(): View {
        // IME 윈도우의 decor view 에 ViewTree 오너를 심어 둔다. ComposeView 는
        // 부모 트리를 거슬러 올라가며 오너를 찾으므로, 자신뿐 아니라 부모 쪽에도
        // 오너가 있어야 어태치 시점에 안정적으로 컴포지션이 시작된다.
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ImeService)
            setViewTreeViewModelStoreOwner(this@ImeService)
            setViewTreeSavedStateRegistryOwner(this@ImeService)
            // IME 입력 뷰는 일반 Activity 와 어태치 수명이 달라, 기본 전략
            // (윈도우 분리 시 폐기)으로는 키보드가 한 번도 그려지지 않거나
            // 곧바로 사라진다. 라이프사이클이 살아 있는 동안 컴포지션을 유지한다.
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                KeyboardView(
                    mode = mode,
                    shiftState = shiftState,
                    ctrlActive = ctrlActive,
                    altActive = altActive,
                    splitGap = splitGap,
                    keyHeight = keyHeight,
                    auxRows = auxRows,
                    clips = clipHistory,
                    pinnedClips = pinnedClips,
                    showClipboard = showClipboard,
                    selectActive = selectActive,
                    clipInStrip = foldedProfile,
                    onKey = ::onKey,
                    onKeyLong = ::onKeyLong,
                    onPaste = ::onPasteClip,
                    onPinToggle = ::onPinToggle,
                    onToggleSelect = { selectActive = !selectActive },
                    // 초성만 있는 상태 → 다음 자모는 모음일 확률이 높다(경계 스냅용).
                    expectVowel = { mode == KeyboardMode.KOREAN && composer.expectingVowel }
                )
            }
        }
    }

    /** 키보드가 화면에 나타날 때 RESUMED 로, 사라질 때 멈춤 상태로 전환. */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        applyPinDisposition()
        // 접힘/펼침에 맞는 프로파일 설정을 반영(키보드가 뜰 때마다 최신값 반영).
        loadProfile()
        // 현재 클립보드 내용을 히스토리에 반영(변경 이벤트가 없어도 최신값 확보).
        captureClip()
    }

    /**
     * 접힘(커버)/펼침(메인) 화면에 따라 별도 저장된 레이아웃 설정을 읽는다.
     * 폴드 커버 화면은 smallestScreenWidthDp 가 600 미만, 메인 화면은 이상이다.
     */
    private fun loadProfile() {
        val folded = resources.configuration.smallestScreenWidthDp < 600
        foldedProfile = folded
        val p = if (folded) PROFILE_FOLDED else PROFILE_UNFOLDED
        splitGap = prefs.getFloat(p + KEY_SPLIT_GAP, if (folded) 0f else 2f)
        keyHeight = prefs.getFloat(p + KEY_KEY_HEIGHT, if (folded) 56f else 52f)
        auxRows = runCatching {
            AuxRows.valueOf(prefs.getString(p + KEY_AUX_ROWS, null) ?: "")
        }.getOrDefault(if (folded) AuxRows.TERMINAL else AuxRows.ALL)
    }

    /** 키보드가 떠 있는 채로 접거나 펼치면 즉시 해당 프로파일로 전환. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loadProfile()
    }

    /**
     * 커서/선택 변화 추적. 선택영역 존재 여부를 기억하고, 한글 조합 중에
     * 사용자가 터치 등으로 커서를 조합 영역 밖으로 옮기면 조합을 그 자리에서
     * 확정하고 오토마타를 리셋한다 — 안 그러면 다음 자모가 멀리 있는 이전
     * 글자에 합쳐지는 버그가 생긴다. (정상 조합 중에는 커서가 항상 조합
     * 영역(candidates) 끝에 온다.)
     */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        hasSelection = newSelStart != newSelEnd
        if (!composer.isEmpty &&
            (candidatesStart == -1 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
        ) {
            composer.flush()  // 글자는 이미 에디터에 있으므로 상태만 리셋하고
            currentInputConnection?.finishComposingText()  // 조합 영역을 확정한다.
        }
    }

    /**
     * 핀이 켜져 있으면 입력 뷰를 항상 표시한다. (가로 모드·하드웨어 키보드
     * 연결 등 기본적으로 숨겨지는 상황에서도 키보드를 계속 띄운다.)
     */
    override fun onEvaluateInputViewShown(): Boolean =
        pinned || super.onEvaluateInputViewShown()

    /** 핀이 켜져 있는 동안에는 뒤로 키로 키보드를 닫지 않는다. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (pinned && keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun applyPinDisposition() {
        setBackDisposition(
            if (pinned) BACK_DISPOSITION_WILL_NOT_DISMISS
            else BACK_DISPOSITION_DEFAULT
        )
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onFinishInputView(finishingInput)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composer.flush()
        shiftState = ShiftState.OFF
        selectActive = false
        // 숫자/전화 입력칸이면 기호 자판으로 시작
        attribute?.let {
            val cls = it.inputType and InputType.TYPE_MASK_CLASS
            if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) {
                // ?123 토글로 돌아갈 자판을 기억해 둔다(이전 previousMode 오염 방지).
                if (mode != KeyboardMode.SYMBOLS) previousMode = mode
                mode = KeyboardMode.SYMBOLS
            }
        }
    }

    override fun onFinishInput() {
        commitComposing()
        super.onFinishInput()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        runCatching { clipboard.removePrimaryClipChangedListener(clipListener) }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    /** 현재 클립보드 텍스트를 히스토리 맨 앞에 넣는다(중복 제거, 최대 20개). */
    private fun captureClip() {
        runCatching {
            val text = clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
            if (!text.isNullOrEmpty() && text !in pinnedClips) {
                clipHistory.remove(text)
                clipHistory.add(0, text)
                while (clipHistory.size > 20) clipHistory.removeAt(clipHistory.size - 1)
                persistClips()
            }
        }
    }

    /** 클립 항목 고정/해제. 고정 항목은 히스토리 만료와 무관하게 유지된다. */
    private fun onPinToggle(text: String) {
        if (pinnedClips.remove(text)) {
            clipHistory.remove(text)
            clipHistory.add(0, text)
        } else {
            pinnedClips.add(0, text)
            clipHistory.remove(text)
        }
        persistClips()
    }

    /** 히스토리/고정 항목을 저장해 프로세스가 죽어도 유지한다. */
    private fun persistClips() {
        prefs.edit()
            .putString(KEY_CLIP_PINNED, JSONArray(pinnedClips.toList()).toString())
            .putString(KEY_CLIP_HISTORY, JSONArray(clipHistory.toList()).toString())
            .apply()
    }

    private fun loadClips() {
        fun read(key: String): List<String> = runCatching {
            val raw = prefs.getString(key, null) ?: return@runCatching emptyList()
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
        pinnedClips.addAll(read(KEY_CLIP_PINNED))
        clipHistory.addAll(read(KEY_CLIP_HISTORY))
    }

    // ---- 입력 처리 ----

    private fun onKey(key: Key) {
        // 입력 처리 중 어떤 예외가 나도 키보드 프로세스가 죽지 않도록 방어한다.
        try {
            when (key) {
                is Key.Char -> onCharKey(key.output)
                is Key.Action -> onActionKey(key.type)
                is Key.KeyCode -> onKeyCodeKey(key.code)
                is Key.Gap -> Unit
            }
        } catch (t: Throwable) {
            // 조합 상태만 안전하게 정리하고 무시.
            runCatching { composer.flush() }
            clearMods()
        }
    }

    private fun onCharKey(text: String) {
        val ic = currentInputConnection ?: return
        // 글자 입력은 선택영역을 대치하므로 선택 모드를 먼저 끈다.
        // (Ctrl 조합도 마찬가지 — 선택 후 Ctrl+C 가 Shift 없이 온전히 나가야 한다.)
        selectActive = false
        // Ctrl/Alt 조합: 다음 키를 실제 키이벤트로 보낸다. 터미널/원격에서 Ctrl+C 등.
        // 한글 자판이면 자모를 그 자리의 QWERTY 키로 매핑해 조합을 유지한다(ㅂ→Q 등).
        if (ctrlActive || altActive) {
            commitComposing()
            val keyCode = latinKeyCode(text) ?: jamoKeyCode(text)
            if (keyCode != null) sendKeyWithMeta(ic, keyCode, activeMeta())
            else ic.commitText(text, 1)
            clearMods()
            return
        }
        if (mode == KeyboardMode.KOREAN && text.isNotEmpty() && isJamo(text[0])) {
            val committed = composer.input(text[0])
            ic.beginBatchEdit()
            // 선택영역이 있으면 먼저 지운다 — setComposingText 는 에디터에 따라
            // 선택영역을 대치하지 않고 커서 자리에만 끼어드는 경우가 있다.
            if (hasSelection) {
                ic.commitText("", 1)
                hasSelection = false
            }
            if (committed.isNotEmpty()) ic.commitText(committed, 1)
            ic.setComposingText(composer.composing, 1)
            ic.endBatchEdit()
        } else {
            commitComposing()
            ic.commitText(text, 1)
        }
        // 단일입력 시프트는 한 글자 입력 후 자동 해제(지속 모드는 유지).
        if (shiftState == ShiftState.SINGLE) shiftState = ShiftState.OFF
    }

    private fun onActionKey(type: ActionType) {
        val ic = currentInputConnection ?: return
        when (type) {
            // 시프트 3단계 순환: 해제 → 단일입력 → 지속 → 해제
            ActionType.SHIFT -> shiftState = when (shiftState) {
                ShiftState.OFF -> ShiftState.SINGLE
                ShiftState.SINGLE -> ShiftState.LOCKED
                ShiftState.LOCKED -> ShiftState.OFF
            }

            ActionType.BACKSPACE -> {
                if (composer.backspace()) {
                    ic.setComposingText(composer.composing, 1)
                } else {
                    // 선택 중이면 선택 모드를 끄고 플레인 백스페이스 → 선택영역 삭제.
                    selectActive = false
                    // 원격 데스크탑에서도 먹도록 실제 백스페이스 키 이벤트를 보낸다.
                    // (Ctrl+Backspace 는 단어 삭제)
                    sendKeyWithMeta(ic, KeyEvent.KEYCODE_DEL, activeMeta())
                    clearMods()
                }
            }

            ActionType.CTRL -> ctrlActive = !ctrlActive
            ActionType.ALT -> altActive = !altActive
            ActionType.CLIPBOARD -> showClipboard = !showClipboard

            // 전체선택/복사/붙여넣기/잘라내기/되돌리기: Ctrl 조합 키 이벤트를
            // 그대로 전송(원격/터미널에서도 동작). 실행 후 선택 모드는 해제.
            ActionType.SELECT_ALL -> sendCtrlShortcut(ic, KeyEvent.KEYCODE_A)
            ActionType.COPY -> sendCtrlShortcut(ic, KeyEvent.KEYCODE_C)
            ActionType.PASTE -> sendCtrlShortcut(ic, KeyEvent.KEYCODE_V)
            ActionType.CUT -> sendCtrlShortcut(ic, KeyEvent.KEYCODE_X)
            ActionType.UNDO -> sendCtrlShortcut(ic, KeyEvent.KEYCODE_Z)

            ActionType.SPACE -> {
                commitComposing()
                ic.commitText(" ", 1)
                selectActive = false
            }

            ActionType.ENTER -> {
                commitComposing()
                selectActive = false
                if (shifted) {
                    // 데스크탑처럼 Shift+Enter 는 (전송하지 않고) 줄바꿈만.
                    ic.commitText("\n", 1)
                    shiftState = ShiftState.OFF
                } else {
                    sendEnter(ic)
                }
            }

            ActionType.COMMA -> commitText(",")
            ActionType.PERIOD -> commitText(".")

            ActionType.LEFT -> moveCursor(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            ActionType.RIGHT -> moveCursor(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            ActionType.UP -> moveCursor(ic, KeyEvent.KEYCODE_DPAD_UP)
            ActionType.DOWN -> moveCursor(ic, KeyEvent.KEYCODE_DPAD_DOWN)

            ActionType.LANGUAGE -> {
                commitComposing()
                mode = if (mode == KeyboardMode.ENGLISH) KeyboardMode.KOREAN
                else KeyboardMode.ENGLISH
                shiftState = ShiftState.OFF
            }

            ActionType.SYMBOLS -> {
                commitComposing()
                if (mode == KeyboardMode.SYMBOLS) {
                    mode = previousMode
                } else {
                    previousMode = mode
                    mode = KeyboardMode.SYMBOLS
                }
                shiftState = ShiftState.OFF
            }

            ActionType.PIN -> {
                pinned = !pinned
                prefs.edit().putBoolean(KEY_PINNED, pinned).apply()
                applyPinDisposition()
            }
        }
    }

    private fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        selectActive = false
        commitComposing()
        ic.commitText(text, 1)
    }

    /** 클립보드 리스트에서 항목을 눌러 붙여넣기. 패널은 켜진 채 유지(연속 붙여넣기). */
    private fun onPasteClip(text: String) {
        val ic = currentInputConnection ?: return
        selectActive = false
        commitComposing()
        ic.commitText(text, 1)
    }

    /** 조합 중인 한글이 있으면 확정한다. */
    private fun commitComposing() {
        val ic = currentInputConnection ?: return
        if (!composer.isEmpty) {
            val finished = composer.flush()
            ic.commitText(finished, 1)
        }
    }

    /** 조합 중인 글자를 확정하고 방향키(DPAD)로 커서를 옮긴다. Ctrl 조합이면 단어 이동 등. */
    private fun moveCursor(ic: android.view.inputmethod.InputConnection, keyCode: Int) {
        commitComposing()
        sendKeyWithMeta(ic, keyCode, activeMeta() or selectionMeta())
        clearMods()
    }

    /** Del/Home/End/PgUp/PgDn 등 하드웨어 키를 (Ctrl/Alt 조합과 함께) 전송. */
    private fun onKeyCodeKey(code: Int) {
        val ic = currentInputConnection ?: return
        commitComposing()
        // 이동 키에만 선택(Shift)을 싣는다. 그 외(tab/del/esc 등)는 선택 모드를
        // 끄고 평범하게 처리 — del 은 선택영역 삭제, esc 는 선택 취소가 된다.
        val movement = code in MOVEMENT_CODES
        if (!movement) selectActive = false
        sendKeyWithMeta(ic, code, activeMeta() or (if (movement) selectionMeta() else 0))
        clearMods()
        if (shiftState == ShiftState.SINGLE) shiftState = ShiftState.OFF
    }

    /** 현재 켜진 Ctrl/Alt(+시프트) 조합의 meta 비트. */
    private fun activeMeta(): Int {
        var m = 0
        if (ctrlActive) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (altActive) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (shiftState != ShiftState.OFF) {
            m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        return m
    }

    /** 선택 모드의 Shift 비트 — 커서 이동 키에만 싣는다(Shift+방향키 = 선택). */
    private fun selectionMeta(): Int =
        if (selectActive) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0

    /** Ctrl+[keyCode] 단축키 전송(전체선택/복사/붙여넣기 등). 선택 모드는 해제. */
    private fun sendCtrlShortcut(ic: android.view.inputmethod.InputConnection, keyCode: Int) {
        commitComposing()
        selectActive = false
        sendKeyWithMeta(ic, keyCode, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
        clearMods()
    }

    private fun clearMods() {
        ctrlActive = false
        altActive = false
    }

    /** keyCode 를 (선택적 meta 와 함께) 실제 하드웨어 키 이벤트로 전송. 원격에서도 동작. */
    private fun sendKeyWithMeta(
        ic: android.view.inputmethod.InputConnection,
        keyCode: Int,
        meta: Int,
    ) {
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    /** 문자를 Ctrl/Alt 조합에 쓸 하드웨어 keyCode 로 매핑(영문/숫자만). */
    private fun latinKeyCode(text: String): Int? {
        if (text.length != 1) return null
        return when (val c = text[0]) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (c - 'a')
            in 'A'..'Z' -> KeyEvent.KEYCODE_A + (c - 'A')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (c - '0')
            else -> null
        }
    }

    /** 두벌식 자모를 같은 위치의 QWERTY 키코드로 매핑(한글 자판에서 Ctrl 조합용). */
    private fun jamoKeyCode(text: String): Int? {
        if (text.isEmpty()) return null
        val q = JAMO_QWERTY[text[0]] ?: return null
        return KeyEvent.KEYCODE_A + (q - 'a')
    }

    /**
     * 길게 누름 처리. 한/A 를 길게 누르면 Windows 원격 등 호스트의 한/영을
     * 토글하도록 오른쪽 Alt(한/영) 키 이벤트를 보낸다.
     */
    private fun onKeyLong(key: Key) {
        if (key is Key.Action && key.type == ActionType.LANGUAGE) {
            val ic = currentInputConnection ?: return
            commitComposing()
            sendKeyWithMeta(ic, KeyEvent.KEYCODE_ALT_RIGHT, 0)
        }
    }

    private fun sendEnter(ic: android.view.inputmethod.InputConnection) {
        val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        // 검색·전송·완료 같은 '진짜' 액션일 때만 그 액션을 수행하고,
        // 그 외(미지정/없음/줄바꿈 필드)에는 실제 Enter 키를 보낸다.
        if (!noEnterAction &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            sendKeyWithMeta(ic, KeyEvent.KEYCODE_ENTER, 0)
        }
    }

    private fun isJamo(c: Char): Boolean = c in 'ㄱ'..'ㅣ'

    private companion object {
        const val KEY_PINNED = "pinned"
        // 프로파일 접두어 + 항목 키. MainActivity 설정 화면과 키를 공유한다.
        const val PROFILE_FOLDED = "folded_"
        const val PROFILE_UNFOLDED = "unfolded_"
        const val KEY_SPLIT_GAP = "split_gap"
        const val KEY_KEY_HEIGHT = "key_height"
        const val KEY_AUX_ROWS = "aux_rows"
        const val KEY_CLIP_HISTORY = "clip_history"
        const val KEY_CLIP_PINNED = "clip_pinned"

        // 선택(Shift)을 실을 수 있는 커서 이동 키. 그 외 키는 선택 모드를 해제한다.
        val MOVEMENT_CODES = setOf(
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN
        )

        // 두벌식 자모 → 같은 물리 위치의 QWERTY 소문자.
        val JAMO_QWERTY = mapOf(
            'ㅂ' to 'q', 'ㅃ' to 'q', 'ㅈ' to 'w', 'ㅉ' to 'w', 'ㄷ' to 'e', 'ㄸ' to 'e',
            'ㄱ' to 'r', 'ㄲ' to 'r', 'ㅅ' to 't', 'ㅆ' to 't', 'ㅛ' to 'y', 'ㅕ' to 'u',
            'ㅑ' to 'i', 'ㅐ' to 'o', 'ㅒ' to 'o', 'ㅔ' to 'p', 'ㅖ' to 'p',
            'ㅁ' to 'a', 'ㄴ' to 's', 'ㅇ' to 'd', 'ㄹ' to 'f', 'ㅎ' to 'g',
            'ㅗ' to 'h', 'ㅓ' to 'j', 'ㅏ' to 'k', 'ㅣ' to 'l',
            'ㅋ' to 'z', 'ㅌ' to 'x', 'ㅊ' to 'c', 'ㅍ' to 'v', 'ㅠ' to 'b',
            'ㅜ' to 'n', 'ㅡ' to 'm'
        )
    }
}
