package com.github.dzhey.videochatsample.di

import com.github.dzhey.videochatsample.di.scopes.ApplicationScope
import com.github.dzhey.videochatsample.permissions.AppPermissionChecker


@dagger.Component(modules = [AppModule::class])
@ApplicationScope
interface ApplicationComponent {
    fun appPermissionChecker(): AppPermissionChecker
}