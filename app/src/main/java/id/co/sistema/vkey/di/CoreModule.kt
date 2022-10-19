package id.co.sistema.vkey

import id.co.sistema.vkey.smarttoken.SmartTokenRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreModule = module {
    single {
        SmartTokenRepository(androidContext())
    }
}