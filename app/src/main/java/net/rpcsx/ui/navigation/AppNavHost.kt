package net.rpcsx.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import net.rpcsx.*
import net.rpcsx.dialogs.AlertDialogQueue
import net.rpcsx.overlay.OverlayEditActivity
import net.rpcsx.ui.channels.*
import net.rpcsx.ui.drivers.GpuDriversScreen
import net.rpcsx.ui.games.GamesScreen
import net.rpcsx.ui.settings.*
import net.rpcsx.ui.user.UsersScreen
import net.rpcsx.utils.FileUtil
import net.rpcsx.utils.RpcsxUpdater
import org.json.JSONObject

@Preview
@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val rpcsxLibrary by remember { RPCSX.activeLibrary }

    // Channels
    var gpuDriverChannels by remember {
        mutableStateOf(prefs.getStringSet("gpu_driver_channel_list", setOf(DefaultGpuDriverChannel))?.toList() ?: listOf(DefaultGpuDriverChannel))
    }
    var uiChannels by remember {
        mutableStateOf(prefs.getStringSet("ui_channel_list", setOf(ReleaseUiChannel, DevUiChannel))?.toList() ?: listOf(ReleaseUiChannel, DevUiChannel))
    }
    var rpcsxChannels by remember {
        mutableStateOf(prefs.getStringSet("rpcsx_channel_list", setOf(ReleaseRpcsxChannel, DevRpcsxChannel))?.toList() ?: listOf(ReleaseRpcsxChannel, DevRpcsxChannel))
    }

    val isValidChannel = { channel: String, releaseRepo: String, devRepo: String ->
        channel != "Release" && channel != "Development" && channel != releaseRepo && channel != devRepo
    }

    if (prefs.getString("gpu_driver_channel", "").isNullOrEmpty()) prefs.edit { putString("gpu_driver_channel", DefaultGpuDriverChannel) }
    if (prefs.getString("ui_channel", "").isNullOrEmpty()) prefs.edit { putString("ui_channel", ReleaseUiChannel) }
    if (prefs.getString("rpcsx_channel", "").isNullOrEmpty()) prefs.edit { putString("rpcsx_channel", ReleaseRpcsxChannel) }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    AlertDialogQueue.AlertDialog()

    if (rpcsxLibrary == null) {
        GamesDestination(navigateToSettings = { }, drawerState = drawerState)
        return
    }

    val settings = remember { mutableStateOf(JSONObject(RPCSX.instance.settingsGet(""))) }
    val refreshSettings: () -> Unit = { settings.value = JSONObject(RPCSX.instance.settingsGet("")) }

    NavHost(navController = navController, startDestination = "games") {

        composable("games") { GamesDestination(navigateToSettings = { navController.navigate("settings") }, drawerState) }
        composable("users") { UsersScreen(navigateBack = navController::navigateUp) }
        composable("settings") { SettingsScreen(navigateBack = navController::navigateUp, navigateTo = navController::navigate, onRefresh = refreshSettings) }
        composable("controls") { ControllerSettings(navigateBack = navController::navigateUp) }
        composable("drivers") { GpuDriversScreen(navigateBack = navController::navigateUp) }
        composable("update_channels") { UpdateChannelsScreen(navigateBack = navController::navigateUp, navigateTo = navController::navigate) }
        composable("gpu_driver_channels") {
            UpdateChannelListScreen(
                navigateBack = navController::navigateUp,
                title = stringResource(R.string.driver_download_channel),
                items = gpuDriverChannels.toList(),
                selected = prefs.getString("gpu_driver_channel", null),
                onSelect = { channel -> prefs.edit { putString("gpu_driver_channel", channel) }; navController.navigateUp() },
                onDelete = { channel -> gpuDriverChannels = gpuDriverChannels.filter { it != channel }; prefs.edit { putStringSet("gpu_driver_channel_list", gpuDriverChannels.toSet()) } },
                onAdd = { channel -> if (gpuDriverChannels.find { it == channel } == null) { gpuDriverChannels += channel; prefs.edit { putStringSet("gpu_driver_channel_list", gpuDriverChannels.toSet()) } } },
                isDeletable = { gpuDriverChannels.size > 1 }
            )
        }
        composable("ui_channels") {
            UpdateChannelListScreen(
                navigateBack = navController::navigateUp,
                title = stringResource(R.string.ui_update_channel),
                items = channelsToUiText(uiChannels, ReleaseUiChannel, DevUiChannel),
                selected = channelToUiText(prefs.getString("ui_channel", ReleaseUiChannel)!!, ReleaseUiChannel, DevUiChannel),
                onSelect = { channel -> prefs.edit { putString("ui_channel", uiTextToChannel(channel, ReleaseUiChannel, DevUiChannel)) }; navController.navigateUp() },
                onDelete = { channel -> uiChannels = uiChannels.filter { it != channel }; prefs.edit { putStringSet("ui_channel_list", uiTextToChannels(uiChannels, ReleaseUiChannel, DevUiChannel).toSet()) } },
                onAdd = { channel -> if (isValidChannel(channel, ReleaseUiChannel, DevUiChannel) && uiChannels.find { it == channel } == null) { uiChannels += channel; prefs.edit { putStringSet("ui_channel_list", uiTextToChannels(uiChannels, ReleaseUiChannel, DevUiChannel).toSet()) } } },
                isDeletable = { isValidChannel(it, ReleaseUiChannel, DevUiChannel) }
            )
        }
        composable("rpcsx_channels") {
            UpdateChannelListScreen(
                navigateBack = navController::navigateUp,
                title = stringResource(R.string.rpcsx_download_channel),
                items = channelsToUiText(rpcsxChannels, ReleaseRpcsxChannel, DevRpcsxChannel),
                selected = channelToUiText(prefs.getString("rpcsx_channel", ReleaseRpcsxChannel)!!, ReleaseRpcsxChannel, DevRpcsxChannel),
                onSelect = { channel -> prefs.edit { putString("rpcsx_channel", uiTextToChannel(channel, ReleaseRpcsxChannel, DevRpcsxChannel)) }; navController.navigateUp() },
                onDelete = { channel -> rpcsxChannels = rpcsxChannels.filter { it != channel }; prefs.edit { putStringSet("rpcsx_channel_list", uiTextToChannels(rpcsxChannels, ReleaseRpcsxChannel, DevRpcsxChannel).toSet()) } },
                onAdd = { channel -> if (isValidChannel(channel, ReleaseRpcsxChannel, DevRpcsxChannel) && rpcsxChannels.find { it == channel } == null) { rpcsxChannels += channel; prefs.edit { putStringSet("rpcsx_channel_list", uiTextToChannels(rpcsxChannels, ReleaseRpcsxChannel, DevRpcsxChannel).toSet()) } } }
            )
        }

        // Advanced settings recursion
        fun unwrapSetting(obj: JSONObject, path: String = "") {
            obj.keys().forEach self@{ key ->
                val item = obj[key]
                val elemPath = "$path@@$key"
                val elemObject = item as? JSONObject ?: return@self
                if (elemObject.has("type")) return@self

                composable("settings$elemPath") {
                    AdvancedSettingsScreen(navigateBack = navController::navigateUp, navigateTo = navController::navigate, settings = elemObject, path = elemPath)
                }
                unwrapSetting(elemObject, elemPath)
            }
        }

        unwrapSetting(settings.value)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesDestination(navigateToSettings: () -> Unit, drawerState: DrawerState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var emulatorState by remember { RPCSX.state }
    val emulatorActiveGame by remember { RPCSX.activeGame }
    val rpcsxLibrary by remember { RPCSX.activeLibrary }

    val fpsCounter = "60"           // placeholder
    val tempVal = "40°C"            // placeholder

    val installPkgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { PrecompilerService.start(context, PrecompilerServiceAction.Install, it) }
    }
    val gameFolderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            FileUtil.installPackages(context, it)
        }
    }

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet {
            Column(modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    label = { Text("Firmware: ${FirmwareRepository.version.value ?: "None"}") },
                    selected = false,
                    icon = { Icon(painterResource(R.drawable.hard_drive), contentDescription = null) },
                    onClick = {}
                )
                HorizontalDivider()
                NavigationDrawerItem(label = { Text(stringResource(R.string.settings)) }, selected = false, icon = { Icon(Icons.Default.Settings, null) }, onClick = navigateToSettings)
                NavigationDrawerItem(label = { Text(stringResource(R.string.edit_overlay)) }, selected = false, icon = { Icon(painterResource(R.drawable.ic_show_osc), null) }, onClick = {
                    context.startActivity(Intent(context, OverlayEditActivity::class.java))
                })
                NavigationDrawerItem(label = { Text(stringResource(R.string.device_info)) }, selected = false, icon = { Icon(painterResource(R.drawable.perm_device_information), null) }, onClick = {
                    AlertDialogQueue.showDialog(
                        context.getString(R.string.device_info),
                        RPCSX.instance.systemInfo(),
                        confirmText = context.getString(android.R.string.copy),
                        dismissText = context.getString(R.string.close),
                        onConfirm = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Device Info", RPCSX.instance.systemInfo()))
                            Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                        }
                    )
                })
            }
        }
    }) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("RPCSX", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                        }
                    },
                    actions = {
                        if (emulatorActiveGame != null && emulatorState != EmulatorState.Stopped && emulatorState != EmulatorState.Stopping) {
                            IconButton(onClick = { emulatorState = EmulatorState.Stopped; RPCSX.instance.kill() }) {
                                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_stop), contentDescription = null)
                            }
                        }
                    }
                )
            },
            floatingActionButton = { DropUpFloatingActionButton(installPkgLauncher, gameFolderPickerLauncher) },
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                GamesScreen()
            }
        }
    }
}

@Composable
fun DropUpFloatingActionButton(installPkgLauncher: ActivityResultLauncher<String>, gameFolderPickerLauncher: ActivityResultLauncher<Uri?>) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.BottomEnd) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)),
                exit = androidx.compose.animation.shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FloatingActionButton(onClick = { installPkgLauncher.launch("*/*"); expanded = false }, containerColor = MaterialTheme.colorScheme.secondary) {
                        Icon(painter = painterResource(R.drawable.ic_description), contentDescription = "Select Game")
                    }
                    FloatingActionButton(onClick = { gameFolderPickerLauncher.launch(null); expanded = false }, containerColor = MaterialTheme.colorScheme.secondary) {
                        Icon(painter = painterResource(R.drawable.ic_folder), contentDescription = "Select Folder")
                    }
                }
            }
            FloatingActionButton(onClick = { expanded = !expanded }) { Icon(Icons.Filled.Add, contentDescription = "Add") }
        }
    }
}
