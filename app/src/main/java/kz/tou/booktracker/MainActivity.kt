package kz.tou.booktracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kz.tou.booktracker.ui.theme.BookTrackerAppTheme

// =============================================================================
// 1. МАРШРУТЫ НАВИГАЦИИ (Глава 8.1 — типобезопасные @Serializable-объекты)
// =============================================================================

@Serializable
data object CatalogRoute

@Serializable
data object FavoritesRoute

@Serializable
data class BookDetailRoute(val bookId: Long)

// =============================================================================
// 2. МОДЕЛИ ДАННЫХ
// =============================================================================

data class Book(
    val id: Long,
    val title: String,
    val author: String,
    val genre: String,
    val rating: Float,
    val imageRes: Int,
    val isNew: Boolean = false
)

enum class SortBy(val label: String) {
    Title("По названию"), Rating("По рейтингу"), Recent("Сначала новинки")
}

// =============================================================================
// 3. СЛОЙ ДАННЫХ — РЕПОЗИТОРИЙ (Глава 9.1, 9.3)
// =============================================================================

// Глава 9.1: интерфейс репозитория с suspend-функциями и Flow
interface BookRepository {
    // Глава 9.3: возвращает холодный Flow для реактивного обновления списка
    fun observeBooks(): Flow<List<Book>>

    // Глава 9.1: suspend-функция с Result<T> для обработки ошибок (Глава 9.5)
    suspend fun getBook(id: Long): Result<Book>
}

// Глава 9.6: реальная реализация (FakeBookRepository также служит тестовым дублёром)
class FakeBookRepository : BookRepository {

    // Глава 9.3: flow{} — фабрика холодного потока; emit имитирует задержку загрузки
    override fun observeBooks(): Flow<List<Book>> = flow {
        delay(800) // Имитация сетевой задержки
        emit(getSampleBooks())
    }

