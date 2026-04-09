plugins {
    id("androidx.room")
}

android {
    namespace = "com.ayman.ecolift.data"
}

dependencies {
    implementation("androidx.room:room-runtime:2.5.2")
    ksp("androidx.room:room-compiler:2.5.2")
    implementation("androidx.room:room-ktx:2.5.2")

    testImplementation("androidx.room:room-testing:2.5.2")
}
