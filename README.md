# LiteX

You no longer need the AppCompat library when you target API 21 or higher because most of the system APIs it wraps into `SomethingCompat.whatever()` calls are now available directly. **LiteX** is a self-contained fork of several popular AndroidX libraries, without the AppCompat dependency.

### Differences from AndroidX

All these libraries are intended to be drop-in replacement for the corresponding AndroidX ones, with the following exceptions:

* **viewpager2** lacks the `FragmentStateAdapter` class because it heavily relies on the specifics of AppCompat fragments, lifecycle, FragmentActivities and all other nonsense.

### Usage

Add JitPack to your root `build.gradle`:

    allprojects {
     repositories {
        jcenter()
        maven { url "https://jitpack.io" }
     }
    }

Add the libraries you need to the `dependencies` section in your app project `build.gradle`:

    api 'com.github.grishka.litex:annotation:1.0'
    compile 'com.github.grishka.litex:recyclerview:1.0'
    compile 'com.github.grishka.litex:drawerlayout:1.0'

I won't recommend using both LiteX and AndroidX in the same project. It would probably work at the end, but you'll most likely have some conflict resolution to do.