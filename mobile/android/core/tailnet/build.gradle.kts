import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.lpm.android.library)
    alias(libs.plugins.lpm.hilt)
}

android {
    ndkVersion = libs.versions.ndk.get()
    defaultConfig.consumerProguardFiles("consumer-rules.pro")
}

/**
 * Builds lpm Link's built-in Tailscale node (`tailnet/android` at the repo root)
 * with `gomobile bind` and unpacks what it makes: the Java glue as a jar and
 * libgojni.so per ABI. 64-bit ABIs only; a 32-bit phone runs without the node.
 */
@CacheableTask
abstract class GomobileBind @Inject constructor(
    private val exec: ExecOperations,
    private val files: FileSystemOperations,
    private val archives: ArchiveOperations
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val targets: Property<String>

    @get:Input
    abstract val minSdk: Property<Int>

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:Internal
    abstract val module: DirectoryProperty

    @get:Internal
    abstract val sdk: DirectoryProperty

    @get:Internal
    abstract val ndk: DirectoryProperty

    @get:LocalState
    abstract val work: DirectoryProperty

    @get:OutputFile
    abstract val classes: RegularFileProperty

    @get:OutputDirectory
    abstract val jniLibs: DirectoryProperty

    @TaskAction
    fun bind() {
        val go = findGo() ?: throw GradleException(
            "lpm Link's built-in Tailscale needs Go to build. Install it with: brew install go"
        )
        val work = work.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val bin = File(work, "bin")
        val module = module.get().asFile
        // The tools at the versions tailnet/android's go.mod pins.
        exec.exec {
            workingDir(module)
            commandLine(
                go,
                "build",
                "-o",
                bin.path + File.separator,
                "golang.org/x/mobile/cmd/gobind",
                "golang.org/x/mobile/cmd/gomobile"
            )
        }
        val aar = File(work, "lpmtailnet.aar")
        val javaBin = File(System.getProperty("java.home"), "bin")
        exec.exec {
            workingDir(module)
            executable(File(bin, "gomobile").path)
            args(
                "bind",
                "-target=${targets.get()}",
                "-androidapi",
                minSdk.get().toString(),
                "-trimpath",
                "-ldflags=-s -w",
                "-o",
                aar.path,
                "."
            )
            // gomobile finds gobind, go and javac on PATH.
            environment(
                "PATH",
                listOf(bin, File(go).parentFile, javaBin)
                    .joinToString(File.pathSeparator) { it.path } +
                    File.pathSeparator + System.getenv("PATH").orEmpty()
            )
            environment("ANDROID_HOME", sdk.get().asFile.path)
            environment("ANDROID_NDK_HOME", ndk.get().asFile.path)
        }
        files.sync {
            from(archives.zipTree(aar)) {
                include("jni/**")
                eachFile { path = path.removePrefix("jni/") }
            }
            includeEmptyDirs = false
            into(jniLibs)
        }
        files.copy {
            from(archives.zipTree(aar)) { include("classes.jar") }
            rename { classes.get().asFile.name }
            into(classes.get().asFile.parentFile)
        }
    }

    private fun findGo(): String? {
        val names = listOf("go", "go.exe")
        val dirs = System.getenv("PATH").orEmpty().split(File.pathSeparator) +
            listOfNotNull(System.getenv("GOROOT")?.let { "$it/bin" }) +
            listOf("/opt/homebrew/bin", "/usr/local/go/bin", "/usr/local/bin")
        return dirs.asSequence()
            .flatMap { dir -> names.asSequence().map { File(dir, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.path
    }
}

val tailnet: File = rootDir.resolve("../../tailnet")

val bindTailnet = tasks.register<GomobileBind>("bindTailnet") {
    sources.from(
        fileTree(tailnet) {
            include("go.mod", "go.sum", "node/**", "android/**")
            exclude("**/*_test.go")
        }
    )
    targets.set("android/arm64,android/amd64")
    minSdk.set(provider { android.defaultConfig.minSdk })
    ndkVersion.set(android.ndkVersion)
    module.set(tailnet.resolve("android"))
    sdk.set(androidComponents.sdkComponents.sdkDirectory)
    ndk.set(androidComponents.sdkComponents.ndkDirectory)
    work.set(layout.buildDirectory.dir("gomobile"))
    classes.set(layout.buildDirectory.file("generated/tailnet/lpmtailnet.jar"))
    jniLibs.set(layout.buildDirectory.dir("generated/tailnet/jniLibs"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(bindTailnet, GomobileBind::jniLibs)
    }
}

dependencies {
    api(projects.core.data)
    implementation(files(bindTailnet.flatMap { it.classes }))
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.serialization.json)
}
