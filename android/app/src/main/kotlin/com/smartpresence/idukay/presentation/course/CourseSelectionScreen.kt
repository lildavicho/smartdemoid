package com.smartpresence.idukay.presentation.course

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSelectionScreen(
    onCourseSelected: (String) -> Unit
) {
    // Hardcoded courses for now - will be fetched from API later
    val courses = listOf(
        Course("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", "Matemáticas 10A", "MAT-10A", "2024-1"),
        Course("b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e", "Física 11B", "FIS-11B", "2024-1"),
        Course("c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f", "Química 9C", "QUI-9C", "2024-1")
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seleccionar Curso") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(courses) { course ->
                CourseCard(
                    course = course,
                    onClick = { onCourseSelected(course.id) }
                )
            }
        }
    }
}

@Composable
fun CourseCard(
    course: Course,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${course.code} • ${course.academicPeriod}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Seleccionar",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

data class Course(
    val id: String,
    val name: String,
    val code: String,
    val academicPeriod: String
)
