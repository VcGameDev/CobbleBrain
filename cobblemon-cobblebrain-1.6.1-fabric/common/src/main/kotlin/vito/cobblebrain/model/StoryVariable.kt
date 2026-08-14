package vito.cobblebrain.model

enum class VariableType {
    BOOLEAN,
    NUMBER,
    STRING,
    LIST
}

enum class VariableScope {
    GLOBAL,
    SCENE_LOCAL
}

data class StoryVariable(
    var id: String = "var_nova",
    var name: String = "var_nova",
    var type: VariableType = VariableType.STRING,
    var defaultValue: String = "",
    var scope: VariableScope = VariableScope.GLOBAL,
    var sceneId: String? = null
) {
    fun parseTypedDefaultValue(): Any {
        return when (type) {
            VariableType.BOOLEAN -> defaultValue.equals("true", ignoreCase = true)
            VariableType.NUMBER -> defaultValue.toDoubleOrNull() ?: 0.0
            VariableType.STRING -> defaultValue
            VariableType.LIST -> {
                if (defaultValue.isBlank()) {
                    mutableListOf<String>()
                } else {
                    defaultValue.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                }
            }
        }
    }
}
