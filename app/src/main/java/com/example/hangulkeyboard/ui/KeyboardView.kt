package com.example.hangulkeyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
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
 * 분할([splitGap] > 0)이면 각 줄 가운데에 공백을 끼우되(키 폭·배치는 원래
 * 그대로), 그 빈 칸 안에 줄별로 내용을 채운다 — 클립보드 모드면 클립 항목,
 * 아니면 커서/기능 미니 키. 별도 패널이나 오버레이를 띄우지 않는다.
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
    clipInStrip: Boolean,
    onKey: (Key) -> Unit,
    onKeyLong: (Key) -> Unit,
    onPaste: (String) -> Unit,
    onPinToggle: (String) -> Unit,
    onToggleSelect: () -> Unit,
    expectVowel: () -> Boolean = { false },
) {
    val shifted = shiftState != ShiftState.OFF
    // 분할이면 액션줄 방향키를 빼고 특수문자를 둔다(가운데 미니 방향키가 대신함).
    val bottomArrows = splitGap <= 0f
    val rows = remember(mode, shifted, auxRows, bottomArrows) {
        resolveRows(mode, shifted, auxRows, bottomArrows)
    }
    // 한/영 자판에서만 상단 보조줄(터미널/특수문자/숫자)을 낮게 둔다.
    val compactCount = if (mode == KeyboardMode.KOREAN || mode == KeyboardMode.ENGLISH)
        (rows.size - 4).coerceAtLeast(0) else 0
    // 고정 항목이 앞, 이후 최근 히스토리. 분할 슬롯/스트립이 함께 쓴다.
    val clipItems = pinnedClips.map { it to true } +
        clips.filter { it !in pinnedClips }.map { it to false }
    // 접은 화면(또는 분할 안 함)에서는 클립보드를 위에 한 줄 추가되는 스트립으로,
    // 펼친 분할 화면에서는 가운데 빈 칸에 넣는다.
    val stripMode = showClipboard && (splitGap <= 0f || clipInStrip)
    val slotClipMode = showClipboard && splitGap > 0f && !clipInStrip

    // 클립 항목이 줄 수보다 많으면 맨 아래 칸을 ▲▼ 페이지 키로 쓴다.
    val pagerNeeded = slotClipMode && clipItems.size > rows.size
    val clipPerPage = if (pagerNeeded) rows.size - 1 else rows.size
    var clipPage by remember(slotClipMode, clipItems.size) { mutableIntStateOf(0) }
    val maxClipPage = if (clipItems.isEmpty()) 0 else (clipItems.size - 1) / clipPerPage

    Surface(color = Color(0xFFECEFF1)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1.dp, vertical = 4.dp)
        ) {
            if (stripMode) ClipboardStrip(clipItems, onPaste, onPinToggle)
            rows.forEachIndexed { index, keys ->
                val compact = index < compactCount
                // 분할: 줄 가운데(weight 절반 지점)에 공백을 끼운다. 키 폭은 그대로.
                val rowKeys = if (splitGap > 0f) splitWithGap(keys, splitGap) else keys
                KeyRow(
                    keys = rowKeys,
                    compact = compact,
                    firstRow = index == 0,
                    keyHeight = keyHeight,
                    shiftState = shiftState,
                    ctrlActive = ctrlActive,
                    altActive = altActive,
                    showClipboard = showClipboard,
                    expectVowel = expectVowel,
                    onKey = onKey,
                    onKeyLong = onKeyLong,
                    gapContent = if (splitGap > 0f) {
                        {
                            CenterSlot(
                                rowFromBottom = rows.size - 1 - index,
                                rowFromTop = index,
                                showClipboard = slotClipMode,
                                selectActive = selectActive,
                                clipItems = clipItems,
                                clipPage = clipPage,
                                clipPerPage = clipPerPage,
                                pagerNeeded = pagerNeeded,
                                onPage = { delta ->
                                    clipPage = (clipPage + delta).coerceIn(0, maxClipPage)
                                },
                                onKey = onKey,
                                onPaste = onPaste,
                                onPinToggle = onPinToggle,
                                onToggleSelect = onToggleSelect
                            )
                        }
                    } else null
                )
            }
        }
    }
}

