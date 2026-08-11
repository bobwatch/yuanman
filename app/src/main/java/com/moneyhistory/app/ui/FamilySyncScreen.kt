package com.moneyhistory.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.R
import com.moneyhistory.app.ui.theme.IncomeGreen

/** 家庭同步：配对码管理 + 设备列表 + 手动同步。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySyncScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val sync = viewModel.syncManager
    val devices by sync.devices.collectAsStateWithLifecycle()
    val status by sync.status.collectAsStateWithLifecycle()
    val myCode by sync.pairingCode.collectAsStateWithLifecycle()

    var inputCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val regeneratedText = stringResource(R.string.family_regenerated)
    val joinedText = stringResource(R.string.family_joined)
    val joinInvalidText = stringResource(R.string.family_join_invalid)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.family_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard(title = stringResource(R.string.family_title)) {
                Text(
                    text = stringResource(R.string.family_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 本机配对码
            SettingsCard(title = stringResource(R.string.family_code_title)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = myCode,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 8.sp
                    )
                    TextButton(onClick = {
                        sync.regeneratePairingCode()
                        message = regeneratedText
                    }) {
                        Text(stringResource(R.string.family_regenerate))
                    }
                }
            }

            // 加入家庭
            SettingsCard(title = stringResource(R.string.family_join_title)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { input ->
                            inputCode = input.filter { it.isDigit() }.take(6)
                        },
                        label = { Text(stringResource(R.string.family_join_hint)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        message = if (sync.setPairingCode(inputCode)) {
                            joinedText
                        } else {
                            joinInvalidText
                        }
                    }) {
                        Text(stringResource(R.string.common_confirm))
                    }
                }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 同步状态与设备列表
            SettingsCard(title = stringResource(R.string.family_status_title)) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { sync.syncNow() }) {
                    Text(stringResource(R.string.family_sync_now))
                }
                Spacer(Modifier.height(12.dp))
                if (devices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.family_no_devices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    devices.forEach { device ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.family_device,
                                    device.name.removePrefix("MH-")
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(
                                    if (device.connected) {
                                        R.string.family_connected
                                    } else {
                                        R.string.family_discovered
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (device.connected) {
                                    IncomeGreen
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
