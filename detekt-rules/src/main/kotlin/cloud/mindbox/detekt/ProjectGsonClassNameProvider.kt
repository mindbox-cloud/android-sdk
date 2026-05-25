package cloud.mindbox.detekt

import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class ProjectGsonClassNameProvider {

    private val gsonClassNamesBySourceRoot: MutableMap<String, Set<String>> = mutableMapOf()

    fun findProjectGsonClassNames(file: KtFile): Set<String> {
        val sourceRoot: File = file.findSourceRoot() ?: return emptySet()
        return gsonClassNamesBySourceRoot.getOrPut(sourceRoot.absolutePath) {
            sourceRoot.walkTopDown()
                .filter { sourceFile: File -> sourceFile.isFile && sourceFile.extension == KOTLIN_EXTENSION }
                .map { sourceFile: File -> sourceFile.readText() }
                .let(::extractProjectGsonClassNames)
        }
    }

    internal fun extractProjectGsonClassNames(fileTexts: Sequence<String>): Set<String> {
        val texts: List<String> = fileTexts.toList()
        val aliases: Map<String, String> = texts
            .flatMap { text: String ->
                TYPE_ALIAS_PATTERN.findAll(text).map { match: MatchResult ->
                    match.groupValues[1] to match.groupValues[2].trim()
                }
            }
            .toMap()
        val directNames: MutableSet<String> = texts
            .flatMap { text: String -> extractSerializedTypeTexts(text) }
            .flatMap { typeText: String -> extractModelNamesFromTypeText(typeText, aliases) }
            .toMutableSet()
        val fieldTypeMap: Map<String, Set<String>> = buildDataClassFieldTypeMap(texts, aliases)
        var changed = true
        while (changed) {
            changed = false
            for (name in directNames.toSet()) {
                fieldTypeMap[name]?.forEach { fieldType: String ->
                    if (directNames.add(fieldType)) changed = true
                }
            }
        }
        return directNames
    }

    private fun extractSerializedTypeTexts(text: String): List<String> {
        val explicitlySerializedTypes: Sequence<String> =
            TYPE_TOKEN_PATTERN.findAll(text).map { match: MatchResult -> match.groupValues[1] } +
                GSON_GENERIC_CALL_PATTERN.findAll(text).map { match: MatchResult -> match.groupValues[1] } +
                GSON_CLASS_LITERAL_PATTERN.findAll(text).map { match: MatchResult -> match.groupValues[1] } +
                TO_JSON_CONSTRUCTOR_PATTERN.findAll(text).map { match: MatchResult -> match.groupValues[1] } +
                extractToJsonThisClassNames(text).asSequence()
        val variableTypes: Map<String, String> = extractTypedVariableNames(text)
        val toJsonVariableTypes: Sequence<String> = TO_JSON_VARIABLE_PATTERN.findAll(text)
            .mapNotNull { match: MatchResult -> variableTypes[match.groupValues[1]] }
        return (explicitlySerializedTypes + toJsonVariableTypes).toList()
    }

    private fun extractToJsonThisClassNames(text: String): List<String> {
        return TO_JSON_THIS_PATTERN.findAll(text).mapNotNull { match: MatchResult ->
            val textBefore: String = text.substring(0, match.range.first)
            PRECEDING_CLASS_NAME_PATTERN.findAll(textBefore).lastOrNull()?.groupValues?.get(1)
        }.toList()
    }

    private fun buildDataClassFieldTypeMap(
        texts: List<String>,
        aliases: Map<String, String>,
    ): Map<String, Set<String>> {
        val result: MutableMap<String, MutableSet<String>> = mutableMapOf()
        for (text in texts) {
            var searchPos = 0
            while (true) {
                val headerMatch: MatchResult = DATA_CLASS_HEADER_PATTERN.find(text, searchPos) ?: break
                val className: String = headerMatch.groupValues[1]
                val afterHeader: Int = headerMatch.range.last + 1
                val parenStart: Int = text.indexOf('(', afterHeader)
                if (parenStart < 0 || parenStart - afterHeader > 100) {
                    searchPos = afterHeader
                    continue
                }
                val constructorBody: String? = extractMatchingParenContent(text, parenStart)
                if (constructorBody != null) {
                    CONSTRUCTOR_FIELD_LINE_PATTERN.findAll(constructorBody)
                        .flatMap { match: MatchResult ->
                            extractModelNamesFromTypeText(match.groupValues[1], aliases)
                        }
                        .forEach { fieldType: String ->
                            result.getOrPut(className) { mutableSetOf() }.add(fieldType)
                        }
                }
                searchPos = afterHeader
            }
        }
        return result
    }

    private fun extractMatchingParenContent(text: String, openParenPos: Int): String? {
        var depth = 1
        var i = openParenPos + 1
        while (i < text.length && depth > 0) {
            when (text[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            i++
        }
        return if (depth == 0) text.substring(openParenPos + 1, i - 1) else null
    }

    private fun extractTypedVariableNames(text: String): Map<String, String> {
        val propertyTypes: Sequence<Pair<String, String>> = VARIABLE_DECLARATION_TYPE_PATTERN.findAll(text)
            .map { match: MatchResult -> match.groupValues[1] to match.groupValues[2] }
        val functionParameterTypes: Sequence<Pair<String, String>> = FUNCTION_PARAMETERS_PATTERN.findAll(text)
            .flatMap { match: MatchResult ->
                PARAMETER_DECLARATION_TYPE_PATTERN.findAll(match.groupValues[1])
                    .map { parameterMatch: MatchResult ->
                        parameterMatch.groupValues[1] to parameterMatch.groupValues[2]
                    }
            }
        return (propertyTypes + functionParameterTypes).toMap()
    }

    private fun extractModelNamesFromTypeText(typeText: String, aliases: Map<String, String>): Set<String> {
        val normalizedText: String = aliases.entries
            .sortedByDescending { alias: Map.Entry<String, String> -> alias.key.length }
            .fold(typeText) { currentText: String, alias: Map.Entry<String, String> ->
                currentText.replace(Regex("\\b${Regex.escape(alias.key)}\\b"), alias.value)
            }
        return MODEL_NAME_PATTERN
            .findAll(normalizedText)
            .map { match: MatchResult -> match.value.substringAfterLast('.') }
            .filter { className: String -> className !in IGNORED_TYPE_NAMES }
            .filter { className: String -> className.firstOrNull()?.isUpperCase() == true }
            .toSet()
    }

    private fun KtFile.findSourceRoot(): File? {
        val path: String = virtualFile?.path ?: return null
        val sourceRootPath: String = SOURCE_ROOT_MARKERS
            .firstNotNullOfOrNull { marker: String ->
                path.substringBefore(marker, missingDelimiterValue = "")
                    .takeIf { rootPrefix: String -> rootPrefix.isNotEmpty() }
                    ?.let { rootPrefix: String -> rootPrefix + MAIN_SOURCE_SET }
            } ?: return null
        return File(sourceRootPath)
    }

    private companion object {
        private const val KOTLIN_EXTENSION = "kt"
        private const val MAIN_SOURCE_SET = "/src/main"

        private val GSON_CLASS_LITERAL_PATTERN: Regex = Regex(
            "\\b(?:fromJson|convertJsonToBody)\\s*\\([^\\n)]*?([A-Z][A-Za-z0-9_]*)::class\\.java"
        )
        private val GSON_GENERIC_CALL_PATTERN: Regex = Regex(
            "\\b(?:fromJson|fromJsonTyped|toJsonTyped|operationBodyJson)\\s*<\\s*([^>]+)>"
        )
        private val MODEL_NAME_PATTERN: Regex = Regex("[A-Za-z_][A-Za-z0-9_.]*")
        private val SOURCE_ROOT_MARKERS: List<String> = listOf("/src/main/java/", "/src/main/kotlin/")
        private val TO_JSON_CONSTRUCTOR_PATTERN: Regex = Regex("\\btoJson\\s*\\(\\s*([A-Z][A-Za-z0-9_]*)\\s*\\(")
        private val TO_JSON_VARIABLE_PATTERN: Regex = Regex("\\btoJson\\s*\\(\\s*([a-zA-Z_][A-Za-z0-9_]*)\\s*(?:,|\\))")
        private val TYPE_ALIAS_PATTERN: Regex = Regex("typealias\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^\\n]+)")
        private val TYPE_TOKEN_PATTERN: Regex = Regex("TypeToken\\s*<\\s*([^>]+)>")
        private val FUNCTION_PARAMETERS_PATTERN: Regex = Regex("\\bfun\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(([^)]*)\\)")
        private val PARAMETER_DECLARATION_TYPE_PATTERN: Regex = Regex(
            "(?:^|,)\\s*([a-zA-Z_][A-Za-z0-9_]*)\\s*:\\s*([A-Z][A-Za-z0-9_.]*(?:<[^>\\n]+>)?)"
        )
        private val VARIABLE_DECLARATION_TYPE_PATTERN: Regex = Regex(
            "\\b(?:val|var)\\s+([a-zA-Z_][A-Za-z0-9_]*)\\s*:\\s*([A-Z][A-Za-z0-9_.]*(?:<[^>\\n]+>)?)"
        )
        private val DATA_CLASS_HEADER_PATTERN: Regex = Regex("data\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)")
        private val CONSTRUCTOR_FIELD_LINE_PATTERN: Regex = Regex(
            "(?:val|var)\\s+[a-zA-Z_][A-Za-z0-9_]*\\s*:\\s*([^\\n=]+)"
        )
        private val TO_JSON_THIS_PATTERN: Regex = Regex("\\btoJson\\s*\\(\\s*this\\s*[,)]")
        private val PRECEDING_CLASS_NAME_PATTERN: Regex = Regex("\\bclass\\s+([A-Z][A-Za-z0-9_]*)")

        private val IGNORED_TYPE_NAMES: Set<String> = setOf(
            "Any",
            "Array",
            "Boolean",
            "Byte",
            "Char",
            "Collection",
            "Double",
            "Float",
            "HashMap",
            "HashSet",
            "Int",
            "Iterable",
            "List",
            "Long",
            "Map",
            "MutableList",
            "MutableMap",
            "MutableSet",
            "Number",
            "Pair",
            "Set",
            "Short",
            "String",
            "TypeToken",
            "Unit",
        )
    }
}
