package com.timelog.app.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timelog.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLogApp(vm: TimeLogViewModel = viewModel(factory = TimeLogViewModel.Factory)) {
    RefreshLocalTime(vm)
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { message -> vm.consumeMessage(); snackbar.showSnackbar(message) }
    }
    var insightsSelected by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val needsSetup = vm.ready && vm.settings?.onboardingComplete == false
    val title = when {
        needsSetup -> R.string.app_name
        settingsOpen -> R.string.settings
        insightsSelected -> R.string.insights
        else -> R.string.today
    }
    BackHandler(settingsOpen) { settingsOpen = false }
    BackHandler(!settingsOpen && insightsSelected) { insightsSelected = false }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    if (settingsOpen) {
                        IconButton(onClick = { settingsOpen = false }) {
                            AppIcon(R.drawable.ic_back, stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (!settingsOpen && !needsSetup) {
                        IconButton(onClick = { settingsOpen = true }) {
                            AppIcon(R.drawable.ic_settings, stringResource(R.string.settings))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (!settingsOpen && !needsSetup) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBarItem(
                        selected = !insightsSelected,
                        onClick = { insightsSelected = false },
                        icon = { AppIcon(R.drawable.ic_today) },
                        label = { Text(stringResource(R.string.today)) },
                    )
                    NavigationBarItem(
                        selected = insightsSelected,
                        onClick = { insightsSelected = true },
                        icon = { AppIcon(R.drawable.ic_insights) },
                        label = { Text(stringResource(R.string.insights)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                needsSetup -> Column(Modifier.verticalScroll(rememberScrollState())) { SetupScreen(vm) }
                settingsOpen -> Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) { SettingsShell(vm) }
                insightsSelected -> Column(Modifier.padding(20.dp)) { EmptyState(
                    R.drawable.ic_insights,
                    R.string.insights_empty_title,
                    R.string.insights_empty_body,
                ) }
                else -> TodayScreen(vm)
            }
        }
    }
    vm.draft?.let { EntrySheet(vm, it) }
}

@Composable
private fun EmptyState(@DrawableRes icon: Int, @StringRes title: Int, @StringRes body: Int) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsShell(vm: TimeLogViewModel) {
    var categoriesOpen by rememberSaveable { mutableStateOf(false) }
    ReminderSettings(vm)
    Spacer(Modifier.height(24.dp))
    androidx.compose.material3.OutlinedButton(onClick = { categoriesOpen = true }, enabled = vm.ready,
        modifier = Modifier.fillMaxWidth()) { Text("Manage categories") }
    Spacer(Modifier.height(24.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(28.dp))
    Text(stringResource(R.string.local_data_title), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.local_data_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (categoriesOpen) CategoryManager(vm) { categoriesOpen = false }
}

@Composable
private fun AppIcon(@DrawableRes drawable: Int, description: String? = null) {
    Icon(painterResource(drawable), contentDescription = description, modifier = Modifier.size(24.dp))
}
