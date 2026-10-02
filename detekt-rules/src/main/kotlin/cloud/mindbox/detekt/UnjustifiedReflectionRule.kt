package cloud.mindbox.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor

/**
 * Reflective lookups R8 can't trace, matched by resolved FQ name — needs type resolution, silent without it.
 * Covers both calls (`clazz.getDeclaredField("x")`) and Kotlin's property form (`clazz.declaredFields`).
 * The "justification" is a convention: detekt only sees the @Suppress, not the comment next to it.
 */
class UnjustifiedReflectionRule(config: Config) : Rule(config) {

    override val issue: Issue = Issue(
        id = "UnjustifiedReflection",
        severity = Severity.Defect,
        description = "Reflective lookups are invisible to R8: the target can be renamed or removed in a " +
            "minified build unless something keeps it.",
        debt = Debt.TEN_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (bindingContext == BindingContext.EMPTY) return
        val fqName = expression.getResolvedCall(bindingContext)?.resultingDescriptor?.fqNameSafe?.asString() ?: return
        if (fqName in REFLECTIVE_CALLS) report(expression, fqName)
    }

    // A call's callee is also a name reference, so only synthetic Java properties are handled here —
    // everything else already went through visitCallExpression.
    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        super.visitReferenceExpression(expression)
        if (bindingContext == BindingContext.EMPTY) return
        if (expression !is KtNameReferenceExpression) return
        val property = expression.getResolvedCall(bindingContext)?.resultingDescriptor as? SyntheticJavaPropertyDescriptor
            ?: return
        val fqName = property.getMethod.fqNameSafe.asString()
        if (fqName in REFLECTIVE_CALLS) report(expression, fqName)
    }

    private fun report(element: KtElement, fqName: String) {
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(element),
                message = "$fqName resolves code by name at runtime, which R8 can't see. Make sure the target " +
                    "survives minification (a keep rule, or a class declared in the manifest), then mark the call " +
                    "with @Suppress(\"UnjustifiedReflection\") and a comment saying why it's safe."
            )
        )
    }

    private companion object {
        private val REFLECTIVE_CALLS: Set<String> = setOf(
            "java.lang.Class.forName",
            "java.lang.Class.getDeclaredMethod",
            "java.lang.Class.getDeclaredMethods",
            "java.lang.Class.getDeclaredField",
            "java.lang.Class.getDeclaredFields",
            "java.lang.Class.getDeclaredConstructor",
            "java.lang.Class.getDeclaredConstructors",
            "java.lang.Class.getMethod",
            "java.lang.Class.getField",
            "java.lang.ClassLoader.loadClass",
            "java.util.ServiceLoader.load",
        )
    }
}
