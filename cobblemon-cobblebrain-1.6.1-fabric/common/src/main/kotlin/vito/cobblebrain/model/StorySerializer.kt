package vito.cobblebrain.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

// ==========================================================
// DTOs FOR MODULAR STORAGE
// ==========================================================

data class StoryMetadataFile(
    var id: String = "",
    var name: String = "",
    var author: String = "Creator",
    var description: String = "",
    var version: String = "1.0.0",
    var activeSceneId: String = "",
    var prerequisites: StoryPrerequisites = StoryPrerequisites(),
    var sceneHeaders: MutableList<SceneHeaderData> = mutableListOf()
)

data class SceneHeaderData(
    val id: String,
    val title: String,
    val description: String = "",
    val isStartScene: Boolean = false,
    val isEndScene: Boolean = false,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 500.0,
    val height: Double = 350.0,
    val inPort: PortData = PortData(name = "In", type = PortType.INPUT),
    val outPort: PortData = PortData(name = "Out", type = PortType.OUTPUT),
    val fileName: String
)

data class StoryGlobalVarsFile(
    val variables: MutableList<StoryVariable> = mutableListOf()
)

data class StoryGlobalSceneFile(
    val sceneConnections: MutableList<ConnectionData> = mutableListOf(),
    val globalNodes: MutableList<NodeData> = mutableListOf()
)

object StorySerializer {
    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    val storageDir: File
        get() {
            val legacyDir = File("cobblebrain-ai/storypacks")
            val dir = File("cobblebrain/storypacks")
            if (!dir.exists() && legacyDir.exists() && legacyDir.isDirectory) {
                legacyDir.renameTo(dir)
            }
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Atomically writes string content to a destination file using a temporary sibling file.
     * Prevents file corruption during unexpected crashes or power failures.
     */
    fun writeTextAtomic(targetFile: File, content: String) {
        val parent = targetFile.parentFile ?: File(".")
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val tempFile = File(parent, "${targetFile.name}.${System.currentTimeMillis()}.tmp")
        tempFile.writeText(content)
        try {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Throwable) {
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Throwable) {
                targetFile.writeText(content)
                tempFile.delete()
            }
        }
    }

