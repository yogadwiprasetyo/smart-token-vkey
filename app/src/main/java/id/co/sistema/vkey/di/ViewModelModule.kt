package id.co.sistema.vkey

import id.co.sistema.vkey.smarttoken.SmartTokenViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { SmartTokenViewModel(get()) }
}