package dev.webshelf.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

class ImportManager(private val context: Context) {
    private val root = File(context.filesDir, "webapps").apply { mkdirs() }

    suspend fun importFile(uri: Uri, displayName: String, category: String): WebApp {
        val id = UUID.randomUUID().toString()
        val destination = File(root, id).apply { mkdirs() }
        val lower = displayName.lowercase()

        val entry: String
        if (lower.endsWith(".zip")) {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Couldn't open the selected ZIP file." }
                ZipInputStream(input).use { zip ->
                    var count = 0
                    var total = 0L
                    while (true) {
                        val item = zip.nextEntry ?: break
                        count++
                        require(count <= 5000) { "ZIP contains too many entries." }
                        val out = File(destination, item.name).canonicalFile
                        require(out.path.startsWith(destination.canonicalPath + File.separator) ||
                            out == destination) { "Unsafe path in ZIP: ${item.name}" }
                        if (item.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().buffered().use { output ->
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    val read = zip.read(buffer)
                                    if (read <= 0) break
                                    total += read
                                    require(total <= 200L * 1024L * 1024L) {
                                        "ZIP expands beyond the 200 MB import limit."
                                    }
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            entry = findEntryPoint(destination)
        } else {
            val safeName = File(displayName).name.ifBlank { "index.html" }
            val output = File(destination, safeName)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Couldn't open the selected HTML file." }
                output.outputStream().use { input.copyTo(it) }
            }
            entry = safeName
        }

        return WebApp(id, File(displayName).name.substringBeforeLast('.', File(displayName).name),
            category.ifBlank { "Uncategorized" }, "LOCAL", entry)
    }

    fun importFolder(treeUri: Uri, folderName: String, category: String): WebApp {
        val id = UUID.randomUUID().toString()
        val destination = File(root, id).apply { mkdirs() }
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Couldn't access the selected folder.")
        copyTree(tree, destination)
        val entry = findEntryPoint(destination)
        return WebApp(id, folderName.ifBlank { "Imported project" },
            category.ifBlank { "Uncategorized" }, "LOCAL", entry)
    }

    private fun copyTree(source: DocumentFile, target: File) {
        require(source.isDirectory) { "Please select a project folder." }
        source.listFiles().forEach { child ->
            val safeName = child.name?.let { File(it).name } ?: return@forEach
            val out = File(target, safeName).canonicalFile
            require(out.path.startsWith(target.canonicalPath + File.separator) ||
                out == target) { "Invalid file name." }
            if (child.isDirectory) {
                out.mkdirs()
                copyTree(child, out)
            } else {
                out.parentFile?.mkdirs()
                context.contentResolver.openInputStream(child.uri).use { input ->
                    requireNotNull(input) { "Couldn't read ${child.name}" }
                    out.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    private fun findEntryPoint(directory: File): String {
        val files = directory.walkTopDown().filter { it.isFile }.toList()
        val preferred = files.firstOrNull { it.name.equals("index.html", true) }
            ?: files.firstOrNull { it.extension.equals("html", true) || it.extension.equals("htm", true) }
            ?: error("No .html or .htm entry point was found.")
        return preferred.relativeTo(directory).invariantSeparatorsPath
    }

    fun deleteLocal(app: WebApp) {
        if (app.kind == "LOCAL") File(root, app.id).deleteRecursively()
    }

    fun localDirectory(app: WebApp) = File(root, app.id)
}
