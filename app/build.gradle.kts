plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.gregmatvocabpro"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"
    }

    flavorDimensions += "appMode"
    productFlavors {
        create("vocab") {
            dimension = "appMode"
            applicationId = "com.example.gregmatvocabpro"
            resValue("string", "app_name", "GregMat Vocab Pro")
        }
        create("bigbook") {
            dimension = "appMode"
            applicationId = "com.example.gregmatbigbookquiz"
            resValue("string", "app_name", "Big Book GRE Quiz")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      resValues = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // WebView asset loader: serves the bundled web app over a virtual
  // https://appassets.androidplatform.net/ origin instead of file:///
  // android_asset/, which Firebase Auth's popup/redirect sign-in flow
  // refuses to run from.
  implementation(libs.androidx.webkit)

  // Native "Sign in with Google" via Credential Manager: bypasses
  // signInWithPopup/signInWithRedirect entirely (and therefore Firebase's
  // Authorized-domains allowlist), by getting a Google ID token natively and
  // handing it to the web app for signInWithCredential().
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services.auth)
  implementation(libs.googleid)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
