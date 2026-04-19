package kz.tou.booktracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kz.tou.booktracker.ui.theme.BookTrackerAppTheme

// --- Data model ---
data class Book(
    val title: String,
    val author: String,
    val rating: Double
)

// --- Sample data (русская классика) ---
val sampleBooks = listOf(
    Book("Война и мир", "Лев Толстой", 4.9),
    Book("Анна Каренина", "Лев Толстой", 4.8),
    Book("Преступление и наказание", "Фёдор Достоевский", 4.9),
    Book("Идиот", "Фёдор Достоевский", 4.7),
    Book("Братья Карамазовы", "Фёдор Достоевский", 5.0),
    Book("Мёртвые души", "Николай Гоголь", 4.6),
    Book("Евгений Онегин", "Александр Пушкин", 4.8),
    Book("Герой нашего времени", "Михаил Лермонтов", 4.7)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookTrackerAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BookList(
                        books = sampleBooks,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// --- List screen ---
@Composable
fun BookList(books: List<Book>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(books) { book ->
            BookListItem(
                title = book.title,
                author = book.author,
                rating = book.rating,
                onBookmark = {}
            )
        }
    }
}

// --- Item ---
@Composable
fun BookListItem(
    title: String,
    author: String,
    rating: Double,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "★ ${"%.1f".format(rating)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )

            IconButton(onClick = onBookmark) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkBorder,
                    contentDescription = "В избранное"
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BookListPreview() {
    BookTrackerAppTheme {
        BookList(sampleBooks)
    }
}