package com.antoninofaro.welcometool.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.antoninofaro.welcometool.R
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val libs = remember {
        val json = context.resources.openRawResource(R.raw.aboutlibraries)
            .bufferedReader().use { it.readText() }
        Libs.Builder().withJson(json).build()
    }

    var query by remember { mutableStateOf("") }
    var selectedLicenses by remember { mutableStateOf(setOf<String>()) }
    val licenseNames = remember(libs) { libs.licenses.map { it.name }.sorted() }

    val filteredLibs = remember(libs, query, selectedLicenses) {
        libs.copy(libraries = libs.libraries.filter { lib ->
            (query.isEmpty() || lib.name.contains(query, ignoreCase = true)) &&
            (selectedLicenses.isEmpty() || lib.licenses.any { it.name in selectedLicenses })
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_libraries_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            LazyRow(modifier = Modifier.padding(horizontal = 12.dp)) {
                items(licenseNames) { license ->
                    FilterChip(
                        selected = license in selectedLicenses,
                        onClick = {
                            selectedLicenses = if (license in selectedLicenses)
                                selectedLicenses - license
                            else
                                selectedLicenses + license
                        },
                        label = { Text(license, maxLines = 1) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            LibrariesContainer(
                filteredLibs,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}