/**
 * 줄을 weight 누적 합이 절반에 가장 가까운 지점에서 나눠 [gap] 공백을 끼운다.
 * 동률이면 뒤쪽 지점을 택해 왼손 글쇠(ㅎ·ㅍ, g 등)가 왼쪽 블록에 남게 한다.
 */
private fun splitWithGap(keys: List<Key>, gap: Float): List<Key> {
    val total = keys.sumOf { keyWeight(it).toDouble() }
    var best = 1
    var bestDiff = Double.MAX_VALUE
    var acc = 0.0
    for (i in 0 until keys.size - 1) {
        acc += keyWeight(keys[i])
        val diff = abs(acc - total / 2)
        if (diff <= bestDiff) {
            bestDiff = diff
            best = i + 1
        }
    }
    return keys.subList(0, best) + Key.Gap(gap) + keys.subList(best, keys.size)
}

@Composable
private fun KeyRow(
    keys: List<Key>,
    compact: Boolean,
    firstRow: Boolean,
    keyHeight: Float,
    shiftState: ShiftState,
    ctrlActive: Boolean,
    altActive: Boolean,
    showClipboard: Boolean,
    expectVowel: () -> Boolean,
    onKey: (Key) -> Unit,
    onKeyLong: (Key) -> Unit,
    gapContent: (@Composable () -> Unit)? = null,
) {
    val cellHeight = if (compact) (keyHeight * 0.77f).dp else keyHeight.dp
    Row(modifier = Modifier.fillMaxWidth()) {
        keys.forEachIndexed { i, key ->
            if (key is Key.Gap) {
                if (gapContent != null) {
                    // 빈 칸도 그 줄의 키와 같은 높이의 셀 — 내용이 줄에 맞춰 박힌다.
                    // 좌우 8dp 는 글자 키와의 오터치 방지용 데드존.
                    Box(
                        modifier = Modifier
                            .weight(key.weight)
                            .height(cellHeight)
                            .padding(horizontal = 8.dp)
                    ) {
                        gapContent()
                    }
                } else {
                    Spacer(Modifier.weight(key.weight))
                }
            } else {
                KeyButton(
                    key = key,
                    shiftState = shiftState,
                    ctrlActive = ctrlActive,
                    altActive = altActive,
                    showClipboard = showClipboard,
                    compact = compact,
                    firstRow = firstRow,
                    keyHeight = keyHeight,
                    // 경계 스냅용 좌우 이웃 글자(한 글자 Char 키만, Gap 건너편은 제외).
                    neighborLeft = (keys.getOrNull(i - 1) as? Key.Char)?.output?.singleOrNull(),
                    neighborRight = (keys.getOrNull(i + 1) as? Key.Char)?.output?.singleOrNull(),
                    expectVowel = expectVowel,
                    modifier = Modifier.weight(keyWeight(key)),
                    onKey = onKey,
                    onKeyLong = onKeyLong
                )
            }
        }
    }
}

/**
 * 분할 시 각 줄 가운데 빈 칸의 내용.
 * 클립보드 모드: 위에서부터 줄당 클립 항목 하나(탭 = 붙여넣기, 길게 = 고정).
 * 항목이 넘치면 맨 아래 칸이 ▲▼ 페이지 키가 된다.
 * 커서 모드: 아래줄부터 선택 / ◀▶ / ▲▼ / esc·전체선택 / 복사·붙여넣기 /
 * 잘라내기·되돌리기. (home/end/pgup/pgdn 은 터미널줄에 이미 있어 뺐다.)
 */
