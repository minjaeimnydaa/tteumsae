package com.tteumsae.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tteumsae.app.ui.theme.TteumInk
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class NoticeDocument(val title: String, val file: String)
private data class NoticeModule(val coordinate: String, val licenses: String)
private data class NoticeCatalog(val documents: List<NoticeDocument>, val modules: List<NoticeModule>)

/** Local text only: viewing legal notices never starts a web request or runs HTML/JavaScript. */
@Composable
internal fun LicenseNoticesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedDocument by remember { mutableStateOf<NoticeDocument?>(null) }
    val catalog by produceState<NoticeCatalog?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val json = context.assets.open("licenses/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
                val docs = json.getJSONArray("documents")
                val modules = json.getJSONArray("modules")
                NoticeCatalog(
                    documents = List(docs.length()) { i ->
                        val doc = docs.getJSONObject(i)
                        NoticeDocument(doc.getString("title"), doc.getString("file"))
                    },
                    modules = List(modules.length()) { i ->
                        val module = modules.getJSONObject(i)
                        val names = module.getJSONArray("licenseNames")
                        NoticeModule(module.getString("coordinate"), List(names.length()) { names.getString(it) }.joinToString(" · "))
                    },
                )
            }.getOrElse { NoticeCatalog(emptyList(), emptyList()) }
        }
    }
    val documentText by produceState<String?>(initialValue = null, selectedDocument) {
        value = null
        val document = selectedDocument ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                require(document.file.matches(Regex("[a-zA-Z0-9_.-]+\\.txt")) && !document.file.contains(".."))
                context.assets.open("licenses/${document.file}").bufferedReader().use { it.readText() }
            }.getOrElse { "문서를 불러오지 못했어요. 앱을 다시 열어 주세요." }
        }
    }
    val goBack = { if (selectedDocument != null) selectedDocument = null else onDismiss() }
    Dialog(onDismissRequest = goBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = goBack)
        Surface(modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f), shape = TteumShapes.dialog) {
            Column(Modifier.padding(TteumInsets.dialog)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("이용 고지", modifier = Modifier.weight(1f).padding(top = TteumSpacing.itemGap), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = goBack, modifier = Modifier.heightIn(min = TteumSizing.touchTarget), contentPadding = TteumInsets.button, shape = TteumShapes.control) {
                        Text(if (selectedDocument == null) "닫기" else "목록", style = MaterialTheme.typography.labelMedium)
                    }
                }
                HorizontalDivider()
                if (selectedDocument != null) {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TteumSpacing.itemGap)) {
                        item { Text(selectedDocument!!.title, modifier = Modifier.padding(top = TteumSpacing.md), style = MaterialTheme.typography.titleLarge) }
                        if (documentText == null) item { Text("문서를 여는 중이에요.", style = MaterialTheme.typography.bodyLarge) }
                        else items(documentText.orEmpty().split(Regex("\\n\\s*\\n"))) { paragraph ->
                            SelectionContainer { Text(paragraph, color = TteumInk, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TteumSpacing.itemGap)) {
                        item { Text("앱에 함께 제공되는 라이브러리·지도 SDK·폰트의 고지입니다. 원문은 인터넷 없이 열 수 있어요.", color = TteumMuted, modifier = Modifier.padding(top = TteumSpacing.md), style = MaterialTheme.typography.bodyLarge) }
                        if (catalog == null) item { Text("목록을 여는 중이에요.", style = MaterialTheme.typography.bodyLarge) }
                        else if (catalog!!.documents.isEmpty()) item { Text("목록을 불러오지 못했어요. 앱을 다시 열어 주세요.", style = MaterialTheme.typography.bodyLarge) }
                        items(catalog?.documents.orEmpty(), key = { it.file }) { doc ->
                            SettingsRow(title = doc.title, description = "원문 보기", onClick = { selectedDocument = doc })
                            HorizontalDivider()
                        }
                        item { Text("포함된 라이브러리와 버전", modifier = Modifier.padding(top = TteumSpacing.itemGap), style = MaterialTheme.typography.titleSmall) }
                        items(catalog?.modules.orEmpty(), key = { it.coordinate }) { module ->
                            Text(module.coordinate, style = MaterialTheme.typography.labelSmall)
                            Text(module.licenses, color = TteumMuted, style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
