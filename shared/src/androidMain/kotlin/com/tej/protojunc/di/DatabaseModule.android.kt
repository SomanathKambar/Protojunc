package com.tej.protojunc.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tej.protojunc.p2p.data.db.ProtojuncDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val databaseModule = module {
    single<ProtojuncDatabase> {
        val dbFile = androidContext().getDatabasePath("protojunc.db")
        Room.databaseBuilder<ProtojuncDatabase>(
            context = androidContext(),
            name = dbFile.absolutePath
        )
        .setDriver(BundledSQLiteDriver())
        .build()
    }
    
    single { get<ProtojuncDatabase>().peerDao() }
    single { get<ProtojuncDatabase>().surgicalReportDao() }
}