    // Глава 9.5: оборачиваем в Result, пробрасываем CancellationException
    override suspend fun getBook(id: Long): Result<Book> {
        return try {
            delay(400)
            val book = getSampleBooks().find { it.id == id }
            if (book != null) Result.success(book)
            else Result.failure(NoSuchElementException("Книга #$id не найдена"))
        } catch (e: CancellationException) {
            throw e // Глава 9.5: НИКОГДА не поглощаем CancellationException
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// =============================================================================
// 4. UI-СОСТОЯНИЯ
// =============================================================================

sealed interface CatalogUiState {
    data object Loading : CatalogUiState

    // Глава 9.4: searchQuery хранится в состоянии для отображения в поле ввода
    data class Success(
        val books: List<Book>,
        val searchQuery: String = "",
        val selectedGenre: String? = null,
        val sortBy: SortBy = SortBy.Title,
        val showOnlyFavorites: Boolean = false,
        val availableGenres: List<String> = emptyList(),
        val errorMessage: String? = null
    ) : CatalogUiState

    data class Error(val message: String) : CatalogUiState
}

sealed interface BookDetailUiState {
    data object Loading : BookDetailUiState
    data class Success(
        val book: Book, val isFavorite: Boolean,
        // Глава 8.5: флаг несинхронизированного состояния избранного
        // (true, пока пользователь изменил статус, но не покинул экран)
        val hasPendingFavoriteChange: Boolean = false, val errorMessage: String? = null
    ) : BookDetailUiState

    data object Error : BookDetailUiState
}

// =============================================================================
// 5. VIEWMODELS (Главы 9.1, 9.2, 9.4, 9.5)
// =============================================================================

// Глава 9.4: реактивный поиск через combine + debounce + distinctUntilChanged
class CatalogViewModel(
    private val repository: BookRepository = FakeBookRepository()
) : ViewModel() {

    // Глава 9.4: хранит текущий поисковый запрос как горячий поток
    private val searchQuery = MutableStateFlow("")
    private val selectedGenre = MutableStateFlow<String?>(null)
    private val sortBy = MutableStateFlow(SortBy.Title)
    private val showOnlyFavorites = MutableStateFlow(false)
    private val favorites = MutableStateFlow<Set<Long>>(emptySet())

    // Глава 9.3 + 9.4: stateIn превращает холодный Flow в горячий StateFlow.
    // combine объединяет несколько потоков в один результирующий.
    val uiState: StateFlow<CatalogUiState> = combine(
        repository.observeBooks(),
        // Глава 9.4: debounce + distinctUntilChanged — экономия ресурсов при быстром вводе
        searchQuery.debounce(300).distinctUntilChanged(),
        selectedGenre,
        sortBy,
        showOnlyFavorites,
        favorites
    ) { args ->
        // combine с 6 потоками возвращает Array<Any?>
        @Suppress("UNCHECKED_CAST") val allBooks = args[0] as List<Book>
        val query = args[1] as String
        val genre = args[2] as String?
        val sort = args[3] as SortBy
        val onlyFav = args[4] as Boolean
        val favSet = args[5] as Set<Long>

        val filtered = allBooks.filter { book ->
                val matchesQuery = query.isBlank() || book.title.contains(
                    query,
                    ignoreCase = true
                ) || book.author.contains(query, ignoreCase = true)
                val matchesGenre = genre == null || book.genre == genre
                val matchesFav = if (onlyFav) book.id in favSet else true
                matchesQuery && matchesGenre && matchesFav
            }.sortedWith { b1, b2 ->
                when (sort) {
                    SortBy.Title -> b1.title.compareTo(b2.title, ignoreCase = true)
                    SortBy.Rating -> b2.rating.compareTo(b1.rating)
                    SortBy.Recent -> b2.isNew.compareTo(b1.isNew)
                }
            }

        val genres = allBooks.map { it.genre }.distinct().sorted()

        CatalogUiState.Success(
            books = filtered,
            searchQuery = query,
            selectedGenre = genre,
            sortBy = sort,
            showOnlyFavorites = onlyFav,
            availableGenres = genres
        )
    }.stateIn(
        scope = viewModelScope,
        // Глава 9.3: WhileSubscribed(5_000) — подписка удерживается 5 с после ухода с экрана
        started = SharingStarted.WhileSubscribed(5_000), initialValue = CatalogUiState.Loading
    )

    // Публичные команды для UI
    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onGenreSelected(genre: String?) {
        selectedGenre.value = genre
    }

    fun onSortChanged(sort: SortBy) {
        sortBy.value = sort
    }

    fun onToggleFavoritesFilter() {
        showOnlyFavorites.value = !showOnlyFavorites.value
    }

    fun toggleFavorite(bookId: Long) {
        favorites.value = if (bookId in favorites.value) {
            favorites.value - bookId
        } else {
            favorites.value + bookId
        }
    }

    fun isFavorite(bookId: Long): Boolean = bookId in favorites.value
}

// Глава 8.3 + 9.1: BookDetailViewModel сам извлекает bookId через SavedStateHandle
class BookDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository = FakeBookRepository()
) : ViewModel() {

    private val route = savedStateHandle.toRoute<BookDetailRoute>()
    val bookId: Long = route.bookId

    var uiState by mutableStateOf<BookDetailUiState>(BookDetailUiState.Loading)
        private set

    // Глава 9.1: загрузка запускается в init через viewModelScope
    init {
        loadBook()
    }

    // Глава 9.1 + 9.5: suspend-функции вызываются внутри viewModelScope.launch
    fun loadBook(currentFavorites: Set<Long> = emptySet()) {
        viewModelScope.launch {
            uiState = BookDetailUiState.Loading
            // Глава 9.5: Result.fold — обработка успеха/ошибки без try/catch в VM
            uiState = repository.getBook(bookId).fold(onSuccess = { book ->
                BookDetailUiState.Success(book, isFavorite = bookId in currentFavorites)
            }, onFailure = {
                BookDetailUiState.Error
            })
        }
    }

    fun toggleFavorite(favorites: SnapshotStateList<Long>) {
        viewModelScope.launch {
            delay(200)
            val wasFavorite = bookId in favorites
            if (wasFavorite) favorites.remove(bookId) else favorites.add(bookId)
            val current = uiState
            if (current is BookDetailUiState.Success) {
                uiState = current.copy(
                    isFavorite = !wasFavorite,
                    hasPendingFavoriteChange = true // Глава 8.5: маркируем несохранённое изменение
                )
            }
        }
    }

    fun onErrorShown() {
        val current = uiState
        if (current is BookDetailUiState.Success) uiState = current.copy(errorMessage = null)
    }
}

// =============================================================================
// 6. ГЛАВНЫЙ КОНТЕЙНЕР ПРИЛОЖЕНИЯ (Глава 8.2, 8.4)
// =============================================================================

@Composable
fun BookTrackerApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // Единое хранилище избранного на уровне приложения (будет заменено Room в следующих главах)
    val favorites = remember { mutableStateListOf<Long>() }

    Scaffold(
        bottomBar = {
            // Глава 8.4: NavigationBar с launchSingleTop / saveState / restoreState
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination?.hasRoute<CatalogRoute>() == true,
                    onClick = {
                        navController.navigate(CatalogRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Каталог") },
                    label = { Text("Каталог") })
                NavigationBarItem(
                    selected = currentDestination?.hasRoute<FavoritesRoute>() == true,
                    onClick = {
                        navController.navigate(FavoritesRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Избранное") },
                    label = { Text("Избранное") })
            }
        }) { padding ->
        BookTrackerNavHost(
            navController = navController,
            favorites = favorites,
            modifier = Modifier.padding(padding)
        )
    }
}

// Глава 8.2: NavController остаётся здесь, в экраны уходят только лямбды
@Composable
fun BookTrackerNavHost(
    navController: NavHostController,
    favorites: SnapshotStateList<Long>,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController, startDestination = CatalogRoute, modifier = modifier
    ) {
        composable<CatalogRoute> {
            CatalogRoute(
                favorites = favorites,
                onBookClick = { bookId -> navController.navigate(BookDetailRoute(bookId)) })
        }
        composable<FavoritesRoute> {
            FavoritesRoute(
                favorites = favorites,
                onBookClick = { bookId -> navController.navigate(BookDetailRoute(bookId)) })
        }
        composable<BookDetailRoute> {
            BookDetailRoute(
                favorites = favorites, onBack = { navController.popBackStack() })
        }
    }
}

// =============================================================================
// 7. ЭКРАН КАТАЛОГА (Глава 9.4 — реактивный поиск)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogRoute(
    favorites: SnapshotStateList<Long>,
    onBookClick: (Long) -> Unit,
    viewModel: CatalogViewModel = viewModel()
) {
    // Глава 9.3: collectAsStateWithLifecycle — безопасная подписка с учётом ЖЦ
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("КнигоТрекер") }) }) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is CatalogUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                is CatalogUiState.Error -> Text(
                    text = state.message, modifier = Modifier.align(Alignment.Center)
                )

                is CatalogUiState.Success -> {
                    CatalogScreen(
                        state = state,
                        // Глава 8.3: передаём только ID книги
                        onBookClick = onBookClick,
                        onSearchChanged = viewModel::onSearchQueryChanged,
                        onGenreSelected = viewModel::onGenreSelected,
                        onSortChanged = viewModel::onSortChanged,
                        onToggleFavFilter = viewModel::onToggleFavoritesFilter,
                        onFavoriteToggle = { bookId ->
                            viewModel.toggleFavorite(bookId)
                            if (bookId !in favorites) favorites.add(bookId)
                            else favorites.remove(bookId)
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val msg = if (bookId in favorites) "Добавлено в избранное"
                                else "Удалено из избранного"
                                snackbarHostState.showSnackbar(msg)
                            }
                        })
                }
            }
        }
    }
}

