package vito.cobblebrain.social

object DialogueFilter {
    val THINK_TAG_REGEX = Regex("""(?i)<think>[\s\S]*?(?:</think>|$)""")
    val MARKDOWN_FENCE_REGEX = Regex("""```[a-zA-Z0-9_-]*""")
    val LIST_BULLET_REGEX = Regex("""^\s*(?:[-*•+]|\d+\.)\s*""")
    val ACTION_TAG_REGEX = Regex("""#([A-Za-z0-9_.'♀♂ -]+?):([A-Za-z0-9+-]+)(?::([A-Za-z0-9_.'♀♂-]+))?""")
    val SCORE_TAG_REGEX = Regex("""\s*#SCORE:\s*[+-]?\d+""", RegexOption.IGNORE_CASE)
    val FLAG_REGEX = Regex("""\[FLAG:\s*([A-Z_,\s]+)\]""", RegexOption.IGNORE_CASE)
    val HEADER_CLEANUP_REGEX = Regex(
        """^\s*\[?\s*(DIALOGUE FORMAT|CANON DIALOGUE FORMAT|FRIENDSHIP FORMAT|MEMORY FORMAT|ACTION FORMAT|GUARANTEED CATCH FORMAT|RESUME FORMAT|QUEST SYSTEM|QUEST COMPLETED|GENERAL RULES|TRAITS AND QUIRKS FORMAT|TRAIT FORMAT|QUIRK FORMAT|STAGE 1 FOREGROUND INSTRUCTIONS|STAGE 2 BACKGROUND STATE RESOLUTION|OUTPUT RULES|OUTPUT FORMAT)\s*]?\s*:?\s*""",
        RegexOption.IGNORE_CASE
    )

    val MEMORY_REGEX = Regex("""&MEMORY:([^:\n|]+):([^|\n]+)\|([^|\n;&%#!=]+)""", RegexOption.IGNORE_CASE)
    val REPLACE_TRAIT_REGEX = Regex("""(?:^|[\s|;])&REPLACE_TRAIT:([^:\n|]+):([^->\n;|]+)->([^|\n;&%#!=]+)""", RegexOption.IGNORE_CASE)
    val REPLACE_QUIRK_REGEX = Regex("""(?:^|[\s|;])&REPLACE_QUIRK:([^:\n|]+):([^->\n;|]+)->([^|\n;&%#!=]+)""", RegexOption.IGNORE_CASE)
    val TRAIT_REGEX = Regex("""(?:^|[\s|;])&TRAIT:([^:\n|]+):([^|\n;&%#!=]+)""", RegexOption.IGNORE_CASE)
    val QUIRK_REGEX = Regex("""(?:^|[\s|;])&QUIRK:([^:\n|]+):([^|\n;&%#!=]+)""", RegexOption.IGNORE_CASE)

    val MULTI_SPACE_REGEX = Regex("""[ \t]+""")
    val PUNCTUATION_SPACING_REGEX = Regex("""\s+([,.:!?])""")

    val RESERVED_SYSTEM_KEYWORDS = setOf(
        "format",
        "rules",
        "rule",
        "note",
        "notes",
        "stage",
        "stage 1",
        "stage 2",
        "dialogue",
        "dialogue format",
        "canon dialogue format",
        "friendship",
        "friendship format",
        "memory",
        "memory format",
        "action",
        "action format",
        "guaranteed catch",
        "guaranteed catch format",
        "resume",
        "resume format",
        "quest",
        "quest system",
        "general rules",
        "output",
        "output rules",
        "output format",
        "separator",
        "response language",
        "warning",
        "error",
        "traits and quirks",
        "traits and quirks format",
        "trait",
        "quirk"
    )

    fun isErrorResponse(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("!Error", ignoreCase = true) ||
               trimmed.startsWith("Error", ignoreCase = true) ||
               trimmed.startsWith("Erro", ignoreCase = true) ||
               trimmed.startsWith("HTTP Error", ignoreCase = true) ||
               (trimmed.startsWith("HTTP ", ignoreCase = true) && trimmed.contains("Error", ignoreCase = true)) ||
               trimmed.startsWith("[CobbleBrain Error]", ignoreCase = true)
    }

    fun isNoPokemonHeard(text: String): Boolean {
        val clean = text.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        return clean.equals("NO POK HEARD", ignoreCase = true) ||
               clean.equals("NO_POK_HEARD", ignoreCase = true) ||
               clean.startsWith("NO POK HEARD", ignoreCase = true) ||
               clean.contains("NO POK HEARD", ignoreCase = true) ||
               clean.equals("No Pokémon heard what you said", ignoreCase = true) ||
               clean.equals("No Pokemon heard what you said", ignoreCase = true) ||
               clean.contains("No Pokémon heard what you said", ignoreCase = true) ||
               clean.contains("No Pokemon heard what you said", ignoreCase = true) ||
               clean.contains("Nenhum Pokémon ouviu o que você disse", ignoreCase = true) ||
               clean.contains("Nenhum Pokemon ouviu o que voce disse", ignoreCase = true)
    }

    fun isSystemNarrationHallucination(text: String): Boolean {
        val clean = text.lowercase().trim()
        return clean.contains("está se irritando") ||
               clean.contains("esta se irritando") ||
               clean.contains("está hostil com") ||
               clean.contains("esta hostil com") ||
               clean.contains("is getting irritated") ||
               clean.contains("is hostile with")
    }

    fun isReservedSystemKeyword(name: String): Boolean {
        val clean = name.trim().lowercase()
        if (clean.startsWith("!error") || clean.startsWith("error") || clean.startsWith("http error")) return false
        return clean in RESERVED_SYSTEM_KEYWORDS
    }

    fun sanitizeRawResponse(raw: String): String {
        return raw
            .replace(THINK_TAG_REGEX, "")
            .replace(MARKDOWN_FENCE_REGEX, "")
            .trim()
    }

    fun stripListPrefix(line: String): String {
        return line.replace(LIST_BULLET_REGEX, "").trim()
    }

    fun cleanSpeechText(line: String): String {
        if (isErrorResponse(line)) return line.trim()
        return line
            .replace(ACTION_TAG_REGEX, "")
            .replace(SCORE_TAG_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .replace(PUNCTUATION_SPACING_REGEX, "$1")
            .trim()
    }

    fun isPromptArtifact(line: String): Boolean {
        if (isErrorResponse(line)) return false
        val trimmed = line.trim()
        val normalized = trimmed.lowercase()

        if (normalized.startsWith("---") ||
            normalized.startsWith("separator") ||
            normalized.startsWith("response language") ||
            normalized.startsWith("[flag") ||
            normalized.startsWith("flag:") ||
            normalized.startsWith("!resume") ||
            normalized.startsWith("##") ||
            normalized.startsWith("==")
        ) {
            return true
        }

        if (":" in trimmed) {
            val potentialSpeaker = trimmed.substringBefore(":").trim()
            if (isReservedSystemKeyword(potentialSpeaker)) {
                return true
            }
        }

        val matchingKeyword = RESERVED_SYSTEM_KEYWORDS.firstOrNull { kw ->
            normalized.startsWith("$kw:") || normalized.startsWith("[$kw")
        }
        return matchingKeyword != null
    }
}
