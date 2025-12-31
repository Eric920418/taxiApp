package com.hualien.taxidriver.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 評分對話框
 */
@Composable
fun RatingDialog(
    title: String = "評價行程",
    targetName: String,  // 被評價者名稱
    isDriver: Boolean,   // true = 評價司機, false = 評價乘客
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, comment: String?) -> Unit
) {
    var selectedRating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isDriver) "您對司機 $targetName 的評價" else "您對乘客 $targetName 的評價",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 星星評分
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 1..5) {
                        Icon(
                            imageVector = if (i <= selectedRating) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = "$i 星",
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { selectedRating = i },
                            tint = if (i <= selectedRating) Color(0xFFFFB300) else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (selectedRating) {
                        1 -> "非常不滿意"
                        2 -> "不滿意"
                        3 -> "普通"
                        4 -> "滿意"
                        5 -> "非常滿意"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = when (selectedRating) {
                        1, 2 -> MaterialTheme.colorScheme.error
                        3 -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> Color(0xFF4CAF50)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 評論輸入
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("評語（選填）") },
                    placeholder = { Text("分享您的體驗...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSubmitting
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSubmitting = true
                    onSubmit(selectedRating, comment.ifBlank { null })
                },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("提交評價")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("稍後再說")
            }
        }
    )
}

/**
 * 簡化版評分組件（內嵌使用）
 */
@Composable
fun StarRating(
    rating: Float,
    maxRating: Int = 5,
    starSize: Int = 20
) {
    Row {
        for (i in 1..maxRating) {
            Icon(
                imageVector = if (i <= rating) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = "$i 星",
                modifier = Modifier.size(starSize.dp),
                tint = if (i <= rating) Color(0xFFFFB300) else Color.Gray.copy(alpha = 0.5f)
            )
        }
    }
}
