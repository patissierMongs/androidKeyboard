package com.example.hangulkeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 키보드 전체 뷰. 상태(mode/shift)는 호출자가 소유하고,
 * 키 입력은 [onKey] 콜백으로 전달한다.
 */
@Composable
fun KeyboardView(
    mode: KeyboardMode,
    shiftState: ShiftState,
    ctrlActive: Boolean,
    altActive: Boolean,
    splitGap: Float,
    clips: List<String>,
    showClipboard: Boolean,
    onKey: (Key) -> Unit,
    onKeyLong: (Key) -> Unit,
    onPaste: (String) -> Unit,
) {
    val shifted = shiftState != ShiftState.OFF
    val rows = remember(mode, shifted) { resolveRows(mode, shifted) }
    // 한/영 자판에서만 상단 보조줄(터미널/특수문자/숫자)을 낮게 둔다.
    val compactCount = if (mode == KeyboardMode.KOREAN || mode == KeyboardMode.ENGLISH)
        (rows.size - 4).coerceAtLeast(0) else 0

    Surface(color = Color(0xFFECEFF1)) {
        // 키 레이아웃은 그대로 두고, 분할 시 클립보드는 가운데 빈 공간에 '겹쳐서' 띄운다.
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp, vertical = 4.dp)
            ) {
                // 분할이 아닐 때만 클립보드를 상단 가로 스트립으로.
                if (showClipboard && splitGap <= 0f) ClipboardStrip(clips, onPaste)
                rows.forEachIndexed { index, keys ->
                    val compact = index < compactCount
                    // 분할: 각 줄 가운데에 공백만 끼운다(키 폭은 원래 그대로).
                    val rowKeys = if (splitGap > 0f) splitRow(keys, splitGap) else keys
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowKeys.forEach { key ->
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
                                    modifier = Modifier.weight(keyWeight(key)),
                                    onKey = onKey,
                                    onKeyLong = onKeyLong
                                )
                            }
                        }
                    }
                }
            }
            // 분할 시 가운데 남는 공간에 클립보드 리스트를 겹쳐 띄운다(키 배치는 안 바뀜).
            if (showClipboard && splitGap > 0f) {
                // 줄마다 가운데 공백 위치가 조금씩 달라서, 모든 줄에서 공통으로 비어 있는
                // 구간에만 패널을 띄운다. 그래야 패널이 키를 가리지 않는다.
                val (start, end) = remember(rows, splitGap) { commonGap(rows, splitGap) }
                if (end > start) {
                    Row(modifier = Modifier.matchParentSize()) {
                        if (start > 0f) Spacer(Modifier.weight(start))
                        Box(
                            modifier = Modifier
                                .weight(end - start)
                                .fillMaxHeight()
                                .background(Color(0xFFECEFF1))
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ClipboardPanel(clips, onPaste)
                        }
                        if (end < 1f) Spacer(Modifier.weight(1f - end))
                    }
                }
            }
        }
    }
}

/** 분할 중앙: 클립보드 히스토리 세로 리스트. 항목을 누르면 붙여넣기. */
@Composable
private fun ClipboardPanel(clips: List<String>, onPaste: (String) -> Unit) {
    if (clips.isEmpty()) {
        Text(
            "클립보드\n비어 있음",
            fontSize = 11.sp,
            color = Color(0xFF607D8B),
            textAlign = TextAlign.Center
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        clips.forEach { clip ->
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().clickable { onPaste(clip) }
            ) {
                Text(
                    text = clip,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF1A1A1A),
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}

/** 분할이 아닐 때 상단 가로 클립보드 스트립. */
@Composable
private fun ClipboardStrip(clips: List<String>, onPaste: (String) -> Unit) {
    if (clips.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        clips.forEach { clip ->
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.clickable { onPaste(clip) }
            ) {
                Text(
                    text = clip,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF1A1A1A),
                    modifier = Modifier
                        .widthIn(max = 160.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// 모든 줄에서 공통으로 비어 있는 가운데 구간을 가로 비율(0~1)로 구한다.
private fun commonGap(rows: List<List<Key>>, gap: Float): Pair<Float, Float> {
    var start = 0f
    var end = 1f
    rows.forEach { keys ->
        val mid = (keys.size + 1) / 2
        val left = keys.take(mid).sumOf { keyWeight(it).toDouble() }.toFloat()
        val total = keys.sumOf { keyWeight(it).toDouble() }.toFloat() + gap
        start = maxOf(start, left / total)
        end = minOf(end, (left + gap) / total)
    }
    return start to end
}

// 분할: 각 줄 가운데에 [gap] 폭의 공백을 끼운다(키 폭은 그대로 유지).
private fun splitRow(keys: List<Key>, gap: Float): List<Key> {
    val mid = (keys.size + 1) / 2
    return keys.subList(0, mid) + Key.Gap(gap) + keys.subList(mid, keys.size)
}

@Composable
private fun KeyButton(
    key: Key,
    shiftState: ShiftState,
    ctrlActive: Boolean,
    altActive: Boolean,
    showClipboard: Boolean,
    compact: Boolean,
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
    // 글자 줄은 크고 누르기 쉽게, 보조 줄은 낮게
    val keyHeight = if (compact) 40.dp else 52.dp
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
            .height(keyHeight)
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

private fun resolveRows(mode: KeyboardMode, shifted: Boolean): List<List<Key>> {
    val base = when (mode) {
        KeyboardMode.KOREAN -> KeyboardLayouts.KOREAN
        KeyboardMode.ENGLISH -> KeyboardLayouts.ENGLISH
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
