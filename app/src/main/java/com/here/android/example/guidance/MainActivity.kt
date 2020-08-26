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

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.here.android.mpa.routing.RouteOptions
import kotlinx.android.synthetic.main.activity_main.fromtext
import kotlinx.android.synthetic.main.activity_main.fromtext_layout
import kotlinx.android.synthetic.main.activity_main.go_button
import kotlinx.android.synthetic.main.activity_main.icon2
import kotlinx.android.synthetic.main.activity_main.icon3
import kotlinx.android.synthetic.main.activity_main.radio_group
import kotlinx.android.synthetic.main.activity_main.radio_safest
import kotlinx.android.synthetic.main.activity_main.radio_shortest
import kotlinx.android.synthetic.main.activity_main.toolbar_layout
import kotlinx.android.synthetic.main.activity_main.totext
import kotlinx.coroutines.launch

/**
 * Main activity which launches map view and handles Android run-time requesting permission.
 */

class MainActivity : AppCompatActivity() {

    private var m_mapFragmentView: MapFragmentView? = null

    private var hasExpandedEver = false
    private var isExpanded = false

    val expandHeight = 300
    val collapseHeight = 150

    var intersections: MutableList<Intersection> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Acquire Permissions ---
        if (hasPermissions(this, *RUNTIME_PERMISSIONS)) {
            setupMapFragmentView() // Create the map!
        } else {
            ActivityCompat
                .requestPermissions(this, RUNTIME_PERMISSIONS, REQUEST_CODE_ASK_PERMISSIONS)
        }

        // --- Call Database ---
        VolleySingleton.getInstance(this).getIntersections(this)

        // --- Setup Auto Complete ---
        lifecycleScope.launch {
            val intersectionNames = VolleySingleton.getInstance(this@MainActivity).getIntersectionNames()

            val intersectionAdapter = ArrayAdapter<String>(
                this@MainActivity,
                android.R.layout.simple_dropdown_item_1line,
                intersectionNames
            )

            fromtext.setAdapter(intersectionAdapter)
            fromtext.threshold = 2

            totext.setAdapter(intersectionAdapter)
            totext.threshold = 2
        }

        // --- Setup Radio Group ---
        radio_group.setOnCheckedChangeListener { _, _ -> updateMap() }

        // --- Setup Animations ---
        val showViews = listOf<View>(
            totext,
            go_button,
            icon2,
            icon3,
            radio_group
        )

        // Don't hide the toText, or icons
        val hideViews = listOf<View>(
            go_button,
            radio_group
        )

        val editTexts = listOf<AutoCompleteTextView>(
            fromtext,
            totext
        )

        editTexts.forEach {
            it.setOnFocusChangeListener { _, hasFocus -> if (hasFocus && !isExpanded) {
                fromtext_layout.hint = "Search"
                fromtext.postDelayed({ fromtext.hint = "From" }, 300)

                val animation = AnimatorSet()

                // Toolbar increase size
                val toolbarFrom = toolbar_layout.layoutParams.height
                val toolbarTo = toolbarFrom + if (hasExpandedEver) collapseHeight else expandHeight

                val valueAnimator = ValueAnimator.ofInt(toolbarFrom, toolbarTo)
                valueAnimator.addUpdateListener {
                    val params = toolbar_layout.layoutParams
                    params.height = it.animatedValue as Int
                    toolbar_layout.layoutParams = params
                }
                animation.play(valueAnimator)

                (if (hasExpandedEver) hideViews else showViews).forEach {
                    it.alpha = 0f
                    it.visibility = View.VISIBLE

                    val showView = ObjectAnimator.ofFloat(it, "alpha", 0f, 1f)
                    showView.startDelay = 300
                    animation.play(showView)
                }

                animation.start()
                hasExpandedEver = true
                isExpanded = true
            } else if (!hasFocus) {
                if (fromtext.text.isBlank()) {
                    fromtext_layout.hint = ""
                    fromtext.hint = "Search"
                }
                updateMap()
            }}
        }

