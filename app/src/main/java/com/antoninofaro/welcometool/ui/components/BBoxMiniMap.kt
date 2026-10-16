package com.antoninofaro.welcometool.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/bright"

@Suppress("DEPRECATION")
@Composable
fun BBoxMiniMap(
    minLon: Double?,
    minLat: Double?,
    maxLon: Double?,
    maxLat: Double?,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clipToBounds()
        ) {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current

            if (minLon != null && minLat != null && maxLon != null && maxLat != null) {
                val mapView = remember {
                    val options = MapLibreMapOptions.createFromAttributes(context)
                        .textureMode(true)
                    MapView(context, options).apply {
                        onCreate(null)
                        onStart()
                        onResume()
                    }
                }

                DisposableEffect(lifecycleOwner, mapView) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> mapView.onStart()
                            Lifecycle.Event.ON_RESUME -> mapView.onResume()
                            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                            Lifecycle.Event.ON_STOP -> mapView.onStop()
                            Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        mapView.onPause()
                        mapView.onStop()
                        mapView.onDestroy()
                    }
                }

                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.getMapAsync { map ->
                            val updateContent = { _: Style ->
                                map.uiSettings.isAttributionEnabled = true
                                map.uiSettings.isLogoEnabled = true
                                map.uiSettings.setAllGesturesEnabled(false)

                                // Set max zoom preference cap so single-node or tiny bboxes retain context
                                map.setMaxZoomPreference(16.0)

                                val latSpan = maxLat - minLat
                                val lonSpan = maxLon - minLon
                                val minSpan = 0.015 // ~1.5 km minimum span for context

                                val effectiveMinLat =
                                    if (latSpan < minSpan) minLat - minSpan / 2 else minLat
                                val effectiveMaxLat =
                                    if (latSpan < minSpan) maxLat + minSpan / 2 else maxLat
                                val effectiveMinLon =
                                    if (lonSpan < minSpan) minLon - minSpan / 2 else minLon
                                val effectiveMaxLon =
                                    if (lonSpan < minSpan) maxLon + minSpan / 2 else maxLon

                                val bounds = LatLngBounds.Builder()
                                    .include(LatLng(effectiveMinLat, effectiveMinLon))
                                    .include(LatLng(effectiveMaxLat, effectiveMaxLon))
                                    .build()

                                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 60))

                                map.clear()
                                val polyline = PolylineOptions()
                                    .add(LatLng(effectiveMinLat, effectiveMinLon))
                                    .add(LatLng(effectiveMinLat, effectiveMaxLon))
                                    .add(LatLng(effectiveMaxLat, effectiveMaxLon))
                                    .add(LatLng(effectiveMaxLat, effectiveMinLon))
                                    .add(LatLng(effectiveMinLat, effectiveMinLon))
                                    .color(android.graphics.Color.argb(0xFF, 0xFF, 0x77, 0x00))
                                    .width(1f * view.context.resources.displayMetrics.density)

                                map.addPolyline(polyline)
                            }

                            val currentStyle = map.style
                            if (currentStyle != null && currentStyle.isFullyLoaded) {
                                updateContent(currentStyle)
                            } else {
                                val styleBuilder = Style.Builder().fromUri(OPENFREEMAP_STYLE_URL)
                                map.setStyle(styleBuilder) { style ->
                                    updateContent(style)
                                }
                            }
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Map preview not available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (label != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BBoxMiniMap(
    bboxString: String?,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val parts = bboxString?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }
    val minLon = parts?.getOrNull(0)
    val minLat = parts?.getOrNull(1)
    val maxLon = parts?.getOrNull(2)
    val maxLat = parts?.getOrNull(3)

    BBoxMiniMap(
        minLon = minLon,
        minLat = minLat,
        maxLon = maxLon,
        maxLat = maxLat,
        modifier = modifier,
        label = label
    )
}
