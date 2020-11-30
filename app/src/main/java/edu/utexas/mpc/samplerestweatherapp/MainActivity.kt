package edu.utexas.mpc.samplerestweatherapp

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage

class MainActivity : AppCompatActivity() {


    // I'm using lateinit for these widgets because I read that repeated calls to findViewById
    // are energy intensive
    lateinit var textView: TextView
    lateinit var retrieveButton: Button
    lateinit var imageView: ImageView
    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var mostRecentWeatherResult: WeatherResult
    lateinit var syncButton: Button
    lateinit var stepCount: TextView
    // I'm doing a late init here because I need this to be an instance variable but I don't
    // have all the info I need to initialize it yet
    lateinit var mqttAndroidClient: MqttAndroidClient
    var temp: String = "No temperature data yet"
    var weatherData:String = ""
    // you may need to change this depending on where your MQTT broker is running
    val serverUri = "tcp://192.168.4.1"
    // you can use whatever name you want to here
    val clientId = "EmergingTechMQTTClient"

    //these should "match" the topics on the "other side" (i.e, on the Raspberry Pi)
    val subscribeTopic = "steps"
    val publishTopic = "weather"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = this.findViewById(R.id.imageView)
        textView = this.findViewById(R.id.text)
        stepCount = this.findViewById(R.id.stepCount)
//        syncButton = this.findViewById(R.id.syncBtn)
        retrieveButton = this.findViewById(R.id.retrieveButton)

        // when the user presses the syncbutton, this method will get called
        retrieveButton.setOnClickListener({
            connectToWiFiDialog();
        })

//        syncButton.setOnClickListener({ syncWithPi() })

        queue = Volley.newRequestQueue(this)
        gson = Gson()

        //mqtt client
        mqttAndroidClient = MqttAndroidClient(getApplicationContext(), serverUri, clientId);

        // when things happen in the mqtt client, these callbacks will be called
        mqttAndroidClient.setCallback(object : MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
                // this subscribes the client to the subscribe topic
                mqttAndroidClient.subscribe(subscribeTopic, 0)
                val message = MqttMessage()
                message.payload = (weatherData).toByteArray()

                // this publishes a message to the publish topic
                mqttAndroidClient.publish(publishTopic, message)
            }

            // this method is called when a message is received that fulfills a subscription
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                println(message)
                stepCount.text = message.toString()
            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
            }
        })
    }

    fun connectToWiFiDialog(){
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setMessage("Are you connected to a WiFi network?")
                .setCancelable(false)
                .setPositiveButton("Yes", DialogInterface.OnClickListener {
                    dialog, id -> dialog.dismiss()
                    requestWeather();
                })
                .setNegativeButton("No, open network settings", DialogInterface.OnClickListener {
                    dialog, id -> dialog.cancel()
                    startActivityForResult(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS), 0)
                    connectToWiFiDialog();
                })

        val alert = dialogBuilder.create()
        alert.setTitle("WIFI Connection")
        alert.show()
    }

    fun connectToMqttDialog(){
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setMessage("Are you connected to the MQTT network?")
                .setCancelable(false)
                .setPositiveButton("Yes", DialogInterface.OnClickListener {
                    dialog, id -> dialog.dismiss()
                    syncWithPi();
                })
                .setNegativeButton("No, open network settings", DialogInterface.OnClickListener {
                    dialog, id -> dialog.cancel()
                    startActivityForResult(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS), 0)
                    connectToMqttDialog()
                })

        val alert = dialogBuilder.create()
        alert.setTitle("MQTT Connection")
        alert.show()
    }

    fun requestWeather(){
        val url = StringBuilder("https://api.openweathermap.org/data/2.5/onecall?lat=30.267153&lon=-97.743057&appid=48caddab30c1aa1a983fdf0e0836dd61&units=imperial&exclude=hourly,minutely").toString()
        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)
                    var current = mostRecentWeatherResult.current
                    var iconurl: String = "https://openweathermap.org/img/w/" + current.weather.get(0).icon + ".png";
                    var today = mostRecentWeatherResult.daily[0].temp;
                    var tomorrow = mostRecentWeatherResult.daily[1];
                    weatherData = today.max.toString() + "," + today.min.toString() + ',' + current.humidity.toString() + "," + tomorrow.temp.max.toString() + "," + tomorrow.temp.min.toString() + "," + tomorrow.humidity.toString();
                    textView.text = current.temp.toString()
                    Picasso.get().load(iconurl).into(imageView);
                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequest)
        connectToMqttDialog()
    }

    // this method just connects the paho mqtt client to the broker
    fun syncWithPi(){
        println("+++++++ Connecting...")
        mqttAndroidClient.connect()
    }
}

class WeatherResult(val id: Int, val name: String, val cod: Int, val coord: Coordinates, val current: WeatherMain, val daily: Array<DailyForecast>)
class DailyForecast(val dt: Int, val temp: DailyTemps, val humidity: Int, val weather: Array<Weather>)
class DailyTemps(val min: Double, val max: Double)
class Coordinates(val lon: Double, val lat: Double)
class Weather(val id: Int, val main: String, val description: String, val icon: String)
class WeatherMain(val temp: Double, val humidity: Int, val weather: Array<Weather>)