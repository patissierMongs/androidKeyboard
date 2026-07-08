package com.example.hangulkeyboard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hangulkeyboard.ui.AuxRows
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 키보드 활성화 안내 + 접힘/펼침 프로파일별 설정 + 테스트 입력칸.
 * IME 자체는 [ImeService] 에 있다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen(
                        onEnable = {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        onChoose = {
                            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                .showInputMethodPicker()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(onEnable: () -> Unit, onChoose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ime_prefs", Context.MODE_PRIVATE) }
    var testText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("한글 키보드", fontSize = 26.sp)
        Text(
            "1) ‘키보드 켜기’로 시스템 설정에서 활성화\n" +
                "2) ‘키보드 선택’으로 입력기 전환\n" +
                "3) 아래 테스트 칸에서 입력해 보기",
            fontSize = 15.sp
        )
        Button(onClick = onEnable) { Text("키보드 켜기 (시스템 설정)") }
        Button(onClick = onChoose) { Text("키보드 선택") }

        HorizontalDivider()

        // 프로파일은 ImeService 가 smallestScreenWidthDp(600 기준)로 자동 선택한다.
        ProfileSection(
            title = "펼쳤을 때 (메인 화면)",
            prefix = "unfolded_",
            prefs = prefs,
            defaultSplit = 2f,
            defaultHeight = 52f,
            defaultAux = AuxRows.ALL
        )

        HorizontalDivider()

        ProfileSection(
            title = "접었을 때 (커버 화면)",
            prefix = "folded_",
            prefs = prefs,
            defaultSplit = 0f,
            defaultHeight = 56f,
            defaultAux = AuxRows.TERMINAL
        )

        Text(
            "분할 간격이 0 이면 분할하지 않습니다. 분할하면 가운데 공간에\n" +
                "커서 패드가 표시되고, 📋 키로 클립보드 → 스니펫 순으로 전환합니다.\n" +
                "설정 변경은 키보드에 즉시 반영됩니다.",
            fontSize = 13.sp
        )

        HorizontalDivider()

        SnippetSection(prefs)

        HorizontalDivider()

        TypoStatsSection(prefs)

        // ── 테스트 입력칸 ──
        OutlinedTextField(
            value = testText,
            onValueChange = { testText = it },
            label = { Text("테스트 입력") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 스니펫 편집. 키보드 가운데 칸 세 번째 모드(📋 두 번)에 뜬다. */
@Composable
private fun SnippetSection(prefs: SharedPreferences) {
    val snippets = remember {
        mutableStateListOf<String>().apply {
            runCatching {
                val arr = JSONArray(prefs.getString("snippets", null) ?: "[]")
                repeat(arr.length()) { add(arr.getString(it)) }
            }
        }
    }
    fun save() {
        prefs.edit().putString("snippets", JSONArray(snippets.toList()).toString()).apply()
    }
    var newSnippet by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("스니펫", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "자주 쓰는 명령어/문자열 (가운데 오른쪽 열). 키보드에서 탭 = 입력,\n" +
                "클립 항목을 길게 누르면 여기로 이동(고정), 스니펫을 길게 누르면 해제.",
            fontSize = 13.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newSnippet,
                onValueChange = { newSnippet = it },
                label = { Text("새 스니펫") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val t = newSnippet.trim()
                    if (t.isNotEmpty() && t !in snippets) {
                        snippets.add(t)
                        save()
                        newSnippet = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("추가") }
        }
        snippets.forEachIndexed { index, snippet ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snippet,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // 배치(순서) 조정 — 위가 키보드에서 먼저 보인다.
                TextButton(onClick = {
                    if (index > 0) {
                        val item = snippets.removeAt(index)
                        snippets.add(index - 1, item)
                        save()
                    }
                }) { Text("↑") }
                TextButton(onClick = {
                    if (index < snippets.lastIndex) {
                        val item = snippets.removeAt(index)
                        snippets.add(index + 1, item)
                        save()
                    }
                }) { Text("↓") }
                TextButton(onClick = {
                    snippets.removeAt(index)
                    save()
                }) { Text("삭제") }
            }
        }
    }
}

/**
 * 오타 분석. IME 가 기록한 터치 편향/혼동 쌍 통계를 읽어 보여준다.
 * (모든 데이터는 이 기기의 SharedPreferences 에만 저장된다.)
 */
@Composable
private fun TypoStatsSection(prefs: SharedPreferences) {
    var refresh by remember { mutableStateOf(0) }
    val tracker = remember(refresh) {
        TypoTracker().apply { loadJson(prefs.getString("typo_stats", null)) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("오타 분석", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { refresh++ }) { Text("새로고침") }
            TextButton(onClick = {
                prefs.edit().remove("typo_stats").apply()
                refresh++
            }) { Text("초기화") }
        }

        if (tracker.taps == 0L) {
            Text(
                "아직 데이터가 없습니다. 키보드를 쓰다 보면 글자 키 터치 지점과\n" +
                    "\"입력→백스페이스→다른 키\" 수정 패턴이 여기에 쌓입니다.",
                fontSize = 13.sp
            )
            return@Column
        }

        val rate = if (tracker.taps > 0) tracker.corrections * 100.0 / tracker.taps else 0.0
        Text(
            "총 입력 ${tracker.taps}타 · 수정 ${tracker.corrections}회 · " +
                "오타율 ${"%.1f".format(rate)}%",
            fontSize = 14.sp
        )

        val topConfusions = tracker.confusion.entries.sortedByDescending { it.value }.take(8)
        if (topConfusions.isNotEmpty()) {
            Text("자주 헷갈리는 키 (지운 키 → 다시 누른 키)", fontSize = 14.sp,
                fontWeight = FontWeight.Bold)
            topConfusions.forEach { (pair, count) ->
                val (from, to) = pair.split(">").let { it[0] to it.getOrElse(1) { "?" } }
                Text("· $from → $to  ${count}회", fontSize = 14.sp)
            }
            Text(
                "10회 이상 쌓인 인접 경계는 키보드가 스냅 폭을 자동으로 넓힙니다.",
                fontSize = 12.sp
            )
        }

        // 표본 30개 이상, 중심에서 8% 이상 치우친 키만 보여준다.
        val biased = tracker.offsets.entries
            .filter { it.value[2] >= 30f }
            .map { (label, v) ->
                Triple(label, v[0] / v[2], v[1] / v[2])
            }
            .filter { abs(it.second) >= 0.08f || abs(it.third) >= 0.08f }
            .sortedByDescending { abs(it.second) + abs(it.third) }
            .take(6)
        if (biased.isNotEmpty()) {
            Text("터치 편향 (키 중심 대비, 표본 30타 이상)", fontSize = 14.sp,
                fontWeight = FontWeight.Bold)
            biased.forEach { (label, dx, dy) ->
                val h = if (dx >= 0) "오른쪽" else "왼쪽"
                val v = if (dy >= 0) "아래" else "위"
                Text(
                    "· $label: $h ${abs(dx * 100).roundToInt()}% · $v ${abs(dy * 100).roundToInt()}%",
                    fontSize = 14.sp
                )
            }
            Text(
                "아래쪽 편향이 크면 터치 Y 오프셋 보정(다음 단계)이 효과적입니다.",
                fontSize = 12.sp
            )
        }
    }
}

/** 접힘/펼침 한쪽 프로파일의 설정 묶음. 키는 [prefix] 를 붙여 저장한다. */
@Composable
private fun ProfileSection(
    title: String,
    prefix: String,
    prefs: SharedPreferences,
    defaultSplit: Float,
    defaultHeight: Float,
    defaultAux: AuxRows,
) {
    var split by remember { mutableFloatStateOf(prefs.getFloat(prefix + "split_gap", defaultSplit)) }
    var height by remember { mutableFloatStateOf(prefs.getFloat(prefix + "key_height", defaultHeight)) }
    var aux by remember {
        mutableStateOf(
            runCatching { AuxRows.valueOf(prefs.getString(prefix + "aux_rows", null) ?: "") }
                .getOrDefault(defaultAux)
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        Text("분할 간격 (가운데 공백): ${"%.1f".format(split)}", fontSize = 14.sp)
        Slider(
            value = split,
            onValueChange = { split = it },
            onValueChangeFinished = {
                prefs.edit().putFloat(prefix + "split_gap", split).apply()
            },
            valueRange = 0f..4f,
            modifier = Modifier.fillMaxWidth()
        )

        Text("키 높이: ${"%.0f".format(height)} dp", fontSize = 14.sp)
        Slider(
            value = height,
            onValueChange = { height = it },
            onValueChangeFinished = {
                prefs.edit().putFloat(prefix + "key_height", height).apply()
            },
            valueRange = 44f..64f,
            modifier = Modifier.fillMaxWidth()
        )

        Text("상단 보조줄", fontSize = 14.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val options = listOf(
                AuxRows.ALL to "전체",
                AuxRows.TERMINAL_NUMBER to "터미널+숫자",
                AuxRows.TERMINAL to "터미널만",
                AuxRows.NONE to "없음"
            )
            options.forEach { (value, label) ->
                val selected = aux == value
                Button(
                    onClick = {
                        aux = value
                        prefs.edit().putString(prefix + "aux_rows", value.name).apply()
                    },
                    colors = if (selected) ButtonDefaults.buttonColors()
                    else ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color(0xFF444444)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
    }
}
