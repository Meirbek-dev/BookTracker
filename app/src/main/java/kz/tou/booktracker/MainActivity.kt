package kz.tou.booktracker

import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kz.tou.booktracker.ui.theme.BookTrackerAppTheme

// --- 1. МАРШРУТЫ НАВИГАЦИИ (Типобезопасные) ---

@Serializable
data object CatalogRouteObj

@Serializable
data object FavoritesRouteObj

@Serializable
data class BookDetailRouteObj(val bookId: Long)

// --- 2. МОДЕЛИ ДАННЫХ И СОСТОЯНИЙ ---

data class Book(
    val id: Long,
    val title: String,
    val author: String,
    val genre: String,
    val rating: Float,
    val imageRes: Int,
    val isNew: Boolean = false
)

enum class SortBy(val label: String) { Title("По названию"), Rating("По рейтингу"), Recent("Сначала новинки") }

@Parcelize
data class CatalogFilter(
    val query: String = "",
    val genre: String? = null,
    val sortBy: SortBy = SortBy.Title,
    val showOnlyFavorites: Boolean = false
) : Parcelable

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(
        val books: List<Book>,
        val filter: CatalogFilter = CatalogFilter(),
        val errorMessage: String? = null
    ) : CatalogUiState

    data class Error(val message: String) : CatalogUiState
}

// FIXED: Uncommented the BookDetailUiState correctly
sealed interface BookDetailUiState {
    data object Loading : BookDetailUiState
    data class Success(val book: Book, val isFavorite: Boolean, val errorMessage: String? = null) :
        BookDetailUiState

    data object Error : BookDetailUiState
}

// --- 3. VIEWMODELS ---

// FIXED: Uncommented the CatalogViewModel class declaration
class CatalogViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        loadBooks()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            _uiState.value = CatalogUiState.Loading
            delay(800)
            _uiState.value = CatalogUiState.Success(books = getSampleBooks())
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            if (state is CatalogUiState.Success) state.copy(filter = state.filter.copy(query = query)) else state
        }
    }

    fun onFilterChanged(newFilter: CatalogFilter) {
        _uiState.update { state ->
            if (state is CatalogUiState.Success) state.copy(filter = newFilter) else state
        }
    }
}

class BookDetailViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // ИЗВЛЕКАЕМ bookId ИЗ ПАРАМЕТРОВ НАВИГАЦИИ (Правило 8.3)
    private val route = savedStateHandle.toRoute<BookDetailRouteObj>()
    val bookId = route.bookId

    var uiState by mutableStateOf<BookDetailUiState>(BookDetailUiState.Loading)
        private set

    suspend fun loadBook(favorites: Set<Long>) {
        uiState = BookDetailUiState.Loading
        delay(800)
        val book = getSampleBooks().find { it.id == bookId }
        uiState = if (book != null) {
            BookDetailUiState.Success(book, favorites.contains(bookId))
        } else {
            BookDetailUiState.Error
        }
    }

    fun onErrorShown() {
        val current = uiState
        if (current is BookDetailUiState.Success) uiState = current.copy(errorMessage = null)
    }

    suspend fun toggleFavorite() {
        delay(300)
    }
}

// --- 4. ФУНКЦИИ ФИЛЬТРАЦИИ ---

fun List<Book>.applyFilter(filter: CatalogFilter, favorites: Set<Long>): List<Book> {
    return this.filter { book ->
        val matchesQuery = filter.query.isBlank() || book.title.contains(
            filter.query, true
        ) || book.author.contains(filter.query, true)
        val matchesGenre = filter.genre == null || book.genre == filter.genre
        val matchesFav = if (filter.showOnlyFavorites) favorites.contains(book.id) else true
        matchesQuery && matchesGenre && matchesFav
    }.sortedWith { b1, b2 ->
        when (filter.sortBy) {
            SortBy.Title -> b1.title.compareTo(b2.title, true)
            SortBy.Rating -> b2.rating.compareTo(b1.rating)
            SortBy.Recent -> b2.isNew.compareTo(b1.isNew)
        }
    }
}

// --- 5. ГЛАВНЫЙ КОНТЕЙНЕР НАВИГАЦИИ (App & NavHost) ---

@Composable
fun BookTrackerApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val favorites = remember { mutableStateListOf<Long>() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination?.hasRoute<CatalogRouteObj>() == true,
                    onClick = {
                        navController.navigate(CatalogRouteObj) {
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
                    selected = currentDestination?.hasRoute<FavoritesRouteObj>() == true,
                    onClick = {
                        navController.navigate(FavoritesRouteObj) {
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

@Composable
fun BookTrackerNavHost(
    navController: NavHostController,
    favorites: SnapshotStateList<Long>,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController, startDestination = CatalogRouteObj, modifier = modifier
    ) {
        composable<CatalogRouteObj> {
            CatalogRoute(
                favorites = favorites,
                onBookClick = { book -> navController.navigate(BookDetailRouteObj(book.id)) })
        }
        composable<FavoritesRouteObj> {
            FavoritesRoute(
                favorites = favorites,
                onBookClick = { book -> navController.navigate(BookDetailRouteObj(book.id)) })
        }
        composable<BookDetailRouteObj> {
            BookDetailRoute(
                favorites = favorites, onBack = { navController.popBackStack() })
        }
    }
}

// --- 6. ЭКРАН ИЗБРАННОГО ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesRoute(
    favorites: SnapshotStateList<Long>, onBookClick: (Book) -> Unit
) {
    val gridState = rememberLazyGridState()
    // Получаем книги для избранного напрямую (имитация репозитория)
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
                    onBookClick = onBookClick,
                    onFavoriteToggle = { id -> favorites.remove(id) })
            }
        }
    }
}

