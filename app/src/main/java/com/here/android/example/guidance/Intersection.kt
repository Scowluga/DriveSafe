package com.here.android.example.guidance

import com.here.android.mpa.common.GeoCoordinate

data class Intersection(val name: String, val geoCoordinate: GeoCoordinate, val dangerRatio: Double)