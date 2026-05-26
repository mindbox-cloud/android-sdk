package cloud.mindbox.mobile_sdk.logger

public enum class Level(public val value: Int) {

    VERBOSE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4),
    NONE(5)
}

internal infix fun Level.isAtMost(other: Level): Boolean = value <= other.value

internal infix fun Level.isAtLeast(other: Level): Boolean = value >= other.value
