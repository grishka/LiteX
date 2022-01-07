# LiteX

You no longer need the AppCompat library when you target API 21 or higher because most of the system APIs it wraps into `SomethingCompat.whatever()` calls are now available directly. **LiteX** is a self-contained fork of several popular AndroidX libraries, without the AppCompat dependency.

### Provided libraries and their versions

* [browser](https://developer.android.com/jetpack/androidx/releases/browser) 1.4.0
* [collection](https://developer.android.com/jetpack/androidx/releases/collection) 1.1.0
* [concurrent](https://developer.android.com/jetpack/androidx/releases/concurrent) 1.1.0
* [drawerlayout](https://developer.android.com/jetpack/androidx/releases/drawerlayout) 1.1.1
* [dynamicanimation](https://developer.android.com/jetpack/androidx/releases/dynamicanimation) 1.1.0-alpha03
* [recyclerview](https://developer.android.com/jetpack/androidx/releases/recyclerview) 1.2.1
* [swiperefreshlayout](https://developer.android.com/jetpack/androidx/releases/swiperefreshlayout) 1.1.0
* [viewpager](https://developer.android.com/jetpack/androidx/releases/viewpager) 1.0.0
* [viewpager2](https://developer.android.com/jetpack/androidx/releases/viewpager2) 1.0.0

### Differences from AndroidX

All these libraries are intended to be drop-in replacement for the corresponding AndroidX ones, with the following exceptions:
* **browser**: `TrustedWebUtils.transferSplashImage` takes a `Uri` instead of a `File` because FileProvider it would use otherwise is part of AppCompat.
* **viewpager2** lacks the `FragmentStateAdapter` class because it heavily relies on the specifics of AppCompat fragments, lifecycle, FragmentActivities and all other nonsense.

### Usage

These libraries are now published on Maven Central. Add the libraries you need to the `dependencies` section in your app project `build.gradle` — just replace `androidx.whatever` with `me.grishka.litex`:

```
implementation 'me.grishka.litex:recyclerview:1.2.1'
implementation 'me.grishka.litex:drawerlayout:1.1.1'
```

I won't recommend using both LiteX and AndroidX in the same project. It would probably work in the end, but you'll most likely have some conflict resolution to do.

### Using with libraries that depend on AndroidX

Sometimes you may need to use a library that pulls in a whole bunch of AndroidX _stuff_ that it doesn't actually need, or it's something that's covered by LiteX anyway. You can exclude specific dependencies of your dependencies like this:

```
implementation('com.whatever.something:library:1.2.3') {
	exclude group: 'androidx.appcompat'
}
```

If you do this, it may be advisable that you read those parts of the source of your library that make use of these dependencies to make sure your app won't be crashing with a NoClassDefFound error due to the missing classes. More often than not though, it's going to be something like a "conveniently" provided appcompat fragment that you aren't going to be using anyway.