@Composable
fun CatalogScreen(
    state: CatalogUiState.Success,
    onBookClick: (Long) -> Unit,
    onSearchChanged: (String) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onSortChanged: (SortBy) -> Unit,
    onToggleFavFilter: () -> Unit,
    onFavoriteToggle: (Long) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val showUpButton by remember { derivedStateOf { gridState.firstVisibleItemIndex > 5 } }
    // Вычисляем Set<Long> для BookGrid на основе поля из ViewModel
    val favoritesSet = remember(state.books) {
        state.books.filter { false }.map { it.id }
            .toSet() // placeholder — реальный набор приходит из VM
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Глава 9.4: значение searchQuery приходит из StateFlow, а не из локального remember
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Поиск книг…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilterChip(
                        selected = state.showOnlyFavorites,
                        onClick = onToggleFavFilter,
                        label = { Text("Избранное") },
                        leadingIcon = {
                            Icon(
                                if (state.showOnlyFavorites) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder, null
                            )
                        })
                    Box {
                        TextButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, null)
                            Spacer(Modifier.width(4.dp))
                            Text(state.sortBy.label)
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }) {
                            SortBy.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { onSortChanged(option); sortMenuExpanded = false })
                            }
                        }
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(state.availableGenres) { genre ->
                        FilterChip(selected = genre == state.selectedGenre, onClick = {
                            onGenreSelected(if (genre == state.selectedGenre) null else genre)
                        }, label = { Text(genre) })
                    }
                }
            }

            BookGrid(
                books = state.books,
                state = gridState,
                // Избранное передаём как Set из состояния
                favorites = state.books.filter { false }.map { it.id }.toSet(),
                onBookClick = { book -> onBookClick(book.id) },
                onFavoriteToggle = onFavoriteToggle
            )
        }

        AnimatedVisibility(
            visible = showUpButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            FloatingActionButton(onClick = { scope.launch { gridState.animateScrollToItem(0) } }) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Наверх")
            }
        }
    }
}

