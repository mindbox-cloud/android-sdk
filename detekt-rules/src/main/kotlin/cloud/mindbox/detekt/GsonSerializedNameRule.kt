package cloud.mindbox.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.KotlinType

class GsonSerializedNameRule(
    config: Config,
    private val projectGsonClassNameProvider: ProjectGsonClassNameProvider = ProjectGsonClassNameProvider()
) : Rule(config) {

    override val issue: Issue = Issue(
        id = "GsonMissingSerializedName",
        severity = Severity.Defect,
        description = "Gson-serialized Kotlin data class constructor properties must declare @SerializedName.",
        debt = Debt.FIVE_MINS
    )

    private val reportedParameterKeys: MutableSet<String> = mutableSetOf()
    private val checkedClasses: MutableSet<String> = mutableSetOf()

    override fun preVisit(root: KtFile) {
        reportedParameterKeys.clear()
    }

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        checkGsonTypeReferences(file)
    }

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (!klass.isData()) return
        val className = klass.name ?: return
        if (!klass.hasSerializedNameContract() && className !in findProjectGsonClassNames(klass.containingKtFile)) return
        reportMissingSerializedNameParameters(klass)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text !in MONITORED_FUNCTION_NAMES) return
        checkFirstArgumentType(expression)
        checkTypeArguments(expression)
        checkClassLiteralArguments(expression)
    }

    override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        super.visitObjectDeclaration(declaration)
        declaration.superTypeListEntries
            .filter { superTypeEntry -> superTypeEntry.text.contains(TYPE_TOKEN) }
            .flatMap { superTypeEntry -> extractModelNamesFromTypeText(superTypeEntry.text, declaration.containingKtFile) }
            .forEach { className -> findClassByName(declaration.containingKtFile, className)?.let(::reportMissingSerializedNameParameters) }
    }

    private fun checkFirstArgumentType(expression: KtCallExpression) {
        val argument = expression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
        checkKotlinType(type = argument.getType(bindingContext), source = expression)
    }

    private fun checkTypeArguments(expression: KtCallExpression) {
        expression.typeArguments
            .mapNotNull { typeProjection -> typeProjection.typeReference }
            .forEach { typeReference -> checkTypeReference(typeReference, expression) }
    }

    private fun checkClassLiteralArguments(expression: KtCallExpression) {
        expression.valueArguments
            .mapNotNull { argument -> argument.getArgumentExpression() }
            .forEach { argument ->
                val classLiteral = when (argument) {
                    is KtDotQualifiedExpression -> argument.receiverExpression as? KtClassLiteralExpression
                    is KtClassLiteralExpression -> argument
                    else -> null
                }
                classLiteral?.let { literal -> checkKotlinType(type = literal.getType(bindingContext), source = expression) }
            }
    }

    private fun checkTypeReference(typeReference: KtTypeReference, source: KtCallExpression) {
        checkKotlinType(type = bindingContext[BindingContext.TYPE, typeReference], source = source)
    }

    private fun checkGsonTypeReferences(file: KtFile) {
        val aliases = file.extractTypeAliases()
        val dataClasses = file.collectDescendantsOfType<KtClass>()
            .filter { klass -> klass.isData() }
            .associateBy { klass -> klass.name.orEmpty() }
        val referencedTypeTexts = (
            TYPE_TOKEN_PATTERN.findAll(file.text).map { match -> match.groupValues[1] } +
                GSON_GENERIC_CALL_PATTERN.findAll(file.text).map { match -> match.groupValues[1] } +
                GSON_CLASS_LITERAL_PATTERN.findAll(file.text).map { match -> match.groupValues[1] }
            ).toList()
        referencedTypeTexts
            .flatMap { typeText -> extractModelNamesFromTypeText(typeText, aliases) }
            .mapNotNull { className -> dataClasses[className] }
            .forEach(::reportMissingSerializedNameParameters)
    }

    private fun findProjectGsonClassNames(file: KtFile): Set<String> {
        return projectGsonClassNameProvider.findProjectGsonClassNames(file)
    }

    private fun checkKotlinType(type: KotlinType?, source: KtCallExpression) {
        type ?: return
        val descriptor = type.constructor.declarationDescriptor as? ClassDescriptor
        val sourceClass = descriptor?.let { DescriptorToSourceUtils.descriptorToDeclaration(it) } as? KtClass
        sourceClass?.let(::reportMissingSerializedNameParameters)
        type.arguments
            .filterNot { projection -> projection.isStarProjection }
            .forEach { projection -> checkKotlinType(type = projection.type, source = source) }
    }

    private fun reportMissingSerializedNameParameters(klass: KtClass) {
        if (!klass.isData()) return
        val qualifiedName = klass.fqName?.asString() ?: return
        if (!checkedClasses.add(qualifiedName)) return
        klass.primaryConstructorParameters
            .filter { parameter -> parameter.valOrVarKeyword != null }
            .forEach { parameter ->
                if (!parameter.hasSerializedNameAnnotation()) reportParameter(parameter, klass)
                val typeRef = parameter.typeReference ?: return@forEach
                val type = bindingContext[BindingContext.TYPE, typeRef] ?: return@forEach
                checkKotlinTypeTransitively(type)
            }
    }

    private fun checkKotlinTypeTransitively(type: KotlinType) {
        val descriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return
        val sourceClass = DescriptorToSourceUtils.descriptorToDeclaration(descriptor) as? KtClass
        sourceClass?.let(::reportMissingSerializedNameParameters)
        type.arguments
            .filterNot { projection -> projection.isStarProjection }
            .forEach { projection -> checkKotlinTypeTransitively(projection.type) }
    }

    private fun reportParameter(parameter: KtParameter, klass: KtClass) {
        val key = "${parameter.containingFile.name}:${parameter.textOffset}"
        if (!reportedParameterKeys.add(key)) return
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(parameter),
                message = "${klass.name}.${parameter.name} must declare @SerializedName."
            )
        )
    }

    private fun KtClass.hasSerializedNameContract(): Boolean {
        return primaryConstructorParameters.any { parameter -> parameter.hasSerializedNameAnnotation() }
    }

    private fun KtParameter.hasSerializedNameAnnotation(): Boolean {
        return annotationEntries.any { annotationEntry ->
            annotationEntry.shortName?.asString() == SERIALIZED_NAME
        }
    }

    private fun KtFile.extractTypeAliases(): Map<String, String> {
        val psiAliases = declarations
            .filterIsInstance<KtTypeAlias>()
            .associate { alias -> alias.name.orEmpty() to alias.getTypeReference()?.text.orEmpty() }
        val textAliases = TYPE_ALIAS_PATTERN.findAll(text)
            .associate { match -> match.groupValues[1] to match.groupValues[2].trim() }
        return psiAliases + textAliases
    }

    private fun extractModelNamesFromTypeText(typeText: String, file: KtFile): Set<String> {
        val aliases = file.extractTypeAliases()
        return extractModelNamesFromTypeText(typeText, aliases)
    }

    private fun extractModelNamesFromTypeText(typeText: String, aliases: Map<String, String>): Set<String> {
        val normalizedText = aliases.entries
            .sortedByDescending { alias -> alias.key.length }
            .fold(typeText) { currentText, alias ->
                currentText.replace(Regex("\\b${Regex.escape(alias.key)}\\b"), alias.value)
        }
        return MODEL_NAME_PATTERN
            .findAll(normalizedText)
            .map { match -> match.value.substringAfterLast('.') }
            .filter { className -> className !in IGNORED_TYPE_NAMES }
            .filter { className -> className.firstOrNull()?.isUpperCase() == true }
            .toSet()
    }

    private fun findClassByName(file: KtFile, className: String): KtClass? {
        return file.collectDescendantsOfType<KtClass>()
            .firstOrNull { klass -> klass.name == className }
    }

    internal companion object {
        private const val SERIALIZED_NAME = "SerializedName"
        private const val TYPE_TOKEN = "TypeToken"

        // To add a new Gson wrapper: just add its name here.
        // UnmonitoredGsonWrapperRule references this same set to stay in sync.
        internal val MONITORED_FUNCTION_NAMES: Set<String> = setOf(
            "convertJsonToBody",
            "fromJson",
            "fromJsonTyped",
            "operationBodyJson",
            "toJson",
            "toJsonTyped",
        )

        private val GSON_CLASS_LITERAL_PATTERN: Regex = Regex(
            "\\b(?:fromJson|convertJsonToBody)\\s*\\([^\\n)]*?([A-Z][A-Za-z0-9_]*)::class\\.java"
        )
        private val GSON_GENERIC_CALL_PATTERN: Regex = Regex(
            "\\b(?:fromJson|fromJsonTyped|toJsonTyped|operationBodyJson)\\s*<\\s*([^>]+)>"
        )
        private val MODEL_NAME_PATTERN: Regex = Regex("[A-Za-z_][A-Za-z0-9_.]*")
        private val TYPE_ALIAS_PATTERN: Regex = Regex("typealias\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^\\n]+)")
        private val TYPE_TOKEN_PATTERN: Regex = Regex("TypeToken\\s*<\\s*([^>]+)>")

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
