package com.example.hangulkeyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 키보드 전체 뷰. 상태(mode/shift)는 호출자가 소유하고,
 * 키 입력은 [onKey] 콜백으로 전달한다.
 *
 * 분할([splitGap] > 0)이면 좌/우 반쪽 키 블록 사이의 가운데 공간이
 * 레이아웃의 실제 구성원이 되고, 그 안에 클립보드/커서 패드를 배치한다.
 */
@Composable
fun KeyboardView(
    mode: KeyboardMode,
    shiftState: ShiftState,
    ctrlActive: Boolean,
    altActive: Boolean,
    splitGap: Float,
    keyHeight: Float,
    auxRows: AuxRows,
    clips: List<String>,
    pinnedClips: List<String>,
    showClipboard: Boolean,
    selectActive: Boolean,
    onKey: (Key) -> Unit,
    onKeyLong: (Key) -> Unit,
    onPaste: (String) -> Unit,
    onPinToggle: (String) -> Unit,
    onToggleSelect: () -> Unit,
) {
    val shifted = shiftState != ShiftState.OFF
    val rows = remember(mode, shifted, auxRows) { resolveRows(mode, shifted, auxRows) }
    // 한/영 자판에서만 상단 보조줄(터미널/특수문자/숫자)을 낮게 둔다.
    val compactCount = if (mode == KeyboardMode.KOREAN || mode == KeyboardMode.ENGLISH)
        (rows.size - 4).coerceAtLeast(0) else 0

    Surface(color = Color(0xFFECEFF1)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1.dp, vertical = 4.dp)
        ) {
            if (splitGap > 0f) {
                SplitLayout(
                    rows, compactCount, splitGap, keyHeight,
                    shiftState, ctrlActive, altActive, showClipboard, selectActive,
                    clips, pinnedClips, onKey, onKeyLong, onPaste, onPinToggle, onToggleSelect
                )
            } else {
                if (showClipboard) ClipboardStrip(clips, pinnedClips, onPaste, onPinToggle)
                rows.forEachIndexed { index, keys ->
                    KeyRow(
                        keys, index < compactCount, keyHeight,
                        shiftState, ctrlActive, altActive, showClipboard, onKey, onKeyLong
                    )
                }
            }
        }
    }
}

/**
 * 분할 레이아웃: [좌측 키 블록 | 중앙 패널 | 우측 키 블록] 3컬럼.
 * 각 줄은 weight 누적 합의 절반 지점에서 나뉘므로 키의 상대 폭은 그대로 유지되고,
 * 중앙 패널은 오버레이가 아니라 실제 배치라 키를 가리거나 터치를 뺏지 않는다.
 */
