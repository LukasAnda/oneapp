plugins {
    alias(libs.plugins.kotlin.jvm)
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin")
        }
    }
}

tasks.register<Copy>("copyPluginInterfaces") {
    from("../app/src/main/kotlin/dev/oneapp/plugin/") {
        include("Plugin.kt", "PluginHost.kt", "PluginRegistry.kt")
    }
    into("src/main/kotlin/dev/oneapp/plugin/")
}

tasks.named("compileKotlin") {
    dependsOn("copyPluginInterfaces")
}
