/*
 * Copyright (c) 2011-2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.here.android.example.guidance

import android.app.AlertDialog
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.here.android.mpa.common.GeoBoundingBox
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.common.GeoPosition
import com.here.android.mpa.common.Image
import com.here.android.mpa.common.OnEngineInitListener
import com.here.android.mpa.guidance.NavigationManager
import com.here.android.mpa.mapping.AndroidXMapFragment
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.mapping.MapMarker
import com.here.android.mpa.mapping.MapOverlayType
import com.here.android.mpa.mapping.MapRoute
import com.here.android.mpa.routing.CoreRouter
import com.here.android.mpa.routing.Route
import com.here.android.mpa.routing.RouteOptions
import com.here.android.mpa.routing.RoutePlan
import com.here.android.mpa.routing.RouteResult
import com.here.android.mpa.routing.RouteWaypoint
import com.here.android.mpa.routing.Router
import com.here.android.mpa.routing.RoutingError
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.pow

/**
 * This class encapsulates the properties and functionality of the Map view.It also triggers a
 * turn-by-turn navigation from HERE Burnaby office to Langley BC.There is a sample voice skin
 * bundled within the SDK package to be used out-of-box, please refer to the Developer's guide for
 * the usage.
 */
class MapFragmentView(private val m_activity: MainActivity) {
    private var m_mapFragment: AndroidXMapFragment? = null
    private var m_naviControlButton: FloatingActionButton? = null
    private var m_map: Map? = null
    private var m_navigationManager: NavigationManager? = null
    private var m_geoBoundingBox: GeoBoundingBox? = null
    private var m_route: Route? = null
    private var m_foregroundServiceStarted: Boolean = false

    private val mapFragment: AndroidXMapFragment
        get() = m_activity.supportFragmentManager.findFragmentById(R.id.mapfragment) as AndroidXMapFragment

    lateinit var tts: TextToSpeech

    init {
        initMapFragment()
        initNaviControlButton()

        tts = TextToSpeech(m_activity, TextToSpeech.OnInitListener { tts.language = Locale.CANADA })
    }

    internal fun initMapFragment() {
        /* Locate the mapFragment UI element */
        m_mapFragment = mapFragment

        if (m_mapFragment != null) {
            /* Initialize the AndroidXMapFragment, results will be given via the called back. */
            m_mapFragment!!.init { error ->

                if (error == OnEngineInitListener.Error.NONE) {
                    m_map = m_mapFragment!!.map
                    m_map!!.setCenter(
                        GeoCoordinate(49.243179, -123.118263),
                        Map.Animation.NONE
                    )
                    //Put this call in Map.onTransformListener if the animation(Linear/Bow)
                    //is used in setCenter()
                    m_map!!.zoomLevel = 12.2
                    /*
                     * Get the NavigationManager instance.It is responsible for providing voice
                     * and visual instructions while driving and walking
                     */
                    m_navigationManager = NavigationManager.getInstance()
                    m_navigationManager?.audioPlayer?.volume = 0f
                    setupDangerousMarkers()
                } else {
                    AlertDialog.Builder(m_activity).setMessage(
                        "Error : " + error.name + "\n\n" + error.details
                    )
                        .setTitle(R.string.engine_init_error)
                        .setNegativeButton(
                            android.R.string.cancel
                        ) { dialog, which -> m_activity.finish() }.create().show()
                }
            }
        }
    }

    var isSetupYet = false

    fun setupDangerousMarkers() {
        if (!isSetupYet && m_activity.intersections.size > 0) {
            // top 10 most dangerous intersections
            val x = m_activity.intersections.sortedBy{ it.dangerRatio }.takeLast(30)
            x.forEach {
                val image = Image()
                image.setImageResource(R.drawable.ic_warning3)

                val marker = MapMarker(it.geoCoordinate, image)
                marker.isDeclutteringEnabled = true
                marker.overlayType = MapOverlayType.FOREGROUND_OVERLAY
                m_map?.addMapObject(marker)
            }
        }
    }

    fun GeoCoordinate.closeIntersections(): List<Intersection> = m_activity.intersections.filter {
        it.geoCoordinate.distanceTo(this) < 150
    }

    fun Route.dangerLevel(): Double {
        Log.d("DALU", "Calculating Danger Level...")

        val geoCoordinates = routeElements.elements.flatMap { it.geometry }
//        val geoCoordinates = maneuvers.map { it.coordinate!! }

        return geoCoordinates.sumByDouble {
            it.closeIntersections().sumByDouble { intersection ->
                intersection.dangerRatio.pow(100.0)
            } / geoCoordinates.count()
        }
    }

