package id.co.sistema.vkey

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { SmartTokenViewModel(get()) }
}