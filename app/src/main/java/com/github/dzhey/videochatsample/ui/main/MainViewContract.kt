package com.github.dzhey.videochatsample.ui.main

import android.graphics.Point
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.dzhey.videochatsample.decoder.VideoPlayer
import com.github.dzhey.videochatsample.decoder.VideoPlayerImpl
import com.github.dzhey.videochatsample.di.ApplicationComponent
import com.github.dzhey.videochatsample.di.scopes.ScreenScope
import com.github.dzhey.videochatsample.permissions.AppPermissionChecker
import com.github.dzhey.videochatsample.ui.App
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

        @Provides
        fun provideVideoPlayer(): VideoPlayer {
            return VideoPlayerImpl(App.instance)
        }
    }

    @dagger.Component(
        dependencies = [ApplicationComponent::class],
        modules = [Module::class])
    @ScreenScope
    interface Component {
        @ScreenScope
        fun viewModelFactory(): ViewModelProvider.Factory

        fun inject(fragment: MainFragment)
    }

    sealed class State {
        object CameraPermissionDeclined : State()
        object CameraPermissionRequired : State()
        data class Content(
            val avatarPosition: Point? = null,
            val isShowingVideos: Boolean = false
        ) : State()
    }
}