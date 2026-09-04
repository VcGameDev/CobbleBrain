package vito.cobblebrain.engine

object StoryCommandSecurity {

    /**
     * Set of administrative, server control, moderation, and permission escalation commands.
     * Includes vanilla level 3-4 commands and popular permission/sudo mod commands.
     */
    val BLOCKED_ADMIN_COMMANDS: Set<String> = setOf(
        // Server lifecycle & administration
        "stop",
        "restart",
        "save-off",
        "save-on",
        "save-all",
        "reload",
        "setidletimeout",
        "publish",
        "debug",
        "perf",
        "jfr",

        // Permissions & Operator Control
        "op",
        "deop",

        // Moderation & Player Access
        "ban",
        "ban-ip",
        "banlist",
        "pardon",
        "pardon-ip",
        "kick",
        "whitelist",

        // Data, chunk loading & console
        "datapack",
        "forceload",
        "defaultgamemode",
        "rcon",

        // Permission mods & forced elevation
        "lp",
        "luckperms",
        "pex",
        "permissions",
        "sudo"
    )

    /**
     * Recursively and iteratively parses a command line to extract all command identifiers,
     * fully unwrapping chained `/execute ... run execute ... run <subcommand>` constructs.
     */
    fun extractCommandNames(rawCommandLine: String): List<String> {
        var current = rawCommandLine.trim().removePrefix("/")
        if (current.isBlank()) return emptyList()

        val extracted = mutableListOf<String>()

        while (current.isNotBlank()) {
            val parts = current.split(Regex("\\s+"), limit = 2)
            val firstToken = parts[0]
            val baseName = extractBaseCommandName(firstToken)

            if (baseName.isNotBlank()) {
                extracted.add(baseName)
            }

            // Only /execute chains subcommands using 'run <subcommand>'
            if (baseName == "execute" && parts.size > 1) {
                val runIdx = findRunSubcommandIndex(parts[1])
                if (runIdx != -1) {
                    current = parts[1].substring(runIdx).trim().removePrefix("/")
                    continue
                }
            }

            // Not an execute chain or reached terminal command
            break
        }

        return extracted
    }

    /**
     * Strips namespace prefix if present (e.g. "minecraft:stop" -> "stop", "luckperms:lp" -> "lp").
     */
    fun extractBaseCommandName(token: String): String {
        val clean = token.trim().lowercase()
        return if (clean.contains(':')) {
            clean.substringAfterLast(':')
        } else {
            clean
        }
    }

    /**
     * Finds the index of the subcommand following the keyword "run " in an execute clause.
     */
    private fun findRunSubcommandIndex(clause: String): Int {
        val regex = Regex("(?:^|\\s)run\\s+", RegexOption.IGNORE_CASE)
        val match = regex.find(clause) ?: return -1
        return match.range.last + 1
    }

    /**
     * Checks if a command line contains any blocked admin command.
     * Returns the name of the first blocked command found, or null if safe.
     */
    fun findBlockedAdminCommand(rawCommandLine: String): String? {
        val commands = extractCommandNames(rawCommandLine)
        for (cmd in commands) {
            if (BLOCKED_ADMIN_COMMANDS.contains(cmd)) {
                return cmd
            }
        }
        return null
    }

    /**
     * Returns true if the command line is permitted, false if blocked.
     */
    fun isCommandAllowed(rawCommandLine: String): Boolean {
        return findBlockedAdminCommand(rawCommandLine) == null
    }
}
