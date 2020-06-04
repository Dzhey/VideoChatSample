package com.github.dzhey.videochatsample.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.dzhey.videochatsample.di.ApplicationComponent
import com.github.dzhey.videochatsample.di.scopes.ScreenScope
import com.github.dzhey.videochatsample.permissions.AppPermissionChecker
import dagger.Provides

interface MainViewContract {

    @dagger.Module
    class Module {
        @Suppress("UNCHECKED_CAST")
        @Provides
        @ScreenScope
        fun provideViewModelFactory(permissionChecker: AppPermissionChecker): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                    return modelClass.cast(MainViewModel(permissionChecker))!!
                }
            }
        }
    }

    @dagger.Component(
        dependencies = [ApplicationComponent::class],
        modules = [Module::class])
    @ScreenScope
    interface Component {
        @ScreenScope
        fun viewModelFactory(): ViewModelProvider.Factory
    }

    sealed class State {
        object CameraPermissionDeclined : State()
        object CameraPermissionRequired : State()
        object Content : State()
    }
}