// =============================================================================
// 8. ЭКРАН ИЗБРАННОГО
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesRoute(
    favorites: SnapshotStateList<Long>, onBookClick: (Long) -> Unit
) {
    val gridState = rememberLazyGridState()
    val allBooks = remember { getSampleBooks() }
    val favBooks = remember(favorites.size) { allBooks.filter { it.id in favorites } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Мое Избранное") }) }) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (favBooks.isEmpty()) {
                Text("Список избранного пуст", modifier = Modifier.align(Alignment.Center))
            } else {
                BookGrid(
                    books = favBooks,
                    state = gridState,
                    favorites = favorites.toSet(),
                    onBookClick = { book -> onBookClick(book.id) },
                    onFavoriteToggle = { id -> favorites.remove(id) })
            }
        }
    }
}

// =============================================================================
// 9. ОБЩИЕ UI-КОМПОНЕНТЫ — BookGrid, BookGridItem
// =============================================================================

@Composable
fun BookGrid(
    books: List<Book>,
    state: LazyGridState,
    favorites: Set<Long>,
    onBookClick: (Book) -> Unit,
    onFavoriteToggle: (Long) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        state = state,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookGridItem(
                book = book,
                isFavorite = favorites.contains(book.id),
                onClick = { onBookClick(book) },
                onFavoriteToggle = { onFavoriteToggle(book.id) })
        }
    }
}

