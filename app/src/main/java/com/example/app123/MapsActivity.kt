package com.example.app123

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle


import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.app123.databinding.ActivityMapsBinding
import com.example.app123.models.MyLatLng
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.Marker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, MenuProvider {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var databaseRef: DatabaseReference
    private lateinit var locationCallback: LocationCallback

    private lateinit var childEventListener: ChildEventListener

    val markersMap = mutableMapOf<String, Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        setupLocClient()

        listeners()

        getCurrentLocation()

        setSupportActionBar(binding.toolbar)

        val menuHost: MenuHost = this
        menuHost.addMenuProvider(this, this, Lifecycle.State.RESUMED)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        databaseRef = FirebaseDatabase.getInstance().getReference("locations")

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult?.lastLocation?.let { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    databaseRef.child(FirebaseAuth.getInstance().currentUser?.uid ?: "")
                        .setValue(MyLatLng(latLng.latitude, latLng.longitude,
                            FirebaseAuth.getInstance().currentUser?.email!!
                        ))
                }
            }
        }
        childEventListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Get the location from the snapshot
                val latLng = snapshot.getValue(MyLatLng::class.java)?.let {
                    LatLng(it.latitude, it.longitude)
                }
                val userId = snapshot.key
                if (markersMap.containsKey(userId)) {
                    markersMap[userId]?.position = latLng!!
                } else {
                    // Создаем новый маркер для данного пользователя
                    if(userId == FirebaseAuth.getInstance().currentUser?.uid) {
                        val marker = mMap.addMarker(MarkerOptions().position(latLng!!).title("ТЫ"))
                        markersMap[userId!!] = marker!!

                    }
                    else{
                        val marker = mMap.addMarker(MarkerOptions().position(latLng!!).title(userId))
                        markersMap[userId!!] = marker!!
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Get the location from the snapshot
                // Move the marker to the new location
                val latLng = snapshot.getValue(MyLatLng::class.java)?.let {
                    LatLng(it.latitude, it.longitude)
                }

                val userId = snapshot.key
                if (markersMap.containsKey(userId)) {
                    markersMap[userId]?.position = latLng!!
                } else {
                    // Создаем новый маркер для данного пользователя
                    if(userId == FirebaseAuth.getInstance().currentUser?.uid) {
                        val marker = mMap.addMarker(MarkerOptions().position(latLng!!).title("ТЫ"))
                        markersMap[userId!!] = marker!!
                    }
                    else{
                        val marker = mMap.addMarker(MarkerOptions().position(latLng!!).title(userId))
                        markersMap[userId!!] = marker!!
                    }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Remove the marker from the map
                mMap.clear()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Do nothing
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Do nothing
            }

        }
        databaseRef.addChildEventListener(childEventListener)
    }
    override fun onDestroy() {
        super.onDestroy()
        databaseRef.removeEventListener(childEventListener)
    }

    private fun listeners() {
    }

    private lateinit var fusedLocClient: FusedLocationProviderClient
    // use it to request location updates and get the latest location

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap //initialise map
        requestLocationUpdates()
        //getCurrentLocation()
    }
    private fun setupLocClient() {
        fusedLocClient =
            LocationServices.getFusedLocationProviderClient(this)
    }
    // prompt the user to grant/deny access
    private fun requestLocPermissions() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), //permission in the manifest
            REQUEST_LOCATION)
    }
    companion object {
        private const val REQUEST_LOCATION = 1 //request code to identify specific permission request
        private const val TAG = "MapsActivity" // for debugging
    }
    private fun getCurrentLocation() {
        // Check if the ACCESS_FINE_LOCATION permission was granted before requesting a location
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            requestLocPermissions()
        } else {
            fusedLocClient.lastLocation.addOnCompleteListener {
                val location = it.result
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    val update = CameraUpdateFactory.newLatLngZoom(latLng, 16.0f)
                    mMap.moveCamera(update)
                } else {
                    Log.e(TAG, "No location found")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = 10000 // 10 seconds
        locationRequest.fastestInterval = 5000 // 5 seconds

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //check if the request code matches the REQUEST_LOCATION
        if (requestCode == REQUEST_LOCATION)
        {
            //check if grantResults contains PERMISSION_GRANTED.If it does, call getCurrentLocation()
            if (grantResults.size == 1 && grantResults[0] ==
                PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                //if it does not log an error message
                Log.e(TAG, "Location permission has been denied")
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.main_menu, menu)
    }
    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_changeName ->
            {
                return true
            }
            R.id.action_selectPhoto ->
            {
                return true
            }
            R.id.action_exit ->
            {
                signOut()
                return true
            }
            else -> false
        }
    }
    private fun signOut() {
        FirebaseAuth.getInstance().signOut()
        // [START auth_fui_signout]
        AuthUI.getInstance()
            .signOut(this)
            .addOnCompleteListener {
                //startActivity(Intent(this, LoginActivity::class.java))
                val intent = Intent(this, LoginActivity::class.java)
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                )
                startActivity(intent)
            }
        // [END auth_fui_signout]
    }
}

