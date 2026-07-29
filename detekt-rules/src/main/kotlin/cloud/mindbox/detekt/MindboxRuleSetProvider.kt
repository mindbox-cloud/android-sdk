package cloud.mindbox.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class MindboxRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "mindbox"

    override fun instance(config: Config): RuleSet {
        return RuleSet(
            id = ruleSetId,
            rules = listOf(
                GsonSerializedNameRule(config = config),
            )
        )
    }
}
