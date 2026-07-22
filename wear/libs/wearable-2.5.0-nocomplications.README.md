# wearable-2.5.0-nocomplications.aar

This is `com.google.android.support:wearable:2.5.0` (resolved from Maven) with the
`android/support/wearable/complications/**` classes removed from `classes.jar`.

## Why

`com.google.android.support:wearable:2.5.0` is still needed for the legacy watch-face
support classes used by the untouched `ustwo-clockwise`-based watch faces (`Home`,
`LargeHome`, `BIGChart`, `CircleWatchface`, `BaseWatchFace`) — e.g.
`android.support.wearable.watchface.*`, `android.support.wearable.view.*`.

`androidx.wear.watchface:watchface-complications-data` (pulled in transitively by
`watchface-complications-data-source`, added for the modern
`ComplicationDataSourceService` complications API) bundles its own copy of the exact
same `android.support.wearable.complications.*` classes (the legacy AIDL/Binder IPC
stubs used for talking to the system's complication host — `IComplicationManager`,
`IProviderInfoService`, etc.), as a compatibility shim.

Both artifacts are top-level (not transitive of each other), so Gradle's
`checkDuplicateClasses` task fails the build with ~30 duplicate class errors.
`packagingOptions.exclude` does NOT avoid this — that check runs before packaging, on
raw dependency jars.

Since the code that used the old `android.support.wearable.complications.*` classes
(`CustomComplicationProviderService`, `ComplicationTapBroadcastReceiver`) has been
rewritten against the new `androidx.wear.watchface.complications.datasource.*` API, we
don't need `com.google.android.support:wearable`'s copy of those classes at all — the
modern library's copy is used instead. So the fix is to strip them from the old AAR
rather than dropping the whole (still-needed-for-other-things) dependency.

## How this file was produced (reproducible)

```sh
# 1. Locate the resolved AAR in the Gradle cache (after a build has resolved it once)
SRC=$(find ~/.gradle/caches -iname "wearable-2.5.0.aar" | head -1)

# 2. Extract it
WORK=/tmp/wearable-strip
rm -rf "$WORK"; mkdir -p "$WORK/aar" "$WORK/classes"
cd "$WORK/aar" && unzip -q "$SRC"
cd "$WORK/classes" && unzip -q "$WORK/aar/classes.jar"

# 3. Remove the conflicting package
rm -rf "$WORK/classes/android/support/wearable/complications"

# 4. Repackage classes.jar, then repackage the AAR (needs a JDK's `jar` tool)
cd "$WORK/classes" && jar -cf "$WORK/classes-stripped.jar" .
cp "$WORK/classes-stripped.jar" "$WORK/aar/classes.jar"
cd "$WORK/aar" && jar -cf wearable-2.5.0-nocomplications.aar \
    AndroidManifest.xml R.txt classes.jar proguard.txt res
```

Copy the resulting `wearable-2.5.0-nocomplications.aar` here, replacing this file's
sibling of the same name.

## If upgrading `com.google.android.support:wearable` in the future

Re-run the above against the new version's resolved AAR. If Google ever removes the
legacy compat shim from `androidx.wear.watchface:watchface-complications-data`, this
stripping step can be dropped and the plain Maven coordinate restored.