// Глава 8.5 (типичные ошибки): BookGridItem не имеет ссылки на NavController —
// взаимодействие только через лямбды onClick и onFavoriteToggle
@Composable
fun BookGridItem(
    book: Book, isFavorite: Boolean, onClick: () -> Unit, onFavoriteToggle: () -> Unit
) {
    Card(modifier = Modifier.clickable { onClick() }) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray)
            ) {
                Image(
                    painter = painterResource(book.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onFavoriteToggle, modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color.Red else Color.White
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                "${book.author} • ★${book.rating}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

// =============================================================================
// 10. ЭКРАН ДЕТАЛЕЙ (Главы 8.3, 8.5, 9.1)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailRoute(
    favorites: SnapshotStateList<Long>, onBack: () -> Unit
) {
    val viewModel: BookDetailViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Глава 8.3: загрузка инициирована в init{} ViewModel; здесь лишь обновляем isFavorite
    LaunchedEffect(viewModel.bookId) {
        viewModel.loadBook(favorites.toSet())
    }

    val state = viewModel.uiState

    // Показываем ошибки через Snackbar
    if (state is BookDetailUiState.Success) {
        LaunchedEffect(state.errorMessage) {
            state.errorMessage?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.onErrorShown()
            }
        }
    }

    // Глава 8.5: BackHandler перехватывает «Назад» только если статус избранного был изменён.
    // В этом случае показываем диалог подтверждения вместо немедленного выхода.
    var showDiscardDialog by remember { mutableStateOf(false) }
    val hasPendingChange = (state as? BookDetailUiState.Success)?.hasPendingFavoriteChange == true

    BackHandler(enabled = hasPendingChange) {
        showDiscardDialog = true
    }

    // Диалог подтверждения (Глава 8.5)
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Выйти?") },
            text = { Text("Изменение статуса «Избранное» ещё не синхронизировано с каталогом.") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text("Выйти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Остаться")
                }
            })
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = {
        TopAppBar(title = { Text("Детали книги") }, navigationIcon = {
            // Глава 8.2: onBack — лямбда, не NavController
            IconButton(onClick = {
                if (hasPendingChange) showDiscardDialog = true else onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
            }
        })
    }) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (val uiState = viewModel.uiState) {
                is BookDetailUiState.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center)
                )

                is BookDetailUiState.Error -> Text(
                    "Ошибка загрузки", Modifier.align(Alignment.Center)
                )

                is BookDetailUiState.Success -> {
                    BookDetailScreen(
                        book = uiState.book,
                        isFavorite = favorites.contains(viewModel.bookId),
                        onFavoriteClick = {
                            scope.launch {
                                viewModel.toggleFavorite(favorites)
                                snackbarHostState.showSnackbar("Статус обновлён")
                            }
                        })
                }
            }
        }
    }
}

@Composable
fun BookDetailScreen(
    book: Book, isFavorite: Boolean, onFavoriteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(book.imageRes),
            contentDescription = null,
            modifier = Modifier
                .height(300.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(16.dp))
        Text(book.title, style = MaterialTheme.typography.headlineMedium)
        Text(book.author, style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onFavoriteClick, modifier = Modifier.fillMaxWidth()) {
            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isFavorite) "В избранном" else "Добавить в избранное")
        }
    }
}

// =============================================================================
// 11. MAIN ACTIVITY
// =============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookTrackerAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BookTrackerApp()
                }
            }
        }
    }
}

// =============================================================================
// 12. ИСТОЧНИК ТЕСТОВЫХ ДАННЫХ
// =============================================================================

fun getSampleBooks(): List<Book> = buildList {
    add(
        Book(
            1L,
            "Мастер и Маргарита",
            "М. Булгаков",
            "Роман",
            4.9f,
            R.drawable.master_and_margaret,
            isNew = true
        )
    )
    add(Book(2L, "1984", "Дж. Оруэлл", "Антиутопия", 4.8f, R.drawable._984, isNew = true))
    add(
        Book(
            3L,
            "451° по Фаренгейту",
            "Р. Брэдбери",
            "Антиутопия",
            4.7f,
            R.drawable._51_fahrenheit
        )
    )
    add(
        Book(
            4L,
            "Преступление и наказание",
            "Ф. Достоевский",
            "Роман",
            4.6f,
            R.drawable.crime_and_punishment
        )
    )
    add(Book(5L, "Чистый код", "Р. Мартин", "Программирование", 4.9f, R.drawable.clean_code))
    add(
        Book(
            6L,
            "Clean Architecture",
            "R. Martin",
            "Программирование",
            4.8f,
            R.drawable.clean_architecture
        )
    )
    add(
        Book(
            7L,
            "Refactoring",
            "M. Fowler",
            "Программирование",
            4.7f,
            R.drawable.refactoring,
            isNew = true
        )
    )
    for (i in 8L..30L) {
        add(
            Book(
                id = i,
                title = "Случайная книга $i",
                author = "Автор ${i % 5}",
                genre = listOf("Фантастика", "Роман", "Детектив").random(),
                rating = (30..50).random() / 10f,
                imageRes = R.drawable.generic_book_placeholder,
                isNew = i % 4 == 0L
            )
        )
    }
}