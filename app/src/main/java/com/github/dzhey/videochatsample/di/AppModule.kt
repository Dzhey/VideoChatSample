package com.github.dzhey.videochatsample.di

import android.content.Context
import com.github.dzhey.videochatsample.di.scopes.ApplicationScope
import com.github.dzhey.videochatsample.permissions.AppPermissionChecker
import com.github.dzhey.videochatsample.permissions.AppPermissionCheckerImpl
import dagger.Provides

@dagger.Module
class AppModule(context: Context) {

    private val context = context.applicationContext

    @Provides
    fun providePermissionChecker(): AppPermissionChecker {
        return AppPermissionCheckerImpl(context)
    }
}