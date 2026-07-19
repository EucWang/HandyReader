package com.wxn.reader.presentation.bookReader.components

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wxn.base.util.ToastUtil
import com.wxn.reader.R
import com.wxn.reader.util.BatteryOptimizationHelper

@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onNeverShowAgain: () -> Unit
) {
    val context = LocalContext.current
    val manufacturer = BatteryOptimizationHelper.getManufacturer()
    var neverShow by remember { mutableStateOf(false) }

    val stepsResId = when (manufacturer) {
        BatteryOptimizationHelper.Manufacturer.XIAOMI -> R.array.battery_optimization_steps_xiaomi
        BatteryOptimizationHelper.Manufacturer.HUAWEI -> R.array.battery_optimization_steps_huawei
        BatteryOptimizationHelper.Manufacturer.OPPO -> R.array.battery_optimization_steps_oppo
        BatteryOptimizationHelper.Manufacturer.VIVO -> R.array.battery_optimization_steps_vivo
        BatteryOptimizationHelper.Manufacturer.SAMSUNG -> R.array.battery_optimization_steps_samsung
        BatteryOptimizationHelper.Manufacturer.OTHER -> R.array.battery_optimization_steps_other
    }
    val steps = context.resources.getStringArray(stepsResId).toList()

    AlertDialog(
        onDismissRequest = onSkip,
        icon = {
            Icon(
                imageVector = Icons.Outlined.BatteryChargingFull,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = stringResource(id = R.string.battery_optimization_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(id = R.string.battery_optimization_message),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(
                        id = R.string.battery_optimization_manufacturer_steps,
                        manufacturer.displayName
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                steps.forEachIndexed { index, step ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = neverShow,
                        onCheckedChange = { neverShow = it }
                    )
                    Text(
                        text = stringResource(id = R.string.battery_optimization_never_show),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        try {
                            BatteryOptimizationHelper.getManufacturerSettingsIntent(context)
                                ?.let { context.startActivity(it) }
                        } catch (_: ActivityNotFoundException) {
                            BatteryOptimizationHelper.openBatteryOptimizationSettings(context)
                        } catch (_: SecurityException) {
                            ToastUtil.show(R.string.action_launch_failed)
                        }
                        onConfirm()
                        if (neverShow) onNeverShowAgain()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.battery_optimization_go_settings))
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        onSkip()
                        if (neverShow) onNeverShowAgain()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.battery_optimization_skip))
                }
            }
        }
    )
}
