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
    var showPassword by rememberSaveable { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessMessage by remember { mutableStateOf(true) }
    var showPrivacyDetail by rememberSaveable { mutableStateOf(false) }

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
                                    InfoRow(label = "登录学号", value = syncState.username ?: "已绑定")
                                    InfoRow(label = "同步设备", value = "${syncState.deviceCount} 台在线设备")
                                    InfoRow(label = "上次同步", value = syncState.lastSyncTimestamp ?: "刚刚")
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
                                    label = { Text("学生账号 / 学号") },
                                    placeholder = { Text("demo-student") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Devices, contentDescription = null)
                                    }
                                )

                                OutlinedTextField(
                                    value = passkeyInput,
                                    onValueChange = { passkeyInput = it },
                                    label = { Text("端对端加密通行密钥 / 密码") },
                                    placeholder = { Text("用于在本地生成解密主密钥") },
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

                                Button(
                                    onClick = {
                                        isProcessing = true
                                        statusMessage = "正在执行端对端密钥派生与登录..."
                                        isSuccessMessage = true
                                        coroutineScope.launch {
                                            syncRepository.registerOrLogin(
                                                schoolId = schoolIdInput.trim(),
                                                username = usernameInput.trim(),
                                                passkey = passkeyInput.trim()
                                            ).onSuccess { msg ->
                                                isProcessing = false
                                                statusMessage = msg
                                                isSuccessMessage = true
                                                passkeyInput = ""
                                            }.onFailure { err ->
                                                isProcessing = false
                                                statusMessage = err.message ?: "登录配置失败"
                                                isSuccessMessage = false
                                            }
                                        }
                                    },
                                    enabled = !isProcessing && usernameInput.isNotBlank() && passkeyInput.isNotBlank(),
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
                                        Text("登录并开启端对端加密同步")
                                    }
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
