package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.favorites.FavoritesScreen
import com.example.ui.home.CategoryDocumentsScreen
import com.example.ui.home.HomeScreen
import com.example.ui.navigation.Screen
import com.example.ui.recent.RecentScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewer.DocumentViewer
import com.example.viewer.ViewerStatus

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            val settings by viewModel.readerSettings.collectAsStateWithLifecycle()

            MyApplicationTheme(themeSetting = settings.theme) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data: Uri? = intent.data
        if ((Intent.ACTION_VIEW == action || Intent.ACTION_EDIT == action) && data != null) {
            viewModel.openDocument(data)
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val recentDocs by viewModel.recentDocuments.collectAsStateWithLifecycle()
    val favoriteDocs by viewModel.favoriteDocuments.collectAsStateWithLifecycle()
    val categoryDocs by viewModel.categoryDocuments.collectAsStateWithLifecycle()
    val isScanningCategory by viewModel.isScanningCategory.collectAsStateWithLifecycle()
    val viewerState by viewModel.viewerState.collectAsStateWithLifecycle()
    val settings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()

    // When document viewer is active, it takes over as a full-screen experience
    // Bottom navigation is strictly NOT displayed inside the document reader
    val isViewerActive = viewerState.status != ViewerStatus.Idle

    AnimatedContent(
        targetState = isViewerActive,
        transitionSpec = {
            if (targetState) {
                (slideInVertically(animationSpec = tween(280)) { height -> height / 6 } + fadeIn(animationSpec = tween(250)))
                    .togetherWith(fadeOut(animationSpec = tween(150)))
            } else {
                fadeIn(animationSpec = tween(150))
                    .togetherWith(slideOutVertically(animationSpec = tween(250)) { height -> height / 6 } + fadeOut(animationSpec = tween(200)))
            }
        },
        label = "ViewerTransition",
        modifier = Modifier.fillMaxSize()
    ) { viewerActive ->
        if (viewerActive) {
            BackHandler {
                viewModel.closeViewer()
            }

            DocumentViewer(
                state = viewerState,
                renderer = viewModel.getCurrentRenderer(),
                onBack = { viewModel.closeViewer() },
                onToggleFavorite = {
                    viewModel.toggleFavorite(viewerState.uri, viewerState.isFavorite)
                },
                onPageSelected = { page ->
                    viewModel.selectPage(page)
                },
                onToggleControls = {
                    viewModel.toggleViewerControls()
                },
                onToggleSearch = {
                    viewModel.toggleSearch()
                },
                onToggleThumbnails = {
                    viewModel.toggleThumbnails()
                },
                onSearchQueryChanged = { query ->
                    viewModel.searchDocument(query)
                },
                onPageModeChanged = { mode ->
                    viewModel.setPageMode(mode)
                },
                onFitModeChanged = { mode ->
                    viewModel.setFitMode(mode)
                },
                onRetry = {
                    if (viewerState.uri.isNotEmpty()) {
                        viewModel.openDocument(Uri.parse(viewerState.uri))
                    }
                },
                onSelectFileAgain = { newUri ->
                    viewModel.openDocument(newUri)
                },
                onSearchMatchSelected = { match ->
                    viewModel.selectSearchMatch(match)
                },
                onDismissNavigationMessage = {
                    viewModel.clearNavigationMessage()
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AnimatedContent(
                targetState = selectedCategory,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(animationSpec = tween(220)) { width -> width / 4 } + fadeIn(animationSpec = tween(200)))
                            .togetherWith(fadeOut(animationSpec = tween(140)))
                    } else {
                        fadeIn(animationSpec = tween(140))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { width -> width / 4 } + fadeOut(animationSpec = tween(180)))
                    }
                },
                label = "CategoryTransition",
                modifier = Modifier.fillMaxSize()
            ) { category ->
                if (category != null) {
                    BackHandler {
                        viewModel.closeCategory()
                    }

                    CategoryDocumentsScreen(
                        category = category,
                        documents = categoryDocs,
                        isLoading = isScanningCategory,
                        onBack = { viewModel.closeCategory() },
                        onDocumentClick = { uri -> viewModel.openDocument(uri) },
                        onToggleFavorite = { uri, isFav -> viewModel.toggleFavorite(uri, isFav) },
                        onRefresh = { viewModel.syncCategory(category, force = true) },
                        thumbnailManager = viewModel.thumbnailManager,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(modifier = Modifier.testTag("main_bottom_nav")) {
                                Screen.bottomNavTabs.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { viewModel.selectTab(tab) },
                                        icon = {
                                            tab.icon?.let {
                                                Icon(imageVector = it, contentDescription = tab.title)
                                            }
                                        },
                                        label = { Text(tab.title) },
                                        modifier = Modifier.testTag("nav_item_${tab.route}")
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        val screenModifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)

                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180))
                                    .togetherWith(fadeOut(animationSpec = tween(120)))
                            },
                            label = "TabTransition",
                            modifier = Modifier.fillMaxSize()
                        ) { tab ->
                            when (tab) {
                                is Screen.Home -> {
                                    HomeScreen(
                                        onCategoryClick = { cat ->
                                            viewModel.openCategory(cat)
                                        },
                                        onDocumentSelected = { uri ->
                                            viewModel.openDocument(uri)
                                        },
                                        modifier = screenModifier
                                    )
                                }

                                is Screen.Recent -> {
                                    RecentScreen(
                                        recentDocuments = recentDocs,
                                        onDocumentClick = { uri ->
                                            viewModel.openDocument(uri)
                                        },
                                        onToggleFavorite = { uri, isFav ->
                                            viewModel.toggleFavorite(uri, isFav)
                                        },
                                        onClearAllRecent = {
                                            viewModel.clearAllRecent()
                                        },
                                        thumbnailManager = viewModel.thumbnailManager,
                                        modifier = screenModifier
                                    )
                                }

                                is Screen.Favorites -> {
                                    FavoritesScreen(
                                        favoriteDocuments = favoriteDocs,
                                        onDocumentClick = { uri ->
                                            viewModel.openDocument(uri)
                                        },
                                        onToggleFavorite = { uri, isFav ->
                                            viewModel.toggleFavorite(uri, isFav)
                                        },
                                        thumbnailManager = viewModel.thumbnailManager,
                                        modifier = screenModifier
                                    )
                                }

                                is Screen.Settings -> {
                                    SettingsScreen(
                                        settings = settings,
                                        cacheSizeBytes = cacheSize,
                                        onUpdateSettings = { newSettings ->
                                            viewModel.updateSettings(newSettings)
                                        },
                                        onClearCache = {
                                            viewModel.clearCache()
                                        },
                                        modifier = screenModifier
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
