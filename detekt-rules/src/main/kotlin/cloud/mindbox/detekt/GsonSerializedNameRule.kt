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
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.KotlinType

class GsonSerializedNameRule(config: Config) : Rule(config) {

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

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (!klass.isData()) return
        if (!klass.hasSerializedNameContract()) return
        reportMissingSerializedNameParameters(klass)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName in DIRECT_GSON_FUNCTION_NAMES) {
            checkFirstArgumentType(expression)
            checkTypeArguments(expression)
            checkClassLiteralArguments(expression)
        } else {
            checkIfIndirectGsonCall(expression)
        }
    }

    override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        super.visitObjectDeclaration(declaration)
        declaration.superTypeListEntries
            .filter { entry -> entry.typeReference?.text?.contains(TYPE_TOKEN) == true }
            .forEach { entry ->
                val type = bindingContext[BindingContext.TYPE, entry.typeReference] ?: return@forEach
                type.arguments
                    .filterNot { projection -> projection.isStarProjection }
                    .forEach { projection -> checkKotlinType(projection.type) }
            }
    }

    private fun checkIfIndirectGsonCall(expression: KtCallExpression) {
        val resolvedCall = expression.getResolvedCall(bindingContext) ?: return
        val sourceFunction = DescriptorToSourceUtils
            .descriptorToDeclaration(resolvedCall.resultingDescriptor) as? KtNamedFunction ?: return
        if (sourceFunction.typeParameters.isEmpty()) return
        if (!sourceFunction.containsDirectGsonCall()) return
        checkFirstArgumentType(expression)
        checkTypeArguments(expression)
        checkClassLiteralArguments(expression)
    }

    private fun KtNamedFunction.containsDirectGsonCall(): Boolean =
        collectDescendantsOfType<KtCallExpression>()
            .any { call -> call.calleeExpression?.text in DIRECT_GSON_FUNCTION_NAMES }

    private fun checkFirstArgumentType(expression: KtCallExpression) {
        val argument = expression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
        checkKotlinType(argument.getType(bindingContext) ?: return)
    }

    private fun checkTypeArguments(expression: KtCallExpression) {
        expression.typeArguments
            .mapNotNull { typeProjection -> typeProjection.typeReference }
            .mapNotNull { typeRef -> bindingContext[BindingContext.TYPE, typeRef] }
            .forEach { type -> checkKotlinType(type) }
    }

    private fun checkClassLiteralArguments(expression: KtCallExpression) {
        expression.valueArguments
            .mapNotNull { argument -> argument.getArgumentExpression() }
            .forEach { argument ->
                val classLiteral = when (argument) {
                    is KtDotQualifiedExpression -> argument.receiverExpression as? KtClassLiteralExpression
                    is KtClassLiteralExpression -> argument
                    else -> null
                } ?: return@forEach
                val type = classLiteral.getType(bindingContext) ?: return@forEach
                type.arguments.firstOrNull()
                    ?.takeIf { projection -> !projection.isStarProjection }
                    ?.type
                    ?.let(::checkKotlinType)
            }
    }

    private fun checkKotlinType(type: KotlinType) {
        val descriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return
        val sourceClass = DescriptorToSourceUtils.descriptorToDeclaration(descriptor) as? KtClass
        sourceClass?.let(::reportMissingSerializedNameParameters)
        type.arguments
            .filterNot { projection -> projection.isStarProjection }
            .forEach { projection -> checkKotlinType(projection.type) }
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
                checkKotlinType(type)
            }
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

    private companion object {
        private const val SERIALIZED_NAME = "SerializedName"
        private const val TYPE_TOKEN = "TypeToken"

        private val DIRECT_GSON_FUNCTION_NAMES: Set<String> = setOf(
            "fromJson",
            "fromJsonTree",
            "toJson",
            "toJsonTree",
        )
    }
}