// --- 7. ЭКРАН КАТАЛОГА (ROUTE + SCREEN) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogRoute(
    favorites: SnapshotStateList<Long>,
    onBookClick: (Book) -> Unit,
    viewModel: CatalogViewModel = viewModel()
) {
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
                    modifier = Modifier.align(
                        Alignment.Center
                    )
                )

                is CatalogUiState.Error -> Text(
                    text = state.message, modifier = Modifier.align(Alignment.Center)
                )

                is CatalogUiState.Success -> {
                    val filteredBooks by remember(state.books, state.filter, favorites.size) {
                        derivedStateOf { state.books.applyFilter(state.filter, favorites.toSet()) }
                    }
                    val availableGenres =
                        remember(state.books) { state.books.map { it.genre }.distinct().sorted() }

                    CatalogScreen(
                        books = filteredBooks,
                        filter = state.filter,
                        availableGenres = availableGenres,
                        favorites = favorites,
                        onSearchQueryChanged = viewModel::onSearchQueryChanged,
                        onFilterChanged = viewModel::onFilterChanged,
                        onBookClick = onBookClick,
                        onFavoriteToggle = { id ->
                            if (favorites.contains(id)) favorites.remove(id)
                            else {
                                favorites.add(id)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar("Добавлено в избранное")
                                }
                            }
                        })
                }
            }
        }
    }
}

@Composable
fun CatalogScreen(
    books: List<Book>,
    filter: CatalogFilter,
    availableGenres: List<String>,
    favorites: SnapshotStateList<Long>,
    onSearchQueryChanged: (String) -> Unit,
    onFilterChanged: (CatalogFilter) -> Unit,
    onBookClick: (Book) -> Unit,
    onFavoriteToggle: (Long) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val showUpButton by remember { derivedStateOf { gridState.firstVisibleItemIndex > 5 } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = onSearchQueryChanged,
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
                        selected = filter.showOnlyFavorites,
                        onClick = { onFilterChanged(filter.copy(showOnlyFavorites = !filter.showOnlyFavorites)) },
                        label = { Text("Избранное") },
                        leadingIcon = {
                            Icon(
                                if (filter.showOnlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                null
                            )
                        })

                    Box {
                        TextButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, null)
                            Spacer(Modifier.width(4.dp))
                            Text(filter.sortBy.label)
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }) {
                            SortBy.entries.forEach { sortOption ->
                                DropdownMenuItem(text = { Text(sortOption.label) }, onClick = {
                                    onFilterChanged(filter.copy(sortBy = sortOption)); sortMenuExpanded =
                                    false
                                })
                            }
                        }
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(availableGenres) { genre ->
                        FilterChip(
                            selected = genre == filter.genre,
                            onClick = { onFilterChanged(filter.copy(genre = if (genre == filter.genre) null else genre)) },
                            label = { Text(genre) })
                    }
                }
            }

            BookGrid(
                books = books,
                state = gridState,
                favorites = favorites.toSet(),
                onBookClick = onBookClick,
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

// --- 8. ЭКРАН ДЕТАЛЕЙ ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailRoute(
    favorites: SnapshotStateList<Long>, onBack: () -> Unit
) {
    val viewModel: BookDetailViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel.bookId) {
        viewModel.loadBook(favorites.toSet())
    }

    val state = viewModel.uiState
    if (state is BookDetailUiState.Success) {
        LaunchedEffect(state.errorMessage) {
            state.errorMessage?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.onErrorShown()
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = {
        TopAppBar(title = { Text("Детали книги") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        })
    }) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (val uiState = viewModel.uiState) {
                is BookDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is BookDetailUiState.Error -> Text(
                    "Ошибка загрузки", Modifier.align(Alignment.Center)
                )

                is BookDetailUiState.Success -> {
                    BookDetailScreen(
                        book = uiState.book,
                        isFavorite = favorites.contains(viewModel.bookId),
                        onFavoriteClick = {
                            scope.launch {
                                viewModel.toggleFavorite()
                                if (favorites.contains(viewModel.bookId)) favorites.remove(viewModel.bookId)
                                else favorites.add(viewModel.bookId)
                                snackbarHostState.showSnackbar("Статус обновлен")
                            }
                        })
                }
            }
        }
    }
}

@Composable
fun BookDetailScreen(book: Book, isFavorite: Boolean, onFavoriteClick: () -> Unit) {
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

// --- 9. MAIN ACTIVITY ---

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

// --- 10. ПОЛНЫЙ СПИСОК КНИГ (Источник данных) ---

fun getSampleBooks(): List<Book> {
    return buildList {
        add(
            Book(
                1L,
                "Мастер и Маргарита",
                "М. Булгаков",
                "Роман",
                4.9f,
                R.drawable.master_and_margaret,
                true
            )
        )
        add(Book(2L, "1984", "Дж. Оруэлл", "Антиутопия", 4.8f, R.drawable._984, true))
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
                true
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
}