@Composable
private fun CenterSlot(
    rowFromBottom: Int,
    rowFromTop: Int,
    showClipboard: Boolean,
    selectActive: Boolean,
    clipItems: List<Pair<String, Boolean>>,
    clipPage: Int,
    clipPerPage: Int,
    pagerNeeded: Boolean,
    onPage: (Int) -> Unit,
    onKey: (Key) -> Unit,
    onPaste: (String) -> Unit,
    onPinToggle: (String) -> Unit,
    onToggleSelect: () -> Unit,
) {
    if (showClipboard) {
        if (pagerNeeded && rowFromBottom == 0) {
            Row(modifier = Modifier.fillMaxSize()) {
                MiniKey("▲") { onPage(-1) }
                MiniKey("▼") { onPage(+1) }
            }
            return
        }
        val item = clipItems.getOrNull(clipPage * clipPerPage + rowFromTop)
        when {
            item != null -> ClipItem(
                text = item.first,
                pinned = item.second,
                maxLines = 2,
                fontSize = 11,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 2.5.dp),
                onPaste = onPaste,
                onPinToggle = onPinToggle
            )
            clipItems.isEmpty() && rowFromTop == 0 -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text("클립보드 비어 있음", fontSize = 10.sp, color = Color(0xFF607D8B))
            }
        }
        return
    }
    Row(modifier = Modifier.fillMaxSize()) {
        when (rowFromBottom) {
            // 선택 토글은 칸 전체 대신 가운데 2/3 폭만 차지(오터치 완화).
            0 -> {
                Spacer(Modifier.weight(0.25f))
                MiniKey("선택", active = selectActive) { onToggleSelect() }
                Spacer(Modifier.weight(0.25f))
            }
            1 -> {
                MiniKey("◀") { onKey(Key.Action(ActionType.LEFT, "◀")) }
                MiniKey("▶") { onKey(Key.Action(ActionType.RIGHT, "▶")) }
            }
            2 -> {
                MiniKey("▲") { onKey(Key.Action(ActionType.UP, "▲")) }
                MiniKey("▼") { onKey(Key.Action(ActionType.DOWN, "▼")) }
            }
            3 -> {
                MiniKey("esc") { onKey(Key.KeyCode("esc", KeyEvent.KEYCODE_ESCAPE)) }
                // 전체선택: Ctrl+A 를 그대로 전송.
                MiniKey("전체") { onKey(Key.Action(ActionType.SELECT_ALL, "전체")) }
            }
            4 -> {
                MiniKey("복사") { onKey(Key.Action(ActionType.COPY, "복사")) }
                MiniKey("붙여") { onKey(Key.Action(ActionType.PASTE, "붙여")) }
            }
            5 -> {
                MiniKey("잘라") { onKey(Key.Action(ActionType.CUT, "잘라")) }
                MiniKey("되돌") { onKey(Key.Action(ActionType.UNDO, "되돌")) }
            }
        }
    }
}

