package cloud.mindbox.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class UnmonitoredGsonWrapperRule(config: Config) : Rule(config) {

    override val issue: Issue = Issue(
        id = "UnmonitoredGsonWrapper",
        severity = Severity.Warning,
        description = "Function wraps a Gson call but is not monitored by GsonMissingSerializedName. " +
            "Add its name to the monitored list in GsonSerializedNameRule.",
        debt = Debt.FIVE_MINS
    )

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        val name = function.name ?: return
        if (function.hasModifier(KtTokens.PRIVATE_KEYWORD)) return
        if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
        if (function.typeParameters.isEmpty()) return
        if (name in MONITORED_GSON_FUNCTION_NAMES) return
        if (!function.containsDirectGsonCall()) return
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.atName(function),
                message = "'$name' calls Gson internally but is not monitored by GsonMissingSerializedName. " +
                    "Add '$name' to GsonSerializedNameRule.MONITORED_FUNCTION_NAMES " +
                    "(detekt-rules/src/main/kotlin/cloud/mindbox/detekt/GsonSerializedNameRule.kt) " +
                    "so that types passed to '$name' are checked for @SerializedName."
            )
        )
    }

    private fun KtNamedFunction.containsDirectGsonCall(): Boolean {
        return collectDescendantsOfType<KtCallExpression>()
            .any { call -> call.calleeExpression?.text in GSON_BASE_FUNCTION_NAMES }
    }

    private companion object {
        private val MONITORED_GSON_FUNCTION_NAMES: Set<String>
            get() = GsonSerializedNameRule.MONITORED_FUNCTION_NAMES

        private val GSON_BASE_FUNCTION_NAMES: Set<String> = setOf(
            "fromJson",
            "toJson",
            "toJsonTree",
            "fromJsonTree",
        )
    }
}