    var mapRoute: MapRoute? = null

    fun displayRoute(route: Route) {
        /* Create a MapRoute so that it can be placed on the map */
        mapRoute = MapRoute(route)
        mapRoute!!.overlayType = MapOverlayType.BACKGROUND_OVERLAY
//        mapRoute.color = m_activity.resources.getColor(R.color.colorAccent)

        /* Add the MapRoute to the map */
        m_map!!.addMapObject(mapRoute!!)

        /*
         * We may also want to make sure the map view is orientated properly
         * so the entire route can be easily seen.
         */
        m_geoBoundingBox = route.boundingBox?.apply {
            val diffLatitude = topLeft.latitude - bottomRight.latitude
            val diffLongitude = bottomRight.longitude - topLeft.longitude

            topLeft = GeoCoordinate(
                topLeft.latitude + diffLatitude / 2, topLeft.longitude - diffLongitude / 5
            )

            bottomRight = GeoCoordinate(
                bottomRight.latitude - diffLatitude / 5, bottomRight.longitude + diffLongitude / 5
            )
        }


        m_map!!.zoomTo(
            m_geoBoundingBox!!, Map.Animation.BOW,
            0f
        )
    }

    fun createRoute(i1: Intersection, i2: Intersection, type: RouteOptions.Type) {
        if (mapRoute != null) {
            m_map!!.removeMapObject(mapRoute!!)
        }

        /* Initialize a CoreRouter */
        val coreRouter = CoreRouter()

        /* Initialize a RoutePlan */
        val routePlan = RoutePlan()

        /*
         * Initialize a RouteOption. HERE Mobile SDK allow users to define their own parameters for the
         * route calculation,including transport modes,route types and route restrictions etc.Please
         * refer to API doc for full list of APIs
         */
        val routeOptions = RouteOptions()
        /* Other transport modes are also available e.g Pedestrian */
        routeOptions.transportMode = RouteOptions.TransportMode.CAR
        /* Disable highway in this route. */
        routeOptions.setHighwaysAllowed(false)
        /* Calculate the shortest route available. */
        routeOptions.routeType = type
        /* Calculate 1 route. */
        routeOptions.routeCount = if (type == RouteOptions.Type.BALANCED) 5 else 1
        /* Finally set the route option */
        routePlan.routeOptions = routeOptions

        /* Define waypoints for the route */
        //        /* START: 4350 Still Creek Dr */
        //        RouteWaypoint startPoint = new RouteWaypoint(new GeoCoordinate(49.259149, -123.008555));
        //        /* END: Langley BC */
        //        RouteWaypoint destination = new RouteWaypoint(new GeoCoordinate(49.073640, -122.559549));

//        /* START: Icon 330 */
//        val startPoint = RouteWaypoint(GeoCoordinate(43.4625375, -80.5407876))
//        /* END: tnt */
//        val destination = RouteWaypoint(GeoCoordinate(43.4764874, -80.5411609))

        /* START: Icon 330 */
        val startPoint = RouteWaypoint(i1.geoCoordinate)
        /* END: tnt */
        val destination = RouteWaypoint(i2.geoCoordinate)

        /* Add both waypoints to the route plan */
        routePlan.addWaypoint(startPoint)
        routePlan.addWaypoint(destination)

        /* Trigger the route calculation,results will be called back via the listener */
        coreRouter.calculateRoute(routePlan,
                                  object : Router.Listener<List<RouteResult>, RoutingError> {
                                      override fun onProgress(i: Int) {
                                          /* The calculation progress can be retrieved in this callback. */
                                          //                        Log.d("DALU", "onProgress: " + i);
                                          //                        if (i > 50) {
                                          //                            tts.speak("Hello Sam", TextToSpeech.QUEUE_FLUSH, null);
                                          //                        }
                                      }

                                      override fun onCalculateRouteFinished(routeResults: List<RouteResult>?,
                                                                            routingError: RoutingError) {
                                          /* Calculation is done.Let's handle the result */
                                          if (routingError == RoutingError.NONE) {
                                              when {
                                                  routeResults!!.size == 1 -> {
                                                      m_route = routeResults[0].route!!
                                                      displayRoute(m_route!!)
                                                  }
                                                  routeResults!!.size > 1 -> { // CALCULATE SAFEST
                                                      val safestRoute = routeResults!!.map {
                                                          it.route
                                                      }.minBy {
                                                          it.dangerLevel()
                                                      }

                                                      m_route = safestRoute
                                                      displayRoute(m_route!!)
                                                  }
                                                  else -> Toast.makeText(
                                                      m_activity,
                                                      "Error:route results returned is not valid",
                                                      Toast.LENGTH_LONG
                                                  ).show()
                                              }
                                          } else {
                                              Toast.makeText(
                                                  m_activity,
                                                  "Error:route calculation returned error code: $routingError",
                                                  Toast.LENGTH_LONG
                                              ).show()

                                          }
                                      }
                                  })
    }