@Composable
private fun SplitLayout(
    rows: List<List<Key>>,
    compactCount: Int,
    splitGap: Float,
    keyHeight: Float,
    shiftState: ShiftState,
    ctrlActive: Boolean,
    altActive: Boolean,
    showClipboard: Boolean,
    selectActive: Boolean,
    clips: List<String>,
    pinnedClips: List<String>,
    onKey: (Key) -> Unit,
    onKeyLong: (Key) -> Unit,
    onPaste: (String) -> Unit,
    onPinToggle: (String) -> Unit,
    onToggleSelect: () -> Unit,
) {
    val halves = remember(rows) { rows.map { splitByWeight(it) } }
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(modifier = Modifier.weight(HALF_ROW_WEIGHT)) {
            halves.forEachIndexed { index, half ->
                KeyRow(
                    half.first, index < compactCount, keyHeight,
                    shiftState, ctrlActive, altActive, showClipboard, onKey, onKeyLong
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(splitGap.coerceAtLeast(0.5f))
                .fillMaxHeight()
                .padding(horizontal = 3.dp, vertical = 2.dp)
        ) {
            if (showClipboard) ClipboardPanel(clips, pinnedClips, onPaste, onPinToggle)
            else CursorPad(selectActive, onKey, onToggleSelect)
        }
        Column(modifier = Modifier.weight(HALF_ROW_WEIGHT)) {
            halves.forEachIndexed { index, half ->
                KeyRow(
                    half.second, index < compactCount, keyHeight,
                    shiftState, ctrlActive, altActive, showClipboard, onKey, onKeyLong
                )
            }
        }
    }
}

// 분할 시 좌/우 키 블록 각각의 weight. 대략 한 줄 weight(~11)의 절반이라
// splitGap 의 의미(키 폭 단위 공백)가 기존과 같게 유지된다.
private const val HALF_ROW_WEIGHT = 5.5f

/** 줄을 weight 누적 합이 절반에 가장 가까운 지점에서 좌/우로 나눈다. */
private fun splitByWeight(keys: List<Key>): Pair<List<Key>, List<Key>> {
    val total = keys.sumOf { keyWeight(it).toDouble() }
    var best = 1
    var bestDiff = Double.MAX_VALUE
    var acc = 0.0
    for (i in 0 until keys.size - 1) {
        acc += keyWeight(keys[i])
        val diff = abs(acc - total / 2)
        if (diff < bestDiff) {
            bestDiff = diff
            best = i + 1
        }
    }
    return keys.subList(0, best) to keys.subList(best, keys.size)
}

@Composable
private fun KeyRow(
    keys: List<Key>,
    compact: Boolean,
    keyHeight: Float,
    shiftState: ShiftState,
    ctrlActive: Boolean,
    altActive: Boolean,
    showClipboard: Boolean,
    onKey: (Key) -> Unit,
    onKeyLong: (Key) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        keys.forEach { key ->
            if (key is Key.Gap) {
                Spacer(Modifier.weight(key.weight))
            } else {
                KeyButton(
                    key = key,
                    shiftState = shiftState,
                    ctrlActive = ctrlActive,
                    altActive = altActive,
                    showClipboard = showClipboard,
                    compact = compact,
                    keyHeight = keyHeight,
                    modifier = Modifier.weight(keyWeight(key)),
                    onKey = onKey,
                    onKeyLong = onKeyLong
                )
            }
        }
    }
}

/**
 * 분할 중앙 커서 패드. 원격 데스크탑/터미널에서 커서 이동·선택을
 * 화면 가운데(양손 엄지 사이)에서 처리한다. 📋 키로 클립보드와 전환.
 */
@Composable
private fun CursorPad(
    selectActive: Boolean,
    onKey: (Key) -> Unit,
    onToggleSelect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        PadRow {
            PadKey("esc") { onKey(Key.KeyCode("esc", KeyEvent.KEYCODE_ESCAPE)) }
            PadKey("▲") { onKey(Key.Action(ActionType.UP, "▲")) }
            // 선택 모드: 켜져 있는 동안 이동 키에 Shift 가 실려 텍스트가 선택된다.
            PadKey("선택", active = selectActive) { onToggleSelect() }
        }
        PadRow {
            PadKey("◀") { onKey(Key.Action(ActionType.LEFT, "◀")) }
            PadKey("▼") { onKey(Key.Action(ActionType.DOWN, "▼")) }
            PadKey("▶") { onKey(Key.Action(ActionType.RIGHT, "▶")) }
        }
        PadRow {
            PadKey("home") { onKey(Key.KeyCode("home", KeyEvent.KEYCODE_MOVE_HOME)) }
            PadKey("end") { onKey(Key.KeyCode("end", KeyEvent.KEYCODE_MOVE_END)) }
        }
        PadRow {
            PadKey("pgup") { onKey(Key.KeyCode("pgup", KeyEvent.KEYCODE_PAGE_UP)) }
            PadKey("pgdn") { onKey(Key.KeyCode("pgdn", KeyEvent.KEYCODE_PAGE_DOWN)) }
        }
    }
}

@Composable
private fun ColumnScope.PadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        content = content
    )
}

@Composable
private fun RowScope.PadKey(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Surface(
        color = if (active) Color(0xFF4CAF50) else Color.White,
        shape = RoundedCornerShape(6.dp),
        shadowElevation = 1.dp,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, fontSize = 13.sp, color = Color(0xFF1A1A1A), textAlign = TextAlign.Center)
        }
    }
}

