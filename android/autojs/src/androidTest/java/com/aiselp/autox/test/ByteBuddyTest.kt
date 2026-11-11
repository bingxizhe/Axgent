package com.aiselp.autox.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy.Wrapping
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import net.bytebuddy.implementation.FixedValue
import net.bytebuddy.matcher.ElementMatchers
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File


@RunWith(AndroidJUnit4::class)
class ByteBuddyTest {

    @Test
    fun base_test() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val strategy: ClassLoadingStrategy<ClassLoader?> = Wrapping(
            context.getDir(
                "generated", Context.MODE_PRIVATE
            )
        )

        val dynamicType = ByteBuddy()
            .subclass(File::class.java)
            .method(ElementMatchers.named("toString"))
            .intercept(FixedValue.value("Hello World!"))
            .make()
            .load(javaClass.getClassLoader(), strategy)
            .getLoaded()
        val file = dynamicType.getConstructor(String::class.java).newInstance("/")
        assert(file.toString() == "Hello World!")
        assert(file is File)
    }

}