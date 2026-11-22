package com.example.reallifetag.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.example.reallifetag.model.LatLng
import com.example.reallifetag.model.Player
import com.example.reallifetag.model.Role
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCircle
import platform.MapKit.MKCircleRenderer
import platform.MapKit.MKMapCamera
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UIColor
import platform.darwin.NSObject

class IosMapDelegate : NSObject(), MKMapViewDelegateProtocol {
    override fun mapView(mapView: MKMapView, rendererForOverlay: MKOverlayProtocol): MKOverlayRenderer {
        if (rendererForOverlay is MKCircle) {
            val renderer = MKCircleRenderer(rendererForOverlay)
            renderer.strokeColor = UIColor.redColor
            renderer.fillColor = UIColor.redColor.colorWithAlphaComponent(0.2)
            renderer.lineWidth = 3.0
            return renderer
        }
        return MKOverlayRenderer(rendererForOverlay)
    }

    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
        val annotation = viewForAnnotation as? MKPointAnnotation ?: return null
        val identifier = "PlayerMarker"
        val view = (mapView.dequeueReusableAnnotationViewWithIdentifier(identifier) as? MKMarkerAnnotationView)
            ?: MKMarkerAnnotationView(annotation, reuseIdentifier = identifier)

        view.annotation = annotation
        val title = annotation.title ?: ""
        if (title.contains("(You)")) {
            view.markerTintColor = UIColor(red = 0.16, green = 0.71, blue = 0.96, alpha = 1.0)
        } else if (title.contains("Teammate")) {
            view.markerTintColor = UIColor(red = 0.0, green = 0.9, blue = 0.46, alpha = 1.0)
        } else {
            view.markerTintColor = UIColor(red = 1.0, green = 0.32, blue = 0.32, alpha = 1.0)
        }
        return view
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun GameMap(
    modifier: Modifier,
    zoneCenter: LatLng,
    zoneRadiusMeters: Float,
    cameraTarget: LatLng,
    targetZoom: Float,
    targetBearing: Float,
    isMapLockedToHeading: Boolean,
    players: List<Player>,
    myDeviceId: String,
    myRole: Role,
    isHeadStart: Boolean,
    pingAlpha: Float
) {
    val delegate = remember { IosMapDelegate() }
    val isMyRoleRunner = myRole == Role.RUNNER

    UIKitView(
        modifier = modifier,
        factory = {
            MKMapView().apply {
                this.delegate = delegate
                zoomEnabled = false
                scrollEnabled = false
                rotateEnabled = false
                pitchEnabled = false
                showsUserLocation = false
            }
        },
        update = { mapView ->
            // Update Camera Position & Orientation
            val centerCoord = CLLocationCoordinate2DMake(cameraTarget.latitude, cameraTarget.longitude)
            val distance = (zoneRadiusMeters * 4.0).coerceAtLeast(500.0)
            val heading = if (isMapLockedToHeading) targetBearing.toDouble() else 0.0
            val camera = MKMapCamera.cameraLookingAtCenterCoordinate(
                centerCoordinate = centerCoord,
                fromDistance = distance,
                pitch = 0.0,
                heading = heading
            )
            mapView.setCamera(camera, animated = false)

            // Update Zone Circle Overlay
            mapView.removeOverlays(mapView.overlays)
            val zoneCoord = CLLocationCoordinate2DMake(zoneCenter.latitude, zoneCenter.longitude)
            val circle = MKCircle.circleWithCenterCoordinate(zoneCoord, zoneRadiusMeters.toDouble())
            mapView.addOverlay(circle)

            // Update Player Annotations
            mapView.removeAnnotations(mapView.annotations)
            players.forEach { p ->
                if (!p.isTagged) {
                    val isYou = (p.id == myDeviceId || p.id == "6")
                    val isTeammate = (p.role == myRole)
                    val showMarker = when {
                        isYou -> true
                        !isHeadStart -> true
                        isTeammate -> true
                        isMyRoleRunner && p.role == Role.HUNTER -> true
                        else -> false
                    }

                    if (showMarker) {
                        val pos = if (isYou || p.role == Role.HUNTER || p.isLiveExposed) p.latLng else (p.lastPingLocation ?: p.latLng)
                        val annotation = MKPointAnnotation().apply {
                            setCoordinate(CLLocationCoordinate2DMake(pos.latitude, pos.longitude))
                            setTitle(if (isYou) "${p.name} (You)" else "${p.name} (${if (isTeammate) "Teammate" else "Enemy"})")
                            setSubtitle(if (isYou) "Your Live Location" else if (isTeammate) "Teammate Location" else "Last Known Location")
                        }
                        mapView.addAnnotation(annotation)
                    }
                }
            }
        }
    )
}
