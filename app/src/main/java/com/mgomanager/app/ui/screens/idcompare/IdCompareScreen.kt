package com.mgomanager.app.ui.screens.idcompare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.ui.theme.StatusOrange
import com.mgomanager.app.ui.theme.StatusRed

@Composable
fun IdCompareScreen(
    navController: NavController,
    viewModel: IdCompareViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Blue header section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp)
        ) {
            Text(
                text = "ID-Vergleich",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SSAID Groups
                IdGroupCard(
                    title = "SSAID",
                    groups = uiState.ssaidGroups,
                    navController = navController
                )

                // GAID Groups
                IdGroupCard(
                    title = "GAID (Google Ad ID)",
                    groups = uiState.gaidGroups,
                    navController = navController
                )

                // Device Token Groups
                IdGroupCard(
                    title = "Device Token",
                    groups = uiState.deviceTokenGroups,
                    navController = navController
                )

                // App Set ID Groups
                IdGroupCard(
                    title = "App Set ID",
                    groups = uiState.appSetIdGroups,
                    navController = navController
                )

                // Back to list link
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "← Zurück zur Liste",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { navController.popBackStack() }
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun IdGroupCard(
    title: String,
    groups: List<IdGroup>,
    navController: NavController
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${groups.size} IDs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (groups.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Keine Daten vorhanden",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                groups.forEach { group ->
                    IdGroupItem(
                        group = group,
                        navController = navController
                    )
                    if (group != groups.last()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun IdGroupItem(
    group: IdGroup,
    navController: NavController
) {
    val isDuplicate = group.accounts.size > 1

    Column {
        // ID value with duplicate indicator
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = group.idValue,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            if (isDuplicate) {
                Surface(
                    color = StatusOrange,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${group.accounts.size}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Account list
        group.accounts.forEach { account ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate("detail/${account.id}")
                    }
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "→",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = account.fullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDuplicate) StatusRed else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