    /**
     * Saves a StoryProject using the modular folder structure:
     * - storypacks/<story_id>/<story_id>_metadata.json
     * - storypacks/<story_id>/<story_id>_global_vars.json
     * - storypacks/<story_id>/<story_id>_global_scene.json
     * - storypacks/<story_id>/scenes/<scene_file>.json
     * - storypacks/<story_id>/assets/
     */
    fun save(project: StoryProject, targetFolderOrFile: File? = null): File? {
        if (project.isReadOnly) {
            println("[CobbleBrain] Cannot save read-only storypack from ZIP: ${project.name}")
            return null
        }
        return try {
            val safeProjectName = project.id.trim().lowercase().replace(" ", "_")
            val packDir = targetFolderOrFile?.let { if (it.isDirectory) it else it.parentFile }
                ?: project.packDirectory
                ?: File(storageDir, safeProjectName)

            if (!packDir.exists()) {
                packDir.mkdirs()
            }

            val scenesDir = File(packDir, "scenes")
            val legacyCenasDir = File(packDir, "cenas")
            // Automatically migrate existing legacy "cenas" directory to "scenes"
            if (!scenesDir.exists() && legacyCenasDir.exists() && legacyCenasDir.isDirectory) {
                legacyCenasDir.renameTo(scenesDir)
            }
            if (!scenesDir.exists()) {
                scenesDir.mkdirs()
            }
            File(packDir, "assets").let { if (!it.exists()) it.mkdirs() }

            // 1. Deduplicate nodes across scenes and globalNodes, sanitize parentSceneId
            val registeredNodeIds = mutableSetOf<String>()
            project.scenes.forEach { scene ->
                if (scene.isLoaded) {
                    scene.nodes.removeIf { node ->
                        if (registeredNodeIds.contains(node.id)) {
                            true
                        } else if (node.parentSceneId.isNullOrBlank()) {
                            // Detached/global node: move to globalNodes!
                            if (!project.globalNodes.any { it.id == node.id }) {
                                project.globalNodes.add(node)
                            }
                            true
                        } else {
                            registeredNodeIds.add(node.id)
                            node.parentSceneId = scene.id
                            false
                        }
                    }
                }
            }

            project.globalNodes.removeIf { node ->
                if (registeredNodeIds.contains(node.id)) {
                    true
                } else {
                    registeredNodeIds.add(node.id)
                    node.parentSceneId = null
                    false
                }
            }

            // 2. Save individual loaded scenes atomically
            val sceneHeaders = mutableListOf<SceneHeaderData>()
            val writtenSceneFileNames = mutableSetOf<String>()

            project.scenes.forEach { scene ->
                val safeSceneTitle = scene.title.trim().lowercase().replace("[^a-z0-9_]+".toRegex(), "_")
                val sceneFileName = scene.sourceFileName
                    ?: "${safeSceneTitle}_${scene.id.take(8)}.json"

                writtenSceneFileNames.add(sceneFileName)
                scene.sourceFileName = sceneFileName

                if (scene.isLoaded) {
                    val sceneFile = File(scenesDir, sceneFileName)
                    val sceneJson = gson.toJson(scene)
                    writeTextAtomic(sceneFile, sceneJson)
                }

                sceneHeaders.add(
                    SceneHeaderData(
                        id = scene.id,
                        title = scene.title,
                        description = scene.description,
                        isStartScene = scene.isStartScene,
                        isEndScene = scene.isEndScene,
                        x = scene.x,
                        y = scene.y,
                        width = scene.width,
                        height = scene.height,
                        inPort = scene.inPort,
                        outPort = scene.outPort,
                        fileName = sceneFileName
                    )
                )
            }

            // Clean up obsolete scene files from deleted scenes
            scenesDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".json", ignoreCase = true) && !writtenSceneFileNames.contains(file.name)) {
                    file.delete()
                }
            }

            // Remove legacy cenas directory if now empty
            if (legacyCenasDir.exists() && legacyCenasDir.isDirectory && legacyCenasDir.listFiles().isNullOrEmpty()) {
                legacyCenasDir.delete()
            }

            // 2. Save metadata atomically
            val metadata = StoryMetadataFile(
                id = project.id,
                name = project.name,
                author = project.author,
                description = project.description,
                version = project.version,
                activeSceneId = project.activeSceneId,
                prerequisites = project.prerequisites,
                sceneHeaders = sceneHeaders
            )
            val metadataFile = File(packDir, "${safeProjectName}_metadata.json")
            writeTextAtomic(metadataFile, gson.toJson(metadata))

            // 3. Save global vars atomically
            val varsFile = File(packDir, "${safeProjectName}_global_vars.json")
            writeTextAtomic(varsFile, gson.toJson(StoryGlobalVarsFile(project.variables)))

            // 4. Save global scene / connections & global nodes atomically
            val globalSceneFile = File(packDir, "${safeProjectName}_global_scene.json")
            writeTextAtomic(globalSceneFile, gson.toJson(StoryGlobalSceneFile(project.sceneConnections, project.globalNodes)))

            project.isFolderPack = true
            project.packDirectory = packDir

            metadataFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads a StoryProject from either a modular directory or a legacy monolithic JSON file.
     * When lazy is true, scene node graphs inside scenes/ are deferred until explicitly requested.
     */
    fun load(fileOrDir: File, lazy: Boolean = false): StoryProject? {
        return try {
            if (!fileOrDir.exists()) return null

            if (fileOrDir.isDirectory) {
                loadFromDirectory(fileOrDir, lazy)
            } else if (fileOrDir.name.endsWith(".zip", ignoreCase = true)) {
                loadFromZip(fileOrDir)
            } else {
                // If the selected file is a metadata file inside a storypack directory, load the directory
                if (fileOrDir.name.endsWith("_metadata.json", ignoreCase = true) || fileOrDir.name.equals("metadata.json", ignoreCase = true)) {
                    val parent = fileOrDir.parentFile
                    if (parent != null && parent.isDirectory) {
                        return loadFromDirectory(parent, lazy)
                    }
                }

                // Legacy monolithic single-file format
                val json = fileOrDir.readText()
                val legacyProject = gson.fromJson(json, StoryProject::class.java)
                legacyProject?.scenes?.forEach { it.isLoaded = true }
                legacyProject?.isFolderPack = false
                legacyProject?.packDirectory = null
                legacyProject
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadFromDirectory(dir: File, lazy: Boolean): StoryProject? {
        val metadataFile = dir.listFiles { _, n ->
            n.endsWith("_metadata.json", ignoreCase = true) || n.equals("metadata.json", ignoreCase = true)
        }?.firstOrNull() ?: return null

        val metadata = gson.fromJson(metadataFile.readText(), StoryMetadataFile::class.java) ?: return null

        val varsFile = dir.listFiles { _, n ->
            n.endsWith("_global_vars.json", ignoreCase = true) ||
            n.equals("global_vars.json", ignoreCase = true) ||
            n.equals("variables.json", ignoreCase = true)
        }?.firstOrNull()
        val vars = if (varsFile?.exists() == true) {
            gson.fromJson(varsFile.readText(), StoryGlobalVarsFile::class.java)?.variables ?: mutableListOf()
        } else {
            mutableListOf()
        }

        val globalSceneFile = dir.listFiles { _, n ->
            n.endsWith("_global_scene.json", ignoreCase = true) ||
            n.equals("global_scene.json", ignoreCase = true)
        }?.firstOrNull()
        val globalSceneData = if (globalSceneFile?.exists() == true) {
            gson.fromJson(globalSceneFile.readText(), StoryGlobalSceneFile::class.java)
        } else null
        val connections = globalSceneData?.sceneConnections ?: mutableListOf()
        val globalNodes = globalSceneData?.globalNodes ?: mutableListOf()
        globalNodes.forEach { it.parentSceneId = null }

        val project = StoryProject(
            id = metadata.id.ifBlank { dir.name },
            name = metadata.name.ifBlank { metadata.id.ifBlank { dir.name } },
            author = metadata.author,
            description = metadata.description,
            version = metadata.version,
            activeSceneId = metadata.activeSceneId,
            prerequisites = metadata.prerequisites,
            scenes = mutableListOf(),
            sceneConnections = connections,
            globalNodes = globalNodes,
            variables = vars,
            isFolderPack = true,
            packDirectory = dir
        )

        val legacyCenasDir = File(dir, "cenas")
        val scenesDir = File(dir, "scenes")
        if (!scenesDir.exists() && legacyCenasDir.exists() && legacyCenasDir.isDirectory) {
            legacyCenasDir.renameTo(scenesDir)
        }
        val effectiveScenesDir = scenesDir.takeIf { it.exists() && it.isDirectory }
            ?: legacyCenasDir.takeIf { it.exists() && it.isDirectory }

        if (metadata.sceneHeaders.isNotEmpty()) {
            for (header in metadata.sceneHeaders) {
                val sceneFile = if (effectiveScenesDir != null) File(effectiveScenesDir, header.fileName) else null
                if (!lazy && sceneFile != null && sceneFile.exists()) {
                    val loadedScene = gson.fromJson(sceneFile.readText(), SceneData::class.java)
                    if (loadedScene != null) {
                        loadedScene.isLoaded = true
                        loadedScene.sourceFileName = header.fileName
                        project.scenes.add(loadedScene)
                        continue
                    }
                }

                // Lazy header placeholder
                val lazyScene = SceneData(
                    id = header.id,
                    title = header.title,
                    description = header.description,
                    isStartScene = header.isStartScene,
                    isEndScene = header.isEndScene,
                    x = header.x,
                    y = header.y,
                    width = header.width,
                    height = header.height,
                    inPort = header.inPort,
                    outPort = header.outPort,
                    isLoaded = false,
                    sourceFileName = header.fileName
                )
                project.scenes.add(lazyScene)
            }
        } else if (effectiveScenesDir != null) {
            // Fallback: discover scene files directly from scenes/ directory
            effectiveScenesDir.listFiles { _, n -> n.endsWith(".json", ignoreCase = true) }?.forEach { sf ->
                if (!lazy) {
                    val loadedScene = gson.fromJson(sf.readText(), SceneData::class.java)
                    if (loadedScene != null) {
                        loadedScene.isLoaded = true
                        loadedScene.sourceFileName = sf.name
                        project.scenes.add(loadedScene)
                    }
                } else {
                    val lazyScene = SceneData(
                        id = sf.nameWithoutExtension,
                        title = sf.nameWithoutExtension,
                        isLoaded = false,
                        sourceFileName = sf.name
                    )
                    project.scenes.add(lazyScene)
                }
            }
        }

        if (project.activeSceneId.isBlank()) {
            project.activeSceneId = project.scenes.firstOrNull()?.id ?: ""
        }

        // Migrate any detached nodes in loaded scenes into project.globalNodes
        for (scene in project.scenes) {
            if (scene.isLoaded) {
                val detached = scene.nodes.filter { it.parentSceneId.isNullOrBlank() }
                if (detached.isNotEmpty()) {
                    scene.nodes.removeAll(detached)
                    for (node in detached) {
                        node.parentSceneId = null
                        if (!project.globalNodes.any { it.id == node.id }) {
                            project.globalNodes.add(node)
                        }
                    }
                }
            }
        }

        return project
    }

    /**
     * Ensures that the node graph and connections for a specific SceneData are fully loaded into memory.
     */
    fun ensureSceneLoaded(project: StoryProject, scene: SceneData): Boolean {
        if (scene.isLoaded) return true

        val packDir = project.packDirectory ?: return false
        val legacyCenasDir = File(packDir, "cenas")
        val scenesDir = File(packDir, "scenes")
        if (!scenesDir.exists() && legacyCenasDir.exists() && legacyCenasDir.isDirectory) {
            legacyCenasDir.renameTo(scenesDir)
        }
        val effectiveScenesDir = scenesDir.takeIf { it.exists() && it.isDirectory }
            ?: legacyCenasDir.takeIf { it.exists() && it.isDirectory }
            ?: return false

        val candidates = mutableListOf<File>()
        scene.sourceFileName?.let { candidates.add(File(effectiveScenesDir, it)) }
        candidates.add(File(effectiveScenesDir, "${scene.id}.json"))
        val safeTitle = scene.title.trim().lowercase().replace("[^a-z0-9_]+".toRegex(), "_")
        candidates.add(File(effectiveScenesDir, "${safeTitle}.json"))

        for (file in candidates) {
            if (file.exists() && file.isFile) {
                try {
                    val loaded = gson.fromJson(file.readText(), SceneData::class.java)
                    if (loaded != null) {
                        val detached = loaded.nodes.filter { it.parentSceneId.isNullOrBlank() }
                        if (detached.isNotEmpty()) {
                            for (node in detached) {
                                node.parentSceneId = null
                                if (!project.globalNodes.any { it.id == node.id }) {
                                    project.globalNodes.add(node)
                                }
                            }
                        }
                        scene.nodes.clear()
                        scene.nodes.addAll(loaded.nodes.filter { !it.parentSceneId.isNullOrBlank() })
                        scene.connections.clear()
                        scene.connections.addAll(loaded.connections)
                        scene.isLoaded = true
                        scene.sourceFileName = file.name
                        return true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Broad scan fallback
        effectiveScenesDir.listFiles { _, n -> n.endsWith(".json", ignoreCase = true) }?.forEach { file ->
            try {
                val loaded = gson.fromJson(file.readText(), SceneData::class.java)
                if (loaded != null && (loaded.id == scene.id || loaded.title.equals(scene.title, ignoreCase = true))) {
                    val detached = loaded.nodes.filter { it.parentSceneId.isNullOrBlank() }
                    if (detached.isNotEmpty()) {
                        for (node in detached) {
                            node.parentSceneId = null
                            if (!project.globalNodes.any { it.id == node.id }) {
                                project.globalNodes.add(node)
                            }
                        }
                    }
                    scene.nodes.clear()
                    scene.nodes.addAll(loaded.nodes.filter { !it.parentSceneId.isNullOrBlank() })
                    scene.connections.clear()
                    scene.connections.addAll(loaded.connections)
                    scene.isLoaded = true
                    scene.sourceFileName = file.name
                    return true
                }
            } catch (_: Exception) {}
        }

        return false
    }

    /**
     * Ensures all scenes across the project are deserialized (e.g. before visual editing).
     */
    fun ensureAllScenesLoaded(project: StoryProject) {
        project.scenes.forEach { scene ->
            ensureSceneLoaded(project, scene)
        }
    }

    /**
     * Reads and deserializes a complete storypack directly from a ZIP file in RAM.
     * Dynamic root folder normalization is applied so ZIPs created with enclosing folders work seamlessly.
     * The stream is closed immediately via ZipFile.use to release file handles and prevent Windows file locking.
     */
    fun loadFromZip(zipFile: File): StoryProject? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val metadataEntry = entries.find { entry ->
                    val name = entry.name.replace('\\', '/')
                    !entry.isDirectory && (name.endsWith("_metadata.json", ignoreCase = true) || name.endsWith("metadata.json", ignoreCase = true))
                } ?: run {
                    println("[CobbleBrain] Invalid storypack zip (missing metadata.json): ${zipFile.name}")
                    return null
                }

                // Subfolder tolerance: extract prefix if metadata is inside an enclosing folder (e.g. "my_pack/metadata.json" -> "my_pack/")
                val normalizedMetadataPath = metadataEntry.name.replace('\\', '/')
                val rootPrefix = if (normalizedMetadataPath.contains('/')) {
                    normalizedMetadataPath.substringBeforeLast('/') + "/"
                } else {
                    ""
                }

                val metadataJson = zip.getInputStream(metadataEntry).bufferedReader().use { it.readText() }
                val metadata = gson.fromJson(metadataJson, StoryMetadataFile::class.java) ?: return null

                // 2. Global variables
                val varsEntry = entries.find { entry ->
                    val rel = entry.name.replace('\\', '/').removePrefix(rootPrefix)
                    !entry.isDirectory && (rel.endsWith("_global_vars.json", ignoreCase = true) ||
                                          rel.equals("global_vars.json", ignoreCase = true) ||
                                          rel.equals("variables.json", ignoreCase = true))
                }
                val vars = if (varsEntry != null) {
                    try {
                        val varsJson = zip.getInputStream(varsEntry).bufferedReader().use { it.readText() }
                        gson.fromJson(varsJson, StoryGlobalVarsFile::class.java)?.variables ?: mutableListOf()
                    } catch (_: Throwable) {
                        mutableListOf()
                    }
                } else {
                    mutableListOf()
                }

                // 3. Global scene & connections
                val globalSceneEntry = entries.find { entry ->
                    val rel = entry.name.replace('\\', '/').removePrefix(rootPrefix)
                    !entry.isDirectory && (rel.endsWith("_global_scene.json", ignoreCase = true) ||
                                          rel.equals("global_scene.json", ignoreCase = true))
                }
                var connections = mutableListOf<ConnectionData>()
                var globalNodes = mutableListOf<NodeData>()
                if (globalSceneEntry != null) {
                    try {
                        val gJson = zip.getInputStream(globalSceneEntry).bufferedReader().use { it.readText() }
                        val gFile = gson.fromJson(gJson, StoryGlobalSceneFile::class.java)
                        if (gFile != null) {
                            connections = gFile.sceneConnections
                            globalNodes = gFile.globalNodes
                        }
                    } catch (_: Throwable) {}
                }

                val project = StoryProject(
                    id = metadata.id.ifBlank { zipFile.nameWithoutExtension },
                    name = metadata.name.ifBlank { metadata.id.ifBlank { zipFile.nameWithoutExtension } },
                    author = metadata.author,
                    description = metadata.description,
                    version = metadata.version,
                    activeSceneId = metadata.activeSceneId,
                    prerequisites = metadata.prerequisites,
                    scenes = mutableListOf(),
                    sceneConnections = connections,
                    globalNodes = globalNodes,
                    variables = vars,
                    isFolderPack = false,
                    packDirectory = null,
                    isReadOnly = true,
                    sourceZipFile = zipFile
                )

                // 4. Load scenes (support both "scenes/" and legacy "cenas/")
                val sceneEntries = entries.filter { entry ->
                    val rel = entry.name.replace('\\', '/').removePrefix(rootPrefix)
                    !entry.isDirectory &&
                    (rel.startsWith("scenes/", ignoreCase = true) || rel.startsWith("cenas/", ignoreCase = true)) &&
                    rel.endsWith(".json", ignoreCase = true)
                }

                if (metadata.sceneHeaders.isNotEmpty()) {
                    for (header in metadata.sceneHeaders) {
                        val matchingEntry = sceneEntries.find { entry ->
                            val fileName = entry.name.replace('\\', '/').substringAfterLast('/')
                            fileName.equals(header.fileName, ignoreCase = true)
                        }
                        if (matchingEntry != null) {
                            try {
                                val sJson = zip.getInputStream(matchingEntry).bufferedReader().use { it.readText() }
                                val loadedScene = gson.fromJson(sJson, SceneData::class.java)
                                if (loadedScene != null) {
                                    loadedScene.isLoaded = true
                                    loadedScene.sourceFileName = header.fileName
                                    project.scenes.add(loadedScene)
                                    continue
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // Placeholder if not found
                        val lazyScene = SceneData(
                            id = header.id,
                            title = header.title,
                            description = header.description,
                            isStartScene = header.isStartScene,
                            isEndScene = header.isEndScene,
                            x = header.x,
                            y = header.y,
                            width = header.width,
                            height = header.height,
                            inPort = header.inPort,
                            outPort = header.outPort,
                            isLoaded = false,
                            sourceFileName = header.fileName
                        )
                        project.scenes.add(lazyScene)
                    }
                } else {
                    // Fallback: load directly from scene entries
                    for (entry in sceneEntries) {
                        val fileName = entry.name.replace('\\', '/').substringAfterLast('/')
                        try {
                            val sJson = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                            val loadedScene = gson.fromJson(sJson, SceneData::class.java)
                            if (loadedScene != null) {
                                loadedScene.isLoaded = true
                                loadedScene.sourceFileName = fileName
                                project.scenes.add(loadedScene)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                project
            }
        } catch (e: Exception) {
            println("[CobbleBrain] Failed to load storypack zip '${zipFile.name}': ${e.message}")
            null
        }
    }

    /**
     * Inspects a ZIP file quickly to extract only its metadata, closing the stream immediately.
     */
    fun peekZipMetadata(zipFile: File): StoryMetadataFile? {
        return try {
            ZipFile(zipFile).use { zip ->
                val metadataEntry = zip.entries().asSequence().find { entry ->
                    val name = entry.name.replace('\\', '/')
                    !entry.isDirectory && (name.endsWith("_metadata.json", ignoreCase = true) || name.endsWith("metadata.json", ignoreCase = true))
                } ?: return null

                val json = zip.getInputStream(metadataEntry).bufferedReader().use { it.readText() }
                gson.fromJson(json, StoryMetadataFile::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isValidStoryZip(zipFile: File): Boolean {
        return peekZipMetadata(zipFile) != null
    }

    fun loadByName(name: String, lazy: Boolean = false): StoryProject? {
        val cleanName = name.removeSuffix(".json").removeSuffix(".zip").trim()
        val folderCandidate = File(storageDir, cleanName)
        if (folderCandidate.exists() && folderCandidate.isDirectory) {
            return load(folderCandidate, lazy)
        }

        val zipCandidate = File(storageDir, "$cleanName.zip")
        if (zipCandidate.exists() && zipCandidate.isFile) {
            return load(zipCandidate, lazy)
        }

        val fileCandidate = File(storageDir, "$cleanName.json")
        if (fileCandidate.exists()) {
            return load(fileCandidate, lazy)
        }

        // Search through packs
        for (pack in listStoryPacks()) {
            if (pack.nameWithoutExtension.equals(cleanName, ignoreCase = true) || pack.name.equals(cleanName, ignoreCase = true)) {
                return load(pack, lazy)
            }
        }
        return null
    }

    fun listStoryPacks(): List<File> {
        val dir = storageDir
        val results = mutableListOf<File>()
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val hasMetadata = file.listFiles { _, n ->
                    n.endsWith("_metadata.json", ignoreCase = true) || n.equals("metadata.json", ignoreCase = true)
                }?.isNotEmpty() == true
                if (hasMetadata) {
                    results.add(file)
                }
            } else if (file.isFile && file.name.endsWith(".zip", ignoreCase = true)) {
                if (isValidStoryZip(file)) {
                    results.add(file)
                }
            } else if (file.isFile && file.name.endsWith(".json", ignoreCase = true)) {
                val name = file.name
                if (!name.endsWith("_metadata.json", ignoreCase = true) &&
                    !name.endsWith("_global_vars.json", ignoreCase = true) &&
                    !name.endsWith("_global_scene.json", ignoreCase = true)) {
                    results.add(file)
                }
            }
        }
        return results.sortedBy { it.name.lowercase() }
    }

    fun toJson(project: StoryProject): String {
        ensureAllScenesLoaded(project)
        return gson.toJson(project)
    }

    fun fromJson(json: String): StoryProject? {
        return try {
            val proj = gson.fromJson(json, StoryProject::class.java)
            proj?.scenes?.forEach { it.isLoaded = true }
            proj
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
