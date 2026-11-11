package com.aiselp.autox.ui.material3

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun MainFloatingActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    FloatingActionButton(
        modifier =
        Modifier
            .padding(16.dp)
            .then(modifier), onClick = onClick
    ) {
        content()
    }
}