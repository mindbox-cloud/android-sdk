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
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class RuntimeTypeAdapterSubtypeWithoutLabelRule(config: Config) : Rule(config) {

    override val issue: Issue = Issue(
        id = "RuntimeTypeAdapterSubtypeWithoutLabel",
        severity = Severity.Defect,
        description = "registerSubtype(Class) takes its JSON label from Class.getSimpleName(), which R8 " +
            "renames in a minified build.",
        debt = Debt.FIVE_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text != REGISTER_SUBTYPE) return
        if (!expression.isOneArgumentOverload()) return
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "registerSubtype(Class) labels the subtype with its simple class name, which changes " +
                    "under obfuscation. Pass the label explicitly: registerSubtype(Type::class.java, LABEL)."
            )
        )
    }

    // Silent without type resolution, like UnjustifiedReflection: CI runs the type-resolved tasks, and an
    // argument-count guess would flag any one-argument registerSubtype on any receiver.
    private fun KtCallExpression.isOneArgumentOverload(): Boolean {
        if (bindingContext == BindingContext.EMPTY) return false
        val descriptor = getResolvedCall(bindingContext)?.resultingDescriptor ?: return false
        val owner = descriptor.containingDeclaration as? ClassDescriptor ?: return false
        return owner.fqNameSafe.asString() == RUNTIME_TYPE_ADAPTER_FACTORY && descriptor.valueParameters.size == 1
    }

    private companion object {
        private const val REGISTER_SUBTYPE = "registerSubtype"
        private const val RUNTIME_TYPE_ADAPTER_FACTORY = "cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory"
    }
}
