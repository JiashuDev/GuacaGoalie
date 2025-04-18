package com.su.mynavigation

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.Manifest
import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.location.Location
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.su.mynavigation.database.DatabaseHelper
import com.su.mynavigation.database.LatlngDB
import com.su.mynavigation.database.UserListModel
import com.su.mynavigation.database.latlng


class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var db: DatabaseHelper
    private lateinit var lastLocation: Location
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    lateinit var userList: List<UserListModel>
    private lateinit var llList: List<latlng>
    private lateinit var dashboard: Dashboard
    private lateinit var latLangDB: LatlngDB
    private lateinit var dashboardView: View
    private var llID: Int = 0
    private lateinit var mainActivityInstance: MainActivity
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickFromGalleryLauncher: ActivityResultLauncher<Intent>
    private var sensorManager: SensorManager? = null
    private var totalSteps = 0f
    private lateinit var dpoints : TextView

    companion object {
        private const val ACTIVITY_RECOGNITION_REQUEST_CODE = 1
        private const val LOCATION_REQUEST_CODE = 1
    }

    private var running = false

    // Creating a variable which will counts total steps
    // and it has been given the value of 0 float


    // Creating a variable  which counts previous total
    // steps and it has also been given the value of 0 float
    private var previousTotalSteps = 0f


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_dashboard)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                MainActivity.LOCATION_REQUEST_CODE
            )
        }
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        dashboardView = window.decorView.rootView
        dashboard = Dashboard()
        mainActivityInstance = this
        db = DatabaseHelper(this)
        latLangDB = LatlngDB(this)

        val editButton = findViewById<Button>(R.id.button2)
        loadData()
        resetSteps()