        editTexts.forEach {
            it.doOnTextChanged { text, start, before, count ->
                if (count > 10) {
                    updateMap()
                }
            }
        }

        go_button.setOnClickListener {
            go_button.startAnimation()

            // Clear focus from others
            editTexts.forEach {
                it.clearFocus()
            }

            go_button.postDelayed(Runnable {
                // Hide keyboard
                val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(go_button.windowToken, 0)

                val animation = AnimatorSet()
                // Toolbar increase size

                val toolbarFrom = toolbar_layout.layoutParams.height
                val toolbarTo = toolbarFrom - collapseHeight

                val valueAnimator = ValueAnimator.ofInt(toolbarFrom, toolbarTo)
                valueAnimator.addUpdateListener {
                    val params = toolbar_layout.layoutParams
                    params.height = it.animatedValue as Int
                    toolbar_layout.layoutParams = params
                }
                valueAnimator.startDelay = 300

                animation.play(valueAnimator)

                hideViews.forEach {
                    it.alpha = 1f
                    it.visibility = View.VISIBLE

                    val hideView = ObjectAnimator.ofFloat(it, "alpha", 1f, 0f)
                    animation.play(hideView)
                }

                animation.doOnEnd {
                    hideViews.forEach {
                        it.visibility = View.GONE
                    }
                    go_button.revertAnimation()

                    m_mapFragmentView?.startNavigation() // ACTUALLY START THE NAVIGATION
                }

                animation.startDelay = 100
                animation.start()
                isExpanded = false

             }, 1000)
        }
    }

    fun dataReceived() {
        m_mapFragmentView?.setupDangerousMarkers()
    }

    fun updateMap() {
        val i1: Intersection? = intersections.filter { it.name.toLowerCase() == fromtext.text.toString().toLowerCase() }.getOrNull(0)
        val i2: Intersection? = intersections.filter { it.name.toLowerCase() == totext.text.toString().toLowerCase() }.getOrNull(0)
        if (i1 != null && i2 != null) {
            m_mapFragmentView?.createRoute(i1, i2, getRouteType())
        }
    }

    fun getRouteType(): RouteOptions.Type {
        if (radio_shortest.isChecked) return RouteOptions.Type.SHORTEST
        if (radio_safest.isChecked) return RouteOptions.Type.BALANCED // BALANCED == SAFEST
        return RouteOptions.Type.FASTEST
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE_ASK_PERMISSIONS -> {
                for (index in permissions.indices) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {

                        /*
                         * If the user turned down the permission request in the past and chose the
                         * Don't ask again option in the permission request system dialog.
                         */
                        if (!ActivityCompat
                                .shouldShowRequestPermissionRationale(this, permissions[index])
                        ) {
                            Toast.makeText(
                                this, "Required permission " + permissions[index]
                                + " not granted. "
                                + "Please go to settings and turn on for sample app",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this, "Required permission " + permissions[index]
                                + " not granted", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                setupMapFragmentView()
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun setupMapFragmentView() {
        // All permission requests are being handled. Create map fragment view. Please note
        // the HERE Mobile SDK requires all permissions defined above to operate properly.
        m_mapFragmentView = MapFragmentView(this)
    }

    public override fun onDestroy() {
        m_mapFragmentView!!.onDestroy()
        super.onDestroy()
    }

    companion object {

        private val REQUEST_CODE_ASK_PERMISSIONS = 1
        private val RUNTIME_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_NETWORK_STATE
        )

        /**
         * Only when the app's target SDK is 23 or higher, it requests each dangerous permissions it
         * needs when the app is running.
         */
        private fun hasPermissions(context: Context, vararg permissions: String): Boolean {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && permissions != null) {
                for (permission in permissions) {
                    if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                        return false
                    }
                }
            }
            return true
        }
    }
}