    internal fun initNaviControlButton() {
        m_naviControlButton = m_activity.findViewById<View>(R.id.fab) as FloatingActionButton
        m_naviControlButton!!.setOnClickListener {
            if (m_route == null) {
//                createRoute()
            } else {
                m_navigationManager!!.stop()
                /*
                 * Restore the map orientation to show entire route on screen
                 */
                m_map!!.zoomTo(m_geoBoundingBox!!, Map.Animation.NONE, 0f)
                m_route = null
            }
        }
    }

    /*
     * Android 8.0 (API level 26) limits how frequently background apps can retrieve the user's
     * current location. Apps can receive location updates only a few times each hour.
     * See href="https://developer.android.com/about/versions/oreo/background-location-limits.html
     * In order to retrieve location updates more frequently start a foreground service.
     * See https://developer.android.com/guide/components/services.html#Foreground
     */
    private fun startForegroundService() {
        if (!m_foregroundServiceStarted) {
            m_foregroundServiceStarted = true
            val startIntent = Intent(m_activity, ForegroundService::class.java)
            startIntent.action = ForegroundService.START_ACTION
            m_activity.applicationContext.startService(startIntent)
        }
    }

    private fun stopForegroundService() {
        if (m_foregroundServiceStarted) {
            m_foregroundServiceStarted = false
            val stopIntent = Intent(m_activity, ForegroundService::class.java)
            stopIntent.action = ForegroundService.STOP_ACTION
            m_activity.applicationContext.startService(stopIntent)
        }
    }

    fun startNavigation() {
        //        m_naviControlButton.setText(R.string.stop_navi);
        /* Configure Navigation manager to launch navigation on current map */
        m_navigationManager!!.setMap(m_map)

        /*
         * Start the turn-by-turn navigation.Please note if the transport mode of the passed-in
         * route is pedestrian, the NavigationManager automatically triggers the guidance which is
         * suitable for walking. Simulation and tracking modes can also be launched at this moment
         * by calling either simulate() or startTracking()
         */

        /* Choose navigation modes between real time navigation and simulation */
        val alertDialogBuilder = AlertDialog.Builder(m_activity)
        alertDialogBuilder.setTitle("Navigation")
        alertDialogBuilder.setMessage("Choose Mode")
        alertDialogBuilder.setNegativeButton("Navigation") { dialoginterface, i ->
//            m_navigationManager!!.startNavigation(m_route!!)
//            m_map!!.tilt = 60f
//
//
//            intersectionsAlreadyNotified.clear()
//            hasttsEnd = false
//
//            mapFragment.positionIndicator?.apply {
//                isVisible = true
////                marker.apply {
////                    val paint = Paint()
////                    paint.colorFilter = PorterDuffColorFilter(
////                        m_activity.resources.getColor(R.color.black),
////                        PorterDuff.Mode.SRC_IN)
////
////                    val bitmapResult = Bitmap.createBitmap(bitmap!!.width, bitmap!!.height, Bitmap.Config.ARGB_8888);
////                    val canvas = Canvas(bitmapResult)
////                    canvas.drawBitmap(bitmap!!, 0f, 0f, paint)
////
////                    setBitmap(bitmapResult)
////                }
//            }
//
//            startForegroundService()
        }
        alertDialogBuilder.setPositiveButton("Simulation") { dialoginterface, i ->
            m_navigationManager!!.simulate(m_route!!, 60)//Simualtion speed is set to 60 m/s
            m_map!!.tilt = 60f
            startForegroundService()
        }
        val alertDialog = alertDialogBuilder.create()
//        alertDialog.show()
        /*
         * Set the map update mode to ROADVIEW.This will enable the automatic map movement based on
         * the current location.If user gestures are expected during the navigation, it's
         * recommended to set the map update mode to NONE first. Other supported update mode can be
         * found in HERE Mobile SDK for Android (Premium) API doc
         */
//        m_navigationManager!!.mapUpdateMode = NavigationManager.MapUpdateMode.ROADVIEW
        m_navigationManager!!.mapUpdateMode = NavigationManager.MapUpdateMode.POSITION_ANIMATION

        /*
         * NavigationManager contains a number of listeners which we can use to monitor the
         * navigation status and getting relevant instructions.In this example, we will add 2
         * listeners for demo purpose,please refer to HERE Android SDK API documentation for details
         */
        addNavigationListeners()


        // Copied from Alert Dialog. Start Navigation!
        m_navigationManager!!.startNavigation(m_route!!)
        m_map!!.setTilt(60f, Map.Animation.BOW)
//        m_map!!.setCenter(m_route!!.waypoints!![0], Map.Animation.BOW)
//        m_map!!.setZoomLevel(17.0, Map.Animation.BOW)

        intersectionsAlreadyNotified.clear()
        hasttsEnd = false

        mapFragment.positionIndicator?.apply {
            isVisible = true
//                marker.apply {
//                    val paint = Paint()
//                    paint.colorFilter = PorterDuffColorFilter(
//                        m_activity.resources.getColor(R.color.black),
//                        PorterDuff.Mode.SRC_IN)
//
//                    val bitmapResult = Bitmap.createBitmap(bitmap!!.width, bitmap!!.height, Bitmap.Config.ARGB_8888);
//                    val canvas = Canvas(bitmapResult)
//                    canvas.drawBitmap(bitmap!!, 0f, 0f, paint)
//
//                    setBitmap(bitmapResult)
//                }
        }

        startForegroundService()
    }