/** 분할 중앙: 클립보드 히스토리 세로 리스트. 탭 = 붙여넣기, 길게 = 고정/해제. */
@Composable
private fun ClipboardPanel(
    clips: List<String>,
    pinnedClips: List<String>,
    onPaste: (String) -> Unit,
    onPinToggle: (String) -> Unit,
) {
    // clips/pinnedClips 는 SnapshotStateList 라 remember 키로 쓰면 내용 변경이
    // 반영되지 않는다. 매 컴포지션마다 직접 계산해 스냅샷 읽기를 추적시킨다.
    val items = pinnedClips.map { it to true } +
        clips.filter { it !in pinnedClips }.map { it to false }
    if (items.isEmpty()) {
        Text(
            "클립보드\n비어 있음",
            fontSize = 11.sp,
            color = Color(0xFF607D8B),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items.forEach { (text, pinned) ->
            ClipItem(
                text, pinned,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                onPaste = onPaste,
                onPinToggle = onPinToggle
            )
        }
    }
}

/** 분할이 아닐 때 상단 가로 클립보드 스트립. 고정 항목이 앞에 온다. */
@Composable
private fun ClipboardStrip(
    clips: List<String>,
    pinnedClips: List<String>,
    onPaste: (String) -> Unit,
    onPinToggle: (String) -> Unit,
) {
    val items = pinnedClips.map { it to true } +
        clips.filter { it !in pinnedClips }.map { it to false }
    if (items.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (text, pinned) ->
            ClipItem(
                text, pinned,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 160.dp),
                onPaste = onPaste,
                onPinToggle = onPinToggle
            )
        }
    }
}

@Composable
private fun ClipItem(
    text: String,
    pinned: Boolean,
    maxLines: Int,
    modifier: Modifier,
    onPaste: (String) -> Unit,
    onPinToggle: (String) -> Unit,
) {
    val view = LocalView.current
    Surface(
        color = if (pinned) Color(0xFFFFF8E1) else Color.White,
        shape = RoundedCornerShape(6.dp),
        shadowElevation = 1.dp,
        modifier = modifier.pointerInput(text) {
            detectTapGestures(
                onTap = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onPaste(text)
                },
                onLongPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onPinToggle(text)
                }
            )
        }
    ) {
        Text(
            text = if (pinned) "📌 $text" else text,
            fontSize = 12.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFF1A1A1A),
            modifier = Modifier.padding(6.dp)
        )
    }
}

