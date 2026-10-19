package com.example.nearbyalert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationTrackingService : Service() {
    private lateinit var fused:FusedLocationProviderClient
    private var started=false
    private val inside=mutableSetOf<String>()
    companion object { const val ACTION_DESTINATIONS_CHANGED="com.example.nearbyalert.DESTINATIONS_CHANGED" }
    private val callback=object:LocationCallback(){override fun onLocationResult(result:LocationResult){result.lastLocation?.let{checkDestinations(it)}}}
    override fun onCreate(){super.onCreate();createChannels();fused=LocationServices.getFusedLocationProviderClient(this);val raw=DestinationStore.prefs(this).getString("inside_ids","[]")?:"[]";val a=JSONArray(raw);for(i in 0 until a.length())inside.add(a.optString(i))}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==ACTION_DESTINATIONS_CHANGED){if(started){checkDestinationsFromLastLocation();updateTrackingNotification()}else if(DestinationStore.prefs(this).getBoolean("tracking_active",false)){startForeground(1001,trackingNotification("현재 위치를 확인하고 있습니다."));requestUpdates()}else{stopSelf()};return START_STICKY}
        startForeground(1001,trackingNotification("현재 위치를 확인하고 있습니다."));if(!started)requestUpdates();return START_STICKY
    }
    private fun requestUpdates(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){DestinationStore.prefs(this).edit().putBoolean("tracking_active",false).apply();stopSelf();return};val req=LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,15000L).setMinUpdateIntervalMillis(7500L).setWaitForAccurateLocation(false).build();try{fused.requestLocationUpdates(req,callback,mainLooper);started=true}catch(_:SecurityException){DestinationStore.prefs(this).edit().putBoolean("tracking_active",false).apply();stopSelf()}}
    private fun checkDestinationsFromLastLocation(){try{fused.lastLocation.addOnSuccessListener{it?.let{loc->checkDestinations(loc)}}}catch(_:SecurityException){}}
    private fun checkDestinations(current:Location){
        DestinationStore.prefs(this).edit().putString("lat",current.latitude.toString()).putString("lon",current.longitude.toString()).putLong("time",System.currentTimeMillis()).apply()
        val prefs=DestinationStore.prefs(this);val all=DestinationStore.get(this);val destinations=all.filter{it.enabled};val activeIds=destinations.map{it.id}.toSet();inside.retainAll(activeIds)
        var nearest:Destination?=null;var nearestMeters=Float.MAX_VALUE;val autoDisable=prefs.getBoolean("auto_disable",false);val hysteresis=prefs.getBoolean("hysteresis",true);val armed=mutableSetOf<String>();armed.addAll(inside)
        destinations.forEach{d->val out=FloatArray(1);Location.distanceBetween(current.latitude,current.longitude,d.latitude,d.longitude,out);val meters=out[0];if(meters<nearestMeters){nearest=d;nearestMeters=meters};val wasInside=inside.contains(d.id);if(meters<=d.radiusMeters){if(!wasInside){notifyArrival(d,meters,prefs);if(autoDisable){val updated=all.map{if(it.id==d.id)it.copy(enabled=false)else it};DestinationStore.save(this,updated)}else armed.add(d.id)}}else if(meters>d.radiusMeters+(if(hysteresis)150f else 0f)){armed.remove(d.id)}}
        inside.clear();inside.addAll(armed.filter{id->destinations.any{it.id==id}});val arr=JSONArray();inside.forEach{arr.put(it)};prefs.edit().putString("inside_ids",arr.toString()).apply();updateTrackingNotification(nearest,nearestMeters)
    }
    private fun notifyArrival(d:Destination,meters:Float,prefs:android.content.SharedPreferences){val title="${d.name} 근처에 도착했어요";val text="목적지까지 ${formatDistance(meters)} · 설정 반경 ${d.radiusMeters.toInt()}m";if(prefs.getBoolean("vibrate",true))vibrate();if(prefs.getBoolean("sound",true)){try{RingtoneManager.getRingtone(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()}catch(_:Exception){}};val manager=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;manager.notify(alertId(d.id),alertNotification(d.id,title,text));val raw=prefs.getString("history","[]")?:"[]";val old=JSONArray(raw);val arr=JSONArray();val stamp=SimpleDateFormat("MM-dd HH:mm",Locale.KOREA).format(Date());arr.put("$stamp · $title\n$text");for(i in 0 until minOf(old.length(),49))arr.put(old.getString(i));prefs.edit().putString("history",arr.toString()).apply()}
    private fun formatDistance(m:Float)=if(m<1000)"${m.toInt()}m" else "%.1fkm".format(m/1000f)
    private fun alertId(id:String)=(id.hashCode() and 0x7fffffff)%100000+2000
    private fun vibrate(){try{
        val v:Vibrator=if(Build.VERSION.SDK_INT>=31)(getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else (getSystemService(VIBRATOR_SERVICE) as Vibrator)
        if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createWaveform(longArrayOf(0,450,200,450),-1)) else v.vibrate(650)
    }catch(_:Exception){}}
    private fun createChannels(){if(Build.VERSION.SDK_INT>=26){val m=getSystemService(NotificationManager::class.java);m.createNotificationChannel(NotificationChannel("nearby_alert","목적지 진입 알림",NotificationManager.IMPORTANCE_HIGH).apply{description="목적지 반경 진입 시 알림";enableVibration(false);setSound(null,null);vibrationPattern=longArrayOf(0,450,200,450)});m.createNotificationChannel(NotificationChannel("tracking_status","위치 감지 상태",NotificationManager.IMPORTANCE_LOW).apply{description="현재 위치와 가장 가까운 목적지까지의 거리"})}}
    private fun pendingIntent(destinationId:String?):PendingIntent{val i=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP);if(destinationId!=null)i.putExtra("destination_id",destinationId);return PendingIntent.getActivity(this,if(destinationId==null)0 else alertId(destinationId),i,PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=23)PendingIntent.FLAG_IMMUTABLE else 0)}
    private fun trackingNotification(text:String):Notification=NotificationCompat.Builder(this,"tracking_status").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("근처 알림 · 감지 중").setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pendingIntent(null)).setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    private fun alertNotification(id:String,title:String,text:String):Notification=NotificationCompat.Builder(this,"nearby_alert").setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pendingIntent(id)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
    private fun updateTrackingNotification(d:Destination?=null,meters:Float=Float.MAX_VALUE){val text=if(d==null)"활성 목적지를 기다리는 중" else "가장 가까운 ${d.name}: ${formatDistance(meters)} · 활성 목적지 ${DestinationStore.get(this).count{it.enabled}}개";(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1001,trackingNotification(text))}
    override fun onDestroy(){if(::fused.isInitialized)fused.removeLocationUpdates(callback);started=false;super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
}