    val intersectionsAlreadyNotified = mutableListOf<Intersection>()
    var hasttsEnd = false

    private val m_positionListener = object : NavigationManager.PositionListener() {

        override fun onPositionUpdated(geoPosition: GeoPosition) {
            /*
            Let the user follow a route
            Warn them when they are
            > On a path that contains a dangerous intersection
            > Are within a certain distance of that intersection
            > Are currently driving towards it (the next step)

            Warns with TTS
             */

//            m_map!!.setCenter(geoPosition.coordinate, Map.Animation.LINEAR)
//            m_map!!.setZoomLevel(17.0, Map.Animation.LINEAR)
//            m_map!!.setOrientation(geoPosition.heading.toFloat(), Map.Animation.LINEAR)

            // Check Dangerous Intersections
            val approachingDangerousIntersection= geoPosition.coordinate.closeIntersections().filter {
                it.dangerRatio > 0.03
            }

            approachingDangerousIntersection.forEach {
                if (!intersectionsAlreadyNotified.contains(it)) {
                    intersectionsAlreadyNotified.add(it)
                    tts.speak(
                        "Dangerous Intersection Approaching: " + it.name,
                        TextToSpeech.QUEUE_ADD, null)
                }
            }

            // Check Ending
            val end = m_route!!.maneuvers.get(m_route!!.maneuvers.size - 1)
            if (end.coordinate!!.distanceTo(geoPosition.coordinate) < 100 && !hasttsEnd) {
                hasttsEnd = true
//                tts.speak(
//                    "Arriving at Destination",
//                    TextToSpeech.QUEUE_ADD, null)
            }

            Log.d("DALU_DANGEROUS", approachingDangerousIntersection.toString())
        }
    }

    private val m_navigationManagerEventListener = object : NavigationManager.NavigationManagerEventListener() {
        override fun onRunningStateChanged() {
            Log.d("HERE.com Debug", "Running state changed")
        }

        override fun onNavigationModeChanged() {
            Log.d("HERE.com Debug", "Navigation mode changed")
        }

        override fun onEnded(navigationMode: NavigationManager.NavigationMode?) {
            Log.d("HERE.com Debug", navigationMode!!.toString() + " was ended")
            stopForegroundService()
        }

        override fun onMapUpdateModeChanged(mapUpdateMode: NavigationManager.MapUpdateMode?) {
            Log.d("HERE.com Debug", "Map update mode is changed to " + mapUpdateMode!!)
        }

        override fun onRouteUpdated(route: Route) {
            Log.d("HERE.com Debug", "Route updated")
        }

        override fun onCountryInfo(s: String, s1: String) {
            Log.d("HERE.com Debug", "Country info updated from $s to $s1")
        }
    }

    private fun addNavigationListeners() {

        /*
         * Register a NavigationManagerEventListener to monitor the status change on
         * NavigationManager
         */
        m_navigationManager!!.addNavigationManagerEventListener(
            WeakReference(
                m_navigationManagerEventListener
            )
        )

        /* Register a PositionListener to monitor the position updates */
        m_navigationManager!!.addPositionListener(
            WeakReference(m_positionListener)
        )
    }

    fun onDestroy() {
        /* Stop the navigation when app is destroyed */
        if (m_navigationManager != null) {
            stopForegroundService()
            m_navigationManager!!.stop()
        }
    }
}
