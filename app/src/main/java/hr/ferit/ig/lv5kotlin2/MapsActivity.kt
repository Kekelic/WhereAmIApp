package hr.ferit.ig.lv5kotlin2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import hr.ferit.ig.lv5kotlin2.databinding.ActivityMapsBinding
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    //Location
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    var fusedLocationProviderClient: FusedLocationProviderClient? = null
    var currentLocation: Location? = null

    //SoundPOOL
    private lateinit var mSoundPool: SoundPool
    private var mLoaded: Boolean = false
    var mSoundMap: HashMap<Int, Int> = HashMap()

    //notification
    private val CHANNEL_ID = "channel_id_01"
    private val notificationId = 101

    //camera
    private var isReadPermissionGranted = false
    private var isWritePermissionGranted = false
    private var isCameraPermissionGranted = false
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var currentUri: Uri
    private val IMAGE_CHOOSE = 1001;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                isReadPermissionGranted =
                    permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE]
                        ?: isReadPermissionGranted
                isWritePermissionGranted =
                    permissions[android.Manifest.permission.WRITE_EXTERNAL_STORAGE]
                        ?: isWritePermissionGranted
                isCameraPermissionGranted =
                    permissions[android.Manifest.permission.CAMERA] ?: isCameraPermissionGranted

            }

        createNotificationChannel()

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        fetchLocation()

        this.loadSound()

        requestCameraPermission()
        initCamera()
    }

    //camera
    private fun initCamera() {
        val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
            lifecycleScope.launch {

                if (isWritePermissionGranted && isCameraPermissionGranted) {
                    val geoCoder = Geocoder(applicationContext, Locale.getDefault())
                    val addresses = geoCoder.getFromLocation(
                        currentLocation!!.latitude,
                        currentLocation!!.longitude,
                        1
                    )
                    val address = addresses[0].getAddressLine(0).toString()

                    if (savePhotoToExternalStorage(
                            address + " " + SimpleDateFormat("yyyyMMdd_HHmmss").format(
                                Date()
                            ), it
                        )
                    ) {

                        Toast.makeText(
                            this@MapsActivity,
                            "Photo Saved Successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        sendNotification()

                    } else {

                        Toast.makeText(
                            this@MapsActivity,
                            "Failed to Save photo",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } else {

                    Toast.makeText(this@MapsActivity, "Permission not Granted", Toast.LENGTH_SHORT)
                        .show()

                }

            }

        }

        binding.btnCamera.setOnClickListener { takePhoto.launch() }
    }


    private fun sdkCheck(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true
        }
        return false
    }

    private fun requestCameraPermission() {

        val isReadPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val isWritePermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val isCameraPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val minSdkLevel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        isReadPermissionGranted = isReadPermission
        isWritePermissionGranted = isWritePermission || minSdkLevel
        isCameraPermissionGranted = isCameraPermission

        val permissionRequest = mutableListOf<String>()
        if (!isWritePermissionGranted) {

            permissionRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

        }
        if (!isReadPermissionGranted) {

            permissionRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)

        }
        if (!isCameraPermissionGranted) {

            permissionRequest.add(android.Manifest.permission.CAMERA)

        }

        if (permissionRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionRequest.toTypedArray())
        }

    }

    private fun savePhotoToExternalStorage(name: String, bmp: Bitmap?): Boolean {

        val imageCollection: Uri = if (sdkCheck()) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        currentUri = imageCollection

        val contentValues = ContentValues().apply {

            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (bmp != null) {
                put(MediaStore.Images.Media.WIDTH, bmp.width)
                put(MediaStore.Images.Media.HEIGHT, bmp.height)
            }

        }

        return try {

            contentResolver.insert(imageCollection, contentValues)?.also {

                contentResolver.openOutputStream(it).use { outputStream ->

                    if (bmp != null) {

                        if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {

                            throw IOException("Failed to save Bitmap")
                        }
                    }
                }

            } ?: throw IOException("Failed to create Media Store entry")
            true
        } catch (e: IOException) {

            e.printStackTrace()
            false
        }

    }


    //notification
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notification Title"
            val descriptionText = "Notification Description"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification() {
        val intent = Intent(Intent.ACTION_VIEW, currentUri)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, IMAGE_CHOOSE, intent, 0)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_camera)
            .setContentTitle("Image saved")
            .setContentText("Open saved image")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, builder.build())
        }
    }

    //SoundPool
    private fun loadSound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.mSoundPool = SoundPool.Builder().setMaxStreams(10).build()
        } else {
            this.mSoundPool = SoundPool(10, AudioManager.STREAM_MUSIC, 0)
        }
        this.mSoundPool.setOnLoadCompleteListener { _, _, _ -> mLoaded = true }
        this.mSoundMap[R.raw.tap] = this.mSoundPool.load(this, R.raw.tap, 1)
    }

    //Location
    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                1000
            )
            return
        }
        val task = fusedLocationProviderClient?.lastLocation
        task?.addOnSuccessListener { location ->
            if (location != null) {
                this.currentLocation = location
                val mapFragment = supportFragmentManager
                    .findFragmentById(R.id.map) as SupportMapFragment
                mapFragment.getMapAsync(this)
            }
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1000 -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation()
                requestCameraPermission()
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap


        var latlong = LatLng(currentLocation?.latitude!!, currentLocation?.longitude!!)
        drawMarker(latlong)
        setLoctionDetails(latlong)

        mMap.setOnMapClickListener(this)
    }

    private fun setLoctionDetails(latLng: LatLng) {
        val latitude = latLng.latitude
        val longitude = latLng.longitude
        val geoCoder = Geocoder(this, Locale.getDefault())
        val addresses = geoCoder.getFromLocation(latitude, longitude, 1)
        val country = addresses[0].countryName
        val city = addresses[0].locality
        val address = addresses[0].thoroughfare
        val addressNumber = addresses[0].subThoroughfare
        binding.tvLocationDetails.text =
            "Latitude: $latitude\nLongitude: $longitude\nCountry: $country\nCity: $city\nAddress: $address $addressNumber"

    }

    private fun drawMarker(latLng: LatLng) {
        val markerOption = MarkerOptions().position(latLng).title("I am here")
            .snippet(getAddres(latLng.latitude, latLng.longitude))

        mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f))
        mMap.addMarker(markerOption)
    }

    private fun getAddres(lat: Double, lon: Double): String? {
        val geoCoder = Geocoder(this, Locale.getDefault())
        val addresses = geoCoder.getFromLocation(lat, lon, 1)
        return addresses[0].getAddressLine(0).toString()
    }

    override fun onMapClick(latLng: LatLng) {
        val markerOption = MarkerOptions().position(latLng).title("You marked here")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            .snippet(getAddres(latLng.latitude, latLng.longitude))
        mMap.addMarker(markerOption)
        val soundID = this.mSoundMap[R.raw.tap] ?: 0
        this.mSoundPool.play(soundID, 1f, 1f, 1, 0, 1f)
    }


}