//        latLangDB.deleteAll()
//        val ll1 = latlng(43.088947, -76.154480, 0)
//        latLangDB.insert(ll1)
        userList = db.getAllInfo()
        llList = latLangDB.getAllInfo()
        llID = llList.size

        if (userList.isEmpty()) {
            val nextPage = Intent(this, Intro::class.java)
            startActivity(nextPage)
            finish()
        } else {
            val name = userList[0].name
            val about = userList[0].about
            val goal = userList[0].goal
            val milestone = userList[0].milestone
            val dName = findViewById<TextView>(R.id.usernameText)
            val dAbout = findViewById<TextView>(R.id.aboutMeMessageText)
            val dGoal = findViewById<TextView>(R.id.weeklyGoalNumberText)
            val dMile = findViewById<TextView>(R.id.milestoneNumberText)
            val dsteps = findViewById<TextView>(R.id.stepsNumberText)
            dName.text = name
            dAbout.text = about
            dGoal.text = goal.toString()
            dMile.text = milestone.toString()
            dsteps.text = userList[0].steps.toString()
        }


        editButton.setOnClickListener() {
            val nextPage = Intent(this, Edit::class.java)
            startActivity(nextPage)
            finish()
        }

        // Adding a context of SENSOR_SERVICE as Sensor Manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val milestoneButton = findViewById<ImageButton>(R.id.milestoneGoalButton)
        val stepsTodayButton3 = findViewById<ImageButton>(R.id.stepsTodayButton3)
        val profilePhotoButton = findViewById<ImageButton>(R.id.profilePhotoButton)
        val view: View = findViewById(R.id.animatedBackgroundTop)
        val animationDrawable: AnimationDrawable = view.background as AnimationDrawable


        dpoints = findViewById<TextView>(R.id.guacpointsText)
        bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.dashboard

        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.dashboard -> {

                }

                R.id.rewards -> {
                    val nextPage = Intent(this, Rewards::class.java)
                    startActivity(nextPage)
                    finish()
                }

                R.id.map -> {
                    val nextPage = Intent(this, Map::class.java)
                    startActivity(nextPage)
                    finish()
                }
            }
            true
        }


        milestoneButton.setOnClickListener {
            dashboard.accessMilestone(mainActivityInstance, db)
        }

        stepsTodayButton3.setOnClickListener {
            dashboard.acessDaily(dashboardView, mainActivityInstance, db)
        }

        // Initialize the ActivityResultLauncher for taking a picture
        takePictureLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data: Intent? = result.data
                    val imageBitmap = data?.extras?.get("data") as Bitmap
                    profilePhotoButton.setImageBitmap(imageBitmap)
                }
            }


        // Initialize the ActivityResultLauncher for picking from gallery
        pickFromGalleryLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data: Intent? = result.data
                    val imageUri = data?.data
                    profilePhotoButton.setImageURI(imageUri)
                }
            }

        profilePhotoButton.setOnClickListener {
            showChangeProfilePhotoDialog()
        }

        // Animation for the top part of dashboard page.
        animationDrawable.setEnterFadeDuration(2500)
        animationDrawable.setExitFadeDuration(5000)
        animationDrawable.start()


        // Set up a TextWatcher to continuously monitor changes in guacpointsText
        dpoints.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // Convert the text to integer and update level visibility
                Log.d("MainActivity", "omg this works")
                val guacPoints = s?.toString()?.toIntOrNull() ?: 0
                updateLevelVisibility(guacPoints)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not used
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Not used
            }
        })
        dpoints.text = userList[0].rewards.toString()
    }


    private fun showChangeProfilePhotoDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Change Profile Photo")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    // This is for talking pictures.

                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        // If it's the first time launching GuacaGoalie or we don't have permission
                        // to use the camera, ask for permission here.
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.CAMERA),
                            100
                        )
                    } else {
                        // Permission has already been granted, proceed with launching camera
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        takePictureLauncher.launch(intent)
                    }
                }

                1 -> {
                    // This is for picking images from the gallery for the profile photo.
                    val intent =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    pickFromGalleryLauncher.launch(intent)
                }

                2 -> dialog.dismiss()
            }
        }
        builder.show()
    }

    /**
     * This is for the user profile photo button, if they want to change their profile photo.
     * This function handles the user's input for camera permission request.
     * Does the user grant us camera permission or deny? Handles logic here.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Camera permission granted, proceed with launching camera
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                takePictureLauncher.launch(intent)
            } else {
                // Camera permission denied, handle accordingly
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onResume() {
        super.onResume()
        running = true

        // Returns the number of steps taken by the user since the last reboot while activated
        // This sensor requires permission android.permission.ACTIVITY_RECOGNITION.
        // So don't forget to add the following permission in AndroidManifest.xml present in manifest folder of the app.
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)


        if (stepSensor == null) {
            // This will give a toast message to the user if there is no sensor in the device
            Toast.makeText(this, "No sensor detected on this device", Toast.LENGTH_SHORT).show()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    ACTIVITY_RECOGNITION_REQUEST_CODE
                )

            }
            // Rate suitable for the user interface
            sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {

        // Calling the TextView that we made in activity_main.xml
        // by the id given to that TextView
        var tv_stepsTaken = findViewById<TextView>(R.id.stepsNumberText)

        if (running) {
            totalSteps = event!!.values[0]

            // Current steps are calculated by taking the difference of total steps
            // and previous steps
            val currentSteps = totalSteps.toInt() - previousTotalSteps.toInt()

            // It will show the current steps to the user
            tv_stepsTaken.text = ("$currentSteps")
            if (currentSteps % userList[0].milestone == 0 && currentSteps != 0) {
                Toast.makeText(this, "You have reached a mileStone", Toast.LENGTH_SHORT).show()
                val rewards = userList[0].rewards
                db.updateRewards(rewards + 100, userList[0])
                userList = db.getAllInfo()
                dpoints = findViewById<TextView>(R.id.guacpointsText)
                dpoints.text = userList[0].rewards.toString()
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                        MainActivity.LOCATION_REQUEST_CODE
                    )
                }
                fusedLocationProviderClient.lastLocation.addOnSuccessListener(this) { location ->
                    if (location != null) {
                        lastLocation = location
                        val temp = latlng(location.latitude, location.longitude, llID)
                        latLangDB.insert(temp)
                        llList = latLangDB.getAllInfo()
                        llID = llList.size
                    }
                }
            }
        }
    }

    fun resetSteps() {
        var tv_stepsTaken = findViewById<TextView>(R.id.stepsNumberText)
        tv_stepsTaken.setOnClickListener {
            // This will give a toast message if the user want to reset the steps
            Toast.makeText(this, "Long tap to reset steps", Toast.LENGTH_SHORT).show()
        }

        tv_stepsTaken.setOnLongClickListener {

            previousTotalSteps = totalSteps
            // When the user will click long tap on the screen,
            // the steps will be reset to 0
            tv_stepsTaken.text = 0.toString()

            // This will save the data
            saveData()

            true
        }
    }

    private fun saveData() {

        // Shared Preferences will allow us to save
        // and retrieve data in the form of key,value pair.
        // In this function we will save data
        val sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)

        val editor = sharedPreferences.edit()
        editor.putFloat("key1", previousTotalSteps)
        editor.putFloat("key2", totalSteps)
        editor.apply()
    }

    private fun loadData() {

        // In this function we will retrieve data
        val sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val savedNumber = sharedPreferences.getFloat("key1", 0f)
        val savedNumbner2 = sharedPreferences.getFloat("key2", 0f)

        // Log.d is used for debugging purposes
        Log.d("MainActivity", "$savedNumber")

        previousTotalSteps = savedNumber
        totalSteps = savedNumbner2
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // We do not have to write anything in this function for this app
    }

    // Function to update the visibility of level TextViews based on guacpointsText value
    fun updateLevelVisibility(guacPoints: Int) {
        val level1 = findViewById<TextView>(R.id.level1)
        val level2 = findViewById<TextView>(R.id.level2)
        val level3 = findViewById<TextView>(R.id.level3)
        val level4 = findViewById<TextView>(R.id.level4)
        val level5 = findViewById<TextView>(R.id.level5)
        val level6 = findViewById<TextView>(R.id.level6)
        val level7 = findViewById<TextView>(R.id.level7)
        val level8 = findViewById<TextView>(R.id.level8)
        val level9 = findViewById<TextView>(R.id.level9)
        val level10 = findViewById<TextView>(R.id.level10)

        level1.visibility = View.INVISIBLE
        level2.visibility = View.INVISIBLE
        level3.visibility = View.INVISIBLE
        level4.visibility = View.INVISIBLE
        level5.visibility = View.INVISIBLE
        level6.visibility = View.INVISIBLE
        level7.visibility = View.INVISIBLE
        level8.visibility = View.INVISIBLE
        level9.visibility = View.INVISIBLE
        level10.visibility = View.INVISIBLE

        if (guacPoints < 100) {
            level1.visibility = View.INVISIBLE
        } else {
            level1.visibility = View.VISIBLE
        }

        if (guacPoints < 200) {
            level2.visibility = View.INVISIBLE
        } else {
            level2.visibility = View.VISIBLE
        }

        if (guacPoints < 300) {
            level3.visibility = View.INVISIBLE
        } else {
            level3.visibility = View.VISIBLE
        }

        if (guacPoints < 400) {
            level4.visibility = View.INVISIBLE
        } else {
            level4.visibility = View.VISIBLE
        }

        if (guacPoints < 500) {
            level5.visibility = View.INVISIBLE
        } else {
            level5.visibility = View.VISIBLE
        }

        if (guacPoints < 600) {
            level6.visibility = View.INVISIBLE
        } else {
            level6.visibility = View.VISIBLE
        }

        if (guacPoints < 700) {
            level7.visibility = View.INVISIBLE
        } else {
            level7.visibility = View.VISIBLE
        }

        if (guacPoints < 800) {
            level8.visibility = View.INVISIBLE
        } else {
            level8.visibility = View.VISIBLE
        }

        if (guacPoints < 900) {
            level9.visibility = View.INVISIBLE
        } else {
            level9.visibility = View.VISIBLE
        }

        if (guacPoints < 1000) {
            level10.visibility = View.INVISIBLE
        } else {
            level10.visibility = View.VISIBLE
        }
    }

}