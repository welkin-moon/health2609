package uk.lunarlab.health2609.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
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
    val state by syncRepository.syncState.collectAsStateWithLifecycle(initialValue = SyncState())
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跨设备同步说明") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("跨设备同步尚未完成，本版本暂不提供登录或加密备份。")
                Text("上一版本的“同步完成”只表示连接到了服务，没有备份餐食、运动或身体信息。",
                    style = MaterialTheme.typography.bodyMedium)
                Text("身体信息与外观保存在这台手机。餐食与运动使用共享试用服务，请勿记录敏感内容。")
                if (state.username != null) {
                    Text("这台手机留有旧账号信息，可清除本地登录状态。云端已有数据会保留。")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        dismissButton = {
            if (state.username != null) {
                TextButton(onClick = { scope.launch { syncRepository.logout(); onDismiss() } }) {
                    Text("清除旧登录状态")
                }
            }
        }
    )
}
