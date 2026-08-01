package com.my.parking

import android.app.Application
import com.my.parking.di.AppContainer

class MyParkingApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(applicationContext) }
}