/** 빈 칸 안에 들어가는 미니 키. 일반 키와 같은 인셋/모양으로 줄에 맞춘다. */
@Composable
private fun RowScope.MiniKey(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = if (active) Color(0xFF4CAF50) else Color(0xFFCFD8DC),
            shape = RoundedCornerShape(7.dp),
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.5.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    label,
                    fontSize = 12.sp,
                    color = Color(0xFF1A1A1A),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** 키보드 위에 한 줄로 추가되는 클립보드 스트립. 고정 항목이 앞에 온다. */
@Composable
private fun ClipboardStrip(
    clipItems: List<Pair<String, Boolean>>,
    onPaste: (String) -> Unit,
    onPinToggle: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (clipItems.isEmpty()) {
            Text(
                "클립보드 비어 있음",
                fontSize = 12.sp,
                color = Color(0xFF607D8B),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            return@Row
        }
        clipItems.forEach { (text, pinned) ->
            ClipItem(
                text = text,
                pinned = pinned,
                maxLines = 1,
                fontSize = 12,
                modifier = Modifier.widthIn(max = 160.dp).fillMaxHeight(),
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
    fontSize: Int,
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
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
            Text(
                text = if (pinned) "📌 $text" else text,
                fontSize = fontSize.sp,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF1A1A1A),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
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
    firstRow: Boolean,
    keyHeight: Float,
    neighborLeft: Char?,
    neighborRight: Char?,
    expectVowel: () -> Boolean,
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
    val isChar = key is Key.Char
    // 키 프리뷰(눌린 글쇠 풍선) 상태. 글자 키만.
    var pressed by remember { mutableStateOf(false) }
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
        // 글자 키: 터치 x 좌표를 받아 경계 스냅(모음이 올 자리에서 자음 키의
        // 가장자리를 눌렀으면 이웃 모음으로 보정)을 적용한다.
        isChar -> Modifier.pointerInput(key, neighborLeft, neighborRight) {
            detectTapGestures(onTap = { pos ->
                press(resolveEdgeSnap(key as Key.Char, pos.x, size.width.toFloat(),
                    neighborLeft, neighborRight, expectVowel))
            })
        }
        else -> Modifier.clickable { press(key) }
    }
    // 프리뷰용 눌림 추적. 입력 제스처와 별개로 down/up 만 관찰한다.
    val previewModifier = if (isChar) Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            pressed = true
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.all { !it.pressed }) break
                }
            } finally {
                pressed = false
            }
        }
    } else Modifier

    // 바깥 Box = 셀 전체(터치 영역, 데드존 없음). 안쪽 Surface = 보이는 키(여백만큼 인셋).
    Box(
        modifier = modifier
            .height(cellHeight)
            .zIndex(if (pressed) 10f else 0f)
            .then(pressModifier)
            .then(previewModifier),
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
        // 키 프리뷰: 눌린 글쇠를 위(맨 윗줄은 제자리)에 크게 띄운다. 손가락에
        // 가린 키를 즉시 확인해 오타를 빨리 인지하게 한다.
        if (pressed && isChar) {
            Surface(
                color = Color(0xFF455A64),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = if (firstRow) (-6).dp else -cellHeight * 0.95f)
                    .zIndex(11f)
            ) {
                Text(
                    text = label,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * 경계 스냅: 초성만 있는 상태(다음은 모음일 확률이 높음)에서 자음 키의
 * 가장자리(폭의 22% 이내)를 눌렀고 그쪽 이웃이 모음이면 그 모음으로 보정한다.
 * 확신이 없는 상황(가운데 터치, 모음 키, 조합 문맥 아님)은 건드리지 않는다.
 */
private fun resolveEdgeSnap(
    key: Key.Char,
    x: Float,
    width: Float,
    neighborLeft: Char?,
    neighborRight: Char?,
    expectVowel: () -> Boolean,
): Key {
    val self = key.output.singleOrNull() ?: return key
    if (!isJamoConsonant(self) || !expectVowel()) return key
    val edge = width * 0.22f
    if (x < edge && neighborLeft != null && isJamoVowel(neighborLeft)) {
        return Key.Char(neighborLeft.toString())
    }
    if (x > width - edge && neighborRight != null && isJamoVowel(neighborRight)) {
        return Key.Char(neighborRight.toString())
    }
    return key
}

private fun isJamoConsonant(c: Char): Boolean = c in 'ㄱ'..'ㅎ'
private fun isJamoVowel(c: Char): Boolean = c in 'ㅏ'..'ㅣ'

private fun resolveRows(
    mode: KeyboardMode,
    shifted: Boolean,
    aux: AuxRows,
    bottomArrows: Boolean,
): List<List<Key>> {
    val base = when (mode) {
        KeyboardMode.KOREAN -> KeyboardLayouts.korean(aux, bottomArrows)
        KeyboardMode.ENGLISH -> KeyboardLayouts.english(aux, bottomArrows)
        KeyboardMode.SYMBOLS -> KeyboardLayouts.symbols(bottomArrows)
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
    is Key.Action -> when {
        // 레이아웃이 명시한 폭이 있으면 우선(스페이스 중앙쪽 확장 등).
        key.weight > 0f -> key.weight
        // 스페이스가 좌우 두 개라 각각 살짝만 넓게.
        key.type == ActionType.SPACE -> 1.5f
        key.type == ActionType.ENTER -> 1.6f
        key.type == ActionType.SHIFT || key.type == ActionType.BACKSPACE -> 1.5f
        // ?123·한/A 는 일반 키와 같은 폭(else = 1f)
        else -> 1f
    }
    is Key.Char -> key.weight
    else -> 1f
}
