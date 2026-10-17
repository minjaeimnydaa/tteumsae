package com.tteumsae.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tteumsae.app.domain.account.AgeGroup
import com.tteumsae.app.domain.account.Gender
import com.tteumsae.app.domain.account.UserProfile
import com.tteumsae.app.ui.theme.TteumBackground
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditScreen(
    profile: UserProfile,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSave: (String?, AgeGroup?, Gender?) -> Unit,
) {
    var displayName by rememberSaveable(profile.userId, profile.displayName) {
        mutableStateOf(profile.displayName.orEmpty())
    }
    var saveRequested by rememberSaveable(profile.userId) { mutableStateOf(false) }
    val normalizedDisplayName = displayName.trim()
    val savedDisplayName = profile.displayName.orEmpty().trim()
    val hasChanges = normalizedDisplayName != savedDisplayName
    val canSave = hasChanges && !isLoading
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val submitProfile = {
        if (canSave) {
            saveRequested = true
            focusManager.clearFocus()
            keyboardController?.hide()
            onSave(normalizedDisplayName.takeIf(String::isNotBlank), profile.ageGroup, profile.gender)
        }
    }

    LaunchedEffect(saveRequested, isLoading, errorMessage, profile.displayName) {
        if (
            saveRequested &&
            !isLoading &&
            errorMessage == null &&
            normalizedDisplayName == savedDisplayName
        ) {
            onBack()
        }
    }

    Scaffold(
        containerColor = TteumBackground,
        topBar = {
            TopAppBar(
                title = { Text("프로필 관리", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TteumBackground),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = TteumSpacing.screenHorizontal)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(vertical = TteumSpacing.sectionGap),
                verticalArrangement = Arrangement.spacedBy(TteumSpacing.md),
            ) {
                Text("기본 정보", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 40) displayName = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = TteumSizing.inputMinHeight),
                    shape = TteumShapes.control,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    enabled = !isLoading,
                    label = { Text("닉네임", style = MaterialTheme.typography.labelMedium) },
                    supportingText = {
                        Row(Modifier.fillMaxWidth()) {
                            Text("선택 입력", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            Text("${displayName.length}/40", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSave) submitProfile()
                            else {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        },
                    ),
                )
                Text(
                    "앱과 계정 화면에 표시할 닉네임을 관리해요.",
                    color = Color(0xFF6B7079),
                    style = MaterialTheme.typography.bodySmall,
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = TteumRed,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(TteumSpacing.xxs))
                Button(
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                    onClick = submitProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TteumSizing.buttonHeight)
                        .semantics {
                            if (isLoading) {
                                liveRegion = LiveRegionMode.Polite
                                stateDescription = "프로필 저장 중"
                            }
                        },
                    enabled = canSave,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF777C85),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(TteumSpacing.inlineGap))
                    }
                    Text(if (isLoading) "저장 중…" else "저장", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
