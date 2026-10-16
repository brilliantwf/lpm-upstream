package cx.lpm.mobile.buildlogic

import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

/**
 * Fails when two modules define the same string resource with different text.
 * Android merges every module's resources into one namespace, so one of the two
 * screens would silently show the other's string.
 */
@CacheableTask
abstract class StringConflictsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stringFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val definitions = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        val root = rootDirectory.get().asFile
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        stringFiles.files.sorted().forEach { file ->
            val nodes = parser.parse(file).documentElement.childNodes
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as? Element ?: continue
                if (element.tagName != "string" && element.tagName != "plurals") continue
                val key = "${file.parentFile.name}/${element.getAttribute("name")}"
                val text = element.textContent.trim().replace(Regex("\\s+"), " ")
                definitions.getOrPut(key) { mutableMapOf() }.getOrPut(text) { mutableListOf() }
                    .add(file.relativeTo(root).path)
            }
        }
        val conflicts = definitions.filterValues { it.size > 1 }
        val lines = conflicts.flatMap { (key, texts) ->
            listOf(key) + texts.map { (text, files) -> "  ${files.joinToString()}: $text" }
        }
        report.get().asFile.writeText(lines.joinToString("\n"))
        if (conflicts.isNotEmpty()) {
            throw GradleException(
                "String resources defined with different text in different modules " +
                    "(rename one):\n" + lines.joinToString("\n")
            )
        }
    }
}
