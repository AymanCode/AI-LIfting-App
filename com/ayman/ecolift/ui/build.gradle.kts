plugins {
    id("androidx.compose.ui") version "1.4.3"
}

android {
    namespace = "com.ayman.ecolift.ui"
}

dependencies {
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation("androidx.navigation:navigation-compose:2.5.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}
