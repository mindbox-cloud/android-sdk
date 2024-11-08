package cloud.mindbox.mobile_sdk.inapp.domain.models

internal sealed class Element {
    internal data class CloseButton(
        val color: String,
        val lineWidth: Double,
        val size: Size,
        val position: Position
    ) : Element() {

        internal data class Position(
            val top: Double,
            val right: Double,
            val left: Double,
            val bottom: Double,
            val kind: Kind
        ) {
            internal enum class Kind {
                PROPORTION
            }
        }

        internal data class Size(val width: Double, val height: Double, val kind: Kind) {
            internal enum class Kind {
                DP
            }
        }
    }
}
