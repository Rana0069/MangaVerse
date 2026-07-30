package eu.kanade.presentation.more.settings.screen.about

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.LogoHeader
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.updaterEnabled
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.Constants
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LinkIcon
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Discord
import tachiyomi.presentation.core.icons.Facebook
import tachiyomi.presentation.core.icons.Github
import tachiyomi.presentation.core.icons.Reddit
import tachiyomi.presentation.core.icons.X
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private val MvRed     = Color(0xFFD32F2F)
private val MvRedDim  = Color(0xFF8B0000)
private val MvBlack   = Color(0xFF0A0A0A)
private val MvWhite   = Color(0xFFF5F5F5)
private val MvGray    = Color(0xFF1A1A1A)
private val MvBorder  = Color(0xFF2C2C2C)
private val MvSubtext = Color(0xFF888888)

object AboutScreen : Screen() {

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val handleBack = LocalBackPress.current
        val navigator = LocalNavigator.currentOrThrow
        var isCheckingUpdates by remember { mutableStateOf(false) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_category_about),
                    navigateUp = if (handleBack != null) handleBack::invoke else null,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            ScrollbarLazyColumn(contentPadding = contentPadding) {

                item { LogoHeader(iconPadding = PaddingValues(vertical = 40.dp)) }

                item {
                    DeveloperSection(
                        onGitHubClick  = { uriHandler.openUri("https://github.com/placeholder") },
                        onDiscordClick = { uriHandler.openUri("https://discord.gg/placeholder") },
                        onEmailClick   = { uriHandler.openUri("mailto:placeholder@example.com") },
                    )
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.version),
                        subtitle = getVersionName(withBuildDate = true),
                        onPreferenceClick = {
                            val deviceInfo = CrashLogUtil(context).getDebugInfo()
                            context.copyToClipboard("Debug information", deviceInfo)
                        },
                    )
                }

                if (updaterEnabled) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.check_for_updates),
                            widget = {
                                AnimatedVisibility(visible = isCheckingUpdates) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp,
                                    )
                                }
                            },
                            onPreferenceClick = {
                                if (!isCheckingUpdates) {
                                    scope.launch {
                                        isCheckingUpdates = true
                                        checkVersion(
                                            context = context,
                                            onAvailableUpdate = { result ->
                                                val updateScreen = NewUpdateScreen(
                                                    versionName   = result.release.version,
                                                    changelogInfo = result.release.info,
                                                    releaseLink   = result.release.releaseLink,
                                                    downloadLink  = result.release.downloadLink,
                                                )
                                                navigator.push(updateScreen)
                                            },
                                            onFinish = { isCheckingUpdates = false },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }

                if (!BuildConfig.DEBUG) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.whats_new),
                            onPreferenceClick = { uriHandler.openUri(RELEASE_URL) },
                        )
                    }
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.licenses),
                        onPreferenceClick = { navigator.push(OpenSourceLicensesScreen()) },
                    )
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.privacy_policy),
                        onPreferenceClick = { uriHandler.openUri("https://mihon.app/privacy/") },
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        LinkIcon(label = stringResource(MR.strings.website), icon = Icons.Outlined.Public, url = "https://mihon.app")
                        LinkIcon(label = "Discord",  icon = CustomIcons.Discord,  url = Constants.URL_DISCORD)
                        LinkIcon(label = "X",        icon = CustomIcons.X,        url = "https://x.com/mihonapp")
                        LinkIcon(label = "Facebook", icon = CustomIcons.Facebook, url = "https://facebook.com/mihonapp")
                        LinkIcon(label = "Reddit",   icon = CustomIcons.Reddit,   url = "https://www.reddit.com/r/mihonapp")
                        LinkIcon(label = "GitHub",   icon = CustomIcons.Github,   url = "https://github.com/mihonapp")
                    }
                }
            }
        }
    }

    @Composable
    private fun DeveloperSection(
        onGitHubClick:  () -> Unit,
        onDiscordClick: () -> Unit,
        onEmailClick:   () -> Unit,
    ) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(
                    initialScale  = 0.93f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMediumLow,
                    ),
                ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 22.dp)
                            .background(
                                Brush.verticalGradient(listOf(MvRed, MvRedDim)),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text          = "Developer",
                        style         = MaterialTheme.typography.titleMedium,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = MvWhite,
                        letterSpacing = 0.8.sp,
                    )
                }

                ElevatedCard(
                    shape     = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                    colors    = CardDefaults.elevatedCardColors(containerColor = MvGray),
                    modifier  = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MvBorder, RoundedCornerShape(20.dp)),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(listOf(MvRed, MvRedDim, MvBlack)),
                                    ),
                            ) {
                                Text(text = "R", color = MvWhite, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                            }

                            Spacer(Modifier.width(16.dp))

                            Column {
                                Text(text = "Rana", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MvWhite)
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Brush.horizontalGradient(listOf(MvRed, MvRedDim)), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(text = "Founder & Lead Developer", style = MaterialTheme.typography.labelSmall, color = MvWhite, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = MvBorder)
                        Spacer(Modifier.height(16.dp))

                        InfoRow(label = "App", value = "MangaVerse")

                        Spacer(Modifier.height(14.dp))

                        Card(
                            shape    = RoundedCornerShape(12.dp),
                            colors   = CardDefaults.cardColors(containerColor = MvBlack),
                            modifier = Modifier.fillMaxWidth().border(1.dp, MvBorder, RoundedCornerShape(12.dp)),
                        ) {
                            Text(
                                text       = "MangaVerse is a modern open-source manga reader built for speed, beautiful design, and an exceptional reading experience.",
                                style      = MaterialTheme.typography.bodyMedium,
                                color      = Color(0xFFCCCCCC),
                                lineHeight = 22.sp,
                                modifier   = Modifier.padding(14.dp),
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = MvBorder)
                        Spacer(Modifier.height(14.dp))

                        Text(text = "LINKS", style = MaterialTheme.typography.labelSmall, color = MvSubtext, letterSpacing = 2.sp)
                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinkChip(label = "GitHub",  icon = CustomIcons.Github,   onClick = onGitHubClick)
                            LinkChip(label = "Discord", icon = CustomIcons.Discord,  onClick = onDiscordClick)
                            LinkChip(label = "Email",   icon = Icons.Filled.Email,   onClick = onEmailClick)
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MvSubtext)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MvWhite, fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable
    private fun LinkChip(label: String, icon: ImageVector, onClick: () -> Unit) {
        Card(
            onClick  = onClick,
            shape    = RoundedCornerShape(10.dp),
            colors   = CardDefaults.cardColors(containerColor = MvBlack),
            modifier = Modifier.border(1.dp, MvBorder, RoundedCornerShape(10.dp)),
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = MvRed, modifier = Modifier.size(16.dp))
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = MvWhite)
            }
        }
    }

    private suspend fun checkVersion(
        context: Context,
        onAvailableUpdate: (GetApplicationRelease.Result.NewUpdate) -> Unit,
        onFinish: () -> Unit,
    ) {
        val updateChecker = AppUpdateChecker()
        withUIContext {
            try {
                when (val result = withIOContext { updateChecker.checkForUpdate(context, forceCheck = true) }) {
                    is GetApplicationRelease.Result.NewUpdate   -> onAvailableUpdate(result)
                    is GetApplicationRelease.Result.NoNewUpdate -> context.toast(MR.strings.update_check_no_new_updates)
                    is GetApplicationRelease.Result.OsTooOld    -> context.toast(MR.strings.update_check_eol)
                }
            } catch (e: Exception) {
                context.toast(e.message)
                logcat(LogPriority.ERROR, e)
            } finally {
                onFinish()
            }
        }
    }

    fun getVersionName(withBuildDate: Boolean): String {
        return when {
            BuildConfig.DEBUG -> {
                "Debug {BuildConfig.COMMIT_SHA}".let {
                    if (withBuildDate) "it ({getFormattedBuildTime()})" else it
                }
            }
            isPreviewBuildType -> {
                "Beta r{BuildConfig.COMMIT_COUNT}".let {
                    if (withBuildDate) "it ({BuildConfig.COMMIT_SHA}, {getFormattedBuildTime()})"
                    else "it ({BuildConfig.COMMIT_SHA})"
                }
            }
            else -> {
                "Stable {BuildConfig.VERSION_NAME}".let {
                    if (withBuildDate) "it ({getFormattedBuildTime()})" else it
                }
            }
        }
    }

    internal fun getFormattedBuildTime(): String {
        return try {
            LocalDateTime.ofInstant(
                Instant.parse(BuildConfig.BUILD_TIME),
                ZoneId.systemDefault(),
            ).toDateTimestampString(
                UiPreferences.dateFormat(
                    Injekt.get<UiPreferences>().dateFormat.get(),
                ),
            )
        } catch (e: Exception) {
            BuildConfig.BUILD_TIME
        }
    }
}
