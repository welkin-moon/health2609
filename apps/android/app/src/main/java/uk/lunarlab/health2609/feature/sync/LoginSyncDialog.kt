package uk.lunarlab.health2609.feature.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.lunarlab.health2609.core.sync.SyncDevice
import uk.lunarlab.health2609.core.sync.SyncRepository
import uk.lunarlab.health2609.core.sync.SyncState

@Composable
fun LoginSyncDialog(
    syncRepository: SyncRepository,
    currentSchoolId: String = "demo-school",
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val syncState by syncRepository.syncState.collectAsStateWithLifecycle(initialValue = SyncState())

    var schoolIdInput by rememberSaveable(syncState.schoolId, currentSchoolId) {
        mutableStateOf(syncState.schoolId ?: currentSchoolId)
    }
    var usernameInput by rememberSaveable(syncState.username) {
        mutableStateOf(syncState.username.orEmpty())
    }
    var passkeyInput by rememberSaveable { mutableStateOf("") }
    var recoveryPhraseInput by rememberSaveable { mutableStateOf("") }
    var generatedRecoveryPhrase by rememberSaveable { mutableStateOf<String?>(null) }
    var rotationPasswordInput by rememberSaveable { mutableStateOf("") }
    var rotationRecoveryInput by rememberSaveable { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<SyncDevice>>(emptyList()) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessMessage by remember { mutableStateOf(true) }
    var showPrivacyDetail by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(syncState.isEnabled, syncState.deviceCount) {
        if (syncState.isEnabled) {
            syncRepository.devices().onSuccess { devices = it }
        } else {
            devices = emptyList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                if (syncState.isEnabled) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "学校账号与加密云同步",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "端对端加密 (E2EE) · 隐私安全隔离",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status feedback banner
                    if (statusMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSuccessMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (isSuccessMessage) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isSuccessMessage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    statusMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSuccessMessage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (generatedRecoveryPhrase != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "恢复短语（只显示这一次）",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    generatedRecoveryPhrase.orEmpty(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "请先离线保存再关闭此窗口。丢失所有已授权设备和恢复短语后，历史端到端加密数据无法恢复。",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = { generatedRecoveryPhrase = null }) {
                                    Text("我已保存恢复短语")
                                }
                            }
                        }
                    }

                    if (syncState.isEnabled) {
                        // Logged-in state
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            "端对端加密云同步已就绪",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "AES-256-GCM 硬件级密匙保护",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InfoRow(label = "就读学校", value = syncState.schoolId ?: currentSchoolId)
                                    InfoRow(label = "同步账号", value = syncState.username ?: "已绑定")
                                    InfoRow(label = "同步设备", value = "${syncState.deviceCount} 台在线设备")
                                    InfoRow(label = "上次同步", value = syncState.lastSyncTimestamp ?: "刚刚")
                                    InfoRow(label = "密钥 epoch", value = syncState.currentKeyEpoch.toString())
                                }

                                if (syncState.keyRefreshRequired) {
                                    Text(
                                        "另一台设备已经轮换账号密钥。请退出后用密码重新登录，以获取新的密钥 epoch。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                if (devices.isNotEmpty()) {
                                    Text(
                                        "已授权设备",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    devices.forEach { device ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    device.name + if (device.current) "（本机）" else "",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (device.current) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                                Text(
                                                    if (device.revokedAt != null) "已吊销" else "最近在线：" + device.lastSeenAt,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (!device.current && device.revokedAt == null) {
                                                TextButton(
                                                    onClick = {
                                                        isProcessing = true
                                                        coroutineScope.launch {
                                                            syncRepository.revokeDevice(device.id)
                                                                .onSuccess { message ->
                                                                    statusMessage = message
                                                                    isSuccessMessage = true
                                                                    syncRepository.devices().onSuccess { devices = it }
                                                                }
                                                                .onFailure { error ->
                                                                    statusMessage = error.message ?: "吊销设备失败"
                                                                    isSuccessMessage = false
                                                                }
                                                            isProcessing = false
                                                        }
                                                    },
                                                    enabled = !isProcessing
                                                ) {
                                                    Text("吊销")
                                                }
                                            }
                                        }
                                    }
                                }

                                if (syncState.rotationRequired) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "设备已被吊销，需要轮换账号加密密钥",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                "吊销会阻止该设备继续访问服务器；完成密钥轮换后，它也无法解密之后产生的新记录。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            OutlinedTextField(
                                                value = rotationPasswordInput,
                                                onValueChange = { rotationPasswordInput = it },
                                                label = { Text("当前密码") },
                                                visualTransformation = PasswordVisualTransformation(),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = rotationRecoveryInput,
                                                onValueChange = { rotationRecoveryInput = it },
                                                label = { Text("恢复短语") },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Button(
                                                onClick = {
                                                    isProcessing = true
                                                    coroutineScope.launch {
                                                        syncRepository.rotateAccountKey(
                                                            passkey = rotationPasswordInput,
                                                            recoveryPhrase = rotationRecoveryInput
                                                        ).onSuccess { message ->
                                                            statusMessage = message
                                                            isSuccessMessage = true
                                                            rotationPasswordInput = ""
                                                            rotationRecoveryInput = ""
                                                        }.onFailure { error ->
                                                            statusMessage = error.message ?: "密钥轮换失败"
                                                            isSuccessMessage = false
                                                        }
                                                        isProcessing = false
                                                    }
                                                },
                                                enabled = !isProcessing &&
                                                    rotationPasswordInput.length >= 8 &&
                                                    rotationRecoveryInput.isNotBlank()
                                            ) {
                                                Text("立即轮换未来记录密钥")
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            isProcessing = true
                                            statusMessage = "正在加密同步..."
                                            isSuccessMessage = true
                                            coroutineScope.launch {
                                                syncRepository.triggerSync()
                                                    .onSuccess { msg ->
                                                        isProcessing = false
                                                        statusMessage = msg
                                                        isSuccessMessage = true
                                                    }
                                                    .onFailure { err ->
                                                        isProcessing = false
                                                        statusMessage = err.message ?: "同步失败"
                                                        isSuccessMessage = false
                                                    }
                                            }
                                        },
                                        enabled = !isProcessing,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isProcessing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("同步中…")
                                        } else {
                                            Icon(
                                                Icons.Rounded.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("立即同步")
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                syncRepository.logout()
                                                statusMessage = "已退出同步账号"
                                                isSuccessMessage = true
                                                passkeyInput = ""
                                            }
                                        },
                                        enabled = !isProcessing
                                    ) {
                                        Text("退出账号")
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            syncRepository.logout(removeLocalKeyMaterial = true)
                                            statusMessage = "已退出，并清除本机账号密钥；本机私密记录仍保留为设备加密状态。"
                                            isSuccessMessage = true
                                            devices = emptyList()
                                        }
                                    },
                                    enabled = !isProcessing,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("退出并清除本机账号密钥")
                                }
                                Text(
                                    "警告：如果没有其他已授权设备或恢复短语，清除本机密钥后将无法恢复云端历史密文。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Login / Register Form
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "学生身份登录与密钥配置",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                OutlinedTextField(
                                    value = schoolIdInput,
                                    onValueChange = { schoolIdInput = it },
                                    label = { Text("学校标识 (School ID)") },
                                    placeholder = { Text("demo-school") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Rounded.School, contentDescription = null)
                                    }
                                )

                                OutlinedTextField(
                                    value = usernameInput,
                                    onValueChange = { usernameInput = it },
                                    label = { Text("同步账号（与学校学号分离）") },
                                    placeholder = { Text("例如 shishi-sync") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Devices, contentDescription = null)
                                    }
                                )

                                OutlinedTextField(
                                    value = passkeyInput,
                                    onValueChange = { passkeyInput = it },
                                    label = { Text("密码") },
                                    placeholder = { Text("至少 8 个字符；恢复账号时作为新密码") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Lock, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                                            )
                                        }
                                    }
                                )

                                OutlinedTextField(
                                    value = recoveryPhraseInput,
                                    onValueChange = { recoveryPhraseInput = it },
                                    label = { Text("恢复短语（新设备/找回密码时填写）") },
                                    placeholder = { Text("首次注册留空；已有账号的新设备请填写") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    minLines = 2,
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Security, contentDescription = null)
                                    }
                                )

                                Button(
                                    onClick = {
                                        isProcessing = true
                                        statusMessage = "正在派生密钥并验证账号…"
                                        isSuccessMessage = true
                                        coroutineScope.launch {
                                            syncRepository.registerOrLogin(
                                                schoolId = schoolIdInput.trim(),
                                                username = usernameInput.trim(),
                                                passkey = passkeyInput,
                                                recoveryPhrase = recoveryPhraseInput
                                            ).onSuccess { result ->
                                                isProcessing = false
                                                statusMessage = result.message
                                                generatedRecoveryPhrase = result.recoveryPhrase
                                                isSuccessMessage = true
                                                passkeyInput = ""
                                                recoveryPhraseInput = ""
                                            }.onFailure { err ->
                                                isProcessing = false
                                                statusMessage = err.message ?: "登录配置失败"
                                                isSuccessMessage = false
                                            }
                                        }
                                    },
                                    enabled = !isProcessing && usernameInput.isNotBlank() && passkeyInput.length >= 8,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isProcessing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("派生密钥并验证中…")
                                    } else {
                                        Text("注册 / 登录并开启同步")
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        isProcessing = true
                                        statusMessage = "正在用恢复短语重建账号密钥…"
                                        isSuccessMessage = true
                                        coroutineScope.launch {
                                            syncRepository.recoverAndResetPassword(
                                                schoolId = schoolIdInput.trim(),
                                                username = usernameInput.trim(),
                                                recoveryPhrase = recoveryPhraseInput,
                                                newPassword = passkeyInput
                                            ).onSuccess { result ->
                                                isProcessing = false
                                                statusMessage = result.message
                                                isSuccessMessage = true
                                                passkeyInput = ""
                                                recoveryPhraseInput = ""
                                            }.onFailure { err ->
                                                isProcessing = false
                                                statusMessage = err.message ?: "账号恢复失败"
                                                isSuccessMessage = false
                                            }
                                        }
                                    },
                                    enabled = !isProcessing &&
                                        usernameInput.isNotBlank() &&
                                        passkeyInput.length >= 8 &&
                                        recoveryPhraseInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("用恢复短语重设密码")
                                }
                            }
                        }
                    }

                    // Mandatory Privacy Guarantee Statement Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "学生健康隐私保护承诺",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            Text(
                                "个人健康明细经本地端对端加密存储，学校仅能查看班级/年级脱敏后的整体统计指标",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            TextButton(
                                onClick = { showPrivacyDetail = !showPrivacyDetail },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (showPrivacyDetail) "收起隐私聚合技术说明" else "了解学校指标如何实现隐私安全聚合",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showPrivacyDetail) {
                                Column(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "1. 本地端加密：每一张餐盘照片与进餐记录均在手机本地使用 PBKDF2 派生的 256 位 AES-GCM 密钥加密后再传输。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        "2. 零知识存储：云端数据库只保存不可逆的 Ciphertext 密文与 IV，云服务商和学校管理员无法解密个人日记。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        "3. 统计脱敏掩码：学校管理看板与大屏仅展示班级平均摄入达标率及体育运动时长，且受 k-匿名保护（少于 3 人班级自动屏蔽），杜绝逆向定位具体学生。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