@Composable
private fun KeyButton(
    key: Key,
    shiftState: ShiftState,
    ctrlActive: Boolean,
    altActive: Boolean,
    showClipboard: Boolean,
    compact: Boolean,
    keyHeight: Float,
    modifier: Modifier,
    onKey: (Key) -> Unit,
    onKeyLong: (Key) -> Unit,
) {
    val isAction = key is Key.Action
    val bg = when {
        // Ctrl/Alt/클립보드 가 켜져 있으면 강조색
        key is Key.Action && key.type == ActionType.CTRL && ctrlActive -> Color(0xFF4CAF50)
        key is Key.Action && key.type == ActionType.ALT && altActive -> Color(0xFF4CAF50)
        key is Key.Action && key.type == ActionType.CLIPBOARD && showClipboard -> Color(0xFF4CAF50)
        // 시프트: 지속(고정)은 진한 초록, 단일입력은 연한 초록
        key is Key.Action && key.type == ActionType.SHIFT && shiftState == ShiftState.LOCKED -> Color(0xFF4CAF50)
        key is Key.Action && key.type == ActionType.SHIFT && shiftState == ShiftState.SINGLE -> Color(0xFFA5D6A7)
        key is Key.Action && key.type == ActionType.SPACE -> Color.White
        isAction -> Color(0xFFB0BEC5)
        // 기능키(Del/Home/End 등)는 기능키 색으로 구분
        key is Key.KeyCode -> Color(0xFFCFD8DC)
        // 보조 줄(터미널/특수문자/숫자)은 살짝 어둡게 해 글자 줄과 구분
        compact -> Color(0xFFF7F8FA)
        else -> Color.White
    }
    val label = labelFor(key, shiftState)
    // 글자 줄은 크고 누르기 쉽게, 보조 줄은 낮게. 높이는 프로파일 설정을 따른다.
    val cellHeight = if (compact) (keyHeight * 0.77f).dp else keyHeight.dp
    val fontSize = when {
        isAction -> 15.sp
        key is Key.KeyCode -> 13.sp
        compact -> 16.sp
        else -> 20.sp
    }
    // 키 누를 때 햅틱(시스템 키보드 진동 설정을 따른다).
    val view = LocalView.current
    fun press(k: Key) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onKey(k)
    }
    // 백스페이스·방향키는 꾹 누르면 반복.
    val repeatable = key is Key.Action && key.type in setOf(
        ActionType.BACKSPACE, ActionType.LEFT, ActionType.RIGHT, ActionType.UP, ActionType.DOWN
    )
    // 한/A 는 길게 누르면 원격 호스트 한/영 전환 키를 보낸다.
    val hasLongPress = key is Key.Action && key.type == ActionType.LANGUAGE
    val isSpace = key is Key.Action && key.type == ActionType.SPACE
    val scope = rememberCoroutineScope()
    val pressModifier = when {
        // 백스페이스: 꾹 누르면 연속 삭제(다른 키보드와 동일).
        repeatable -> Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val job = scope.launch {
                        onKey(key)          // 첫 입력
                        delay(400)          // 길게 누름 인식 지연
                        while (isActive) {  // 이후 빠르게 반복
                            onKey(key)
                            delay(45)
                        }
                    }
                    // 손을 떼거나 제스처가 취소돼도 반복 작업이 반드시 멈추도록 finally 로 정리.
                    try {
                        tryAwaitRelease()
                    } finally {
                        job.cancel()
                    }
                }
            )
        }
        // 스페이스: 탭 = 공백, 누른 채 좌우로 끌면 즉시 커서 이동(길게 누름 지연 없음).
        isSpace -> Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val step = 22.dp.toPx()
                val slop = viewConfiguration.touchSlop
                var dragging = false
                var acc = 0f
                var lastX = down.position.x
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (!dragging) onKey(key)   // 움직이지 않았으면 일반 스페이스
                        break
                    }
                    acc += change.position.x - lastX
                    lastX = change.position.x
                    if (!dragging && abs(acc) > slop) {
                        dragging = true
                        acc = 0f
                    }
                    if (dragging) {
                        change.consume()
                        while (acc >= step) {
                            acc -= step
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onKey(Key.Action(ActionType.RIGHT, "▶"))
                        }
                        while (acc <= -step) {
                            acc += step
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onKey(Key.Action(ActionType.LEFT, "◀"))
                        }
                    }
                }
            }
        }
        hasLongPress -> Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { press(key) },
                onLongPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onKeyLong(key)
                }
            )
        }
        else -> Modifier.clickable { press(key) }
    }

    // 바깥 Box = 셀 전체(터치 영역, 데드존 없음). 안쪽 Surface = 보이는 키(여백만큼 인셋).
    Box(
        modifier = modifier
            .height(cellHeight)
            .then(pressModifier),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(7.dp),
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.5.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = label,
                    fontSize = fontSize,
                    fontWeight = if (isAction) FontWeight.Medium else FontWeight.Normal,
                    color = Color(0xFF1A1A1A),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun resolveRows(mode: KeyboardMode, shifted: Boolean, aux: AuxRows): List<List<Key>> {
    val base = when (mode) {
        KeyboardMode.KOREAN -> KeyboardLayouts.korean(aux)
        KeyboardMode.ENGLISH -> KeyboardLayouts.english(aux)
        KeyboardMode.SYMBOLS -> KeyboardLayouts.SYMBOLS
    }
    if (!shifted) return base
    return when (mode) {
        KeyboardMode.KOREAN -> KeyboardLayouts.shiftKorean(base)
        KeyboardMode.ENGLISH -> KeyboardLayouts.shiftEnglish(base)
        KeyboardMode.SYMBOLS -> base
    }
}

private fun labelFor(key: Key, shiftState: ShiftState): String = when (key) {
    is Key.Char -> key.label
    is Key.KeyCode -> key.label
    is Key.Action -> when (key.type) {
        ActionType.SPACE -> "space"
        ActionType.SHIFT -> when (shiftState) {
            ShiftState.OFF -> "⇧"
            ShiftState.SINGLE -> "⬆"
            ShiftState.LOCKED -> "⇪"
        }
        else -> key.label
    }
    is Key.Gap -> ""
}

private fun keyWeight(key: Key): Float = when (key) {
    is Key.Action -> when (key.type) {
        // 스페이스가 좌우 두 개라 각각 살짝만 넓게.
        ActionType.SPACE -> 1.5f
        ActionType.ENTER -> 1.6f
        ActionType.SHIFT, ActionType.BACKSPACE -> 1.5f
        // ?123·한/A 는 일반 키와 같은 폭(else = 1f)
        else -> 1f
    }
    is Key.Char -> key.weight
    else -> 1f
}
