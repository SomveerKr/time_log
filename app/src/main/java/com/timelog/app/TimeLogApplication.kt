package com.timelog.app

import android.app.Application
import com.timelog.app.data.TimeLogDatabase
import com.timelog.app.data.TimeLogStore

class TimeLogApplication : Application() {
    val store by lazy { TimeLogStore(TimeLogDatabase.open(this)) }
}
