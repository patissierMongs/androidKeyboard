package com.example.hangulkeyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 키보드 활성화 안내 + 설정(분할 공백) + 테스트 입력칸.
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

    var splitGap by remember { mutableFloatStateOf(prefs.getFloat("split_gap", 0f)) }
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

        // ── 분할 키보드 공백 조절 ──
        Text(
            "분할 간격 (가운데 공백): ${"%.1f".format(splitGap)}",
            fontSize = 16.sp
        )
        Slider(
            value = splitGap,
            onValueChange = { splitGap = it },
            onValueChangeFinished = {
                prefs.edit().putFloat("split_gap", splitGap).apply()
            },
            valueRange = 0f..4f,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "0 이면 분할 안 함. 값을 바꾼 뒤 아래 칸을 다시 누르면 적용됩니다.",
            fontSize = 13.sp
        )

        // ── 테스트 입력칸 ──
        OutlinedTextField(
            value = testText,
            onValueChange = { testText = it },
            label = { Text("테스트 입력") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
