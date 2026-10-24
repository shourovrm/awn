package app.awn.quran.feature.learn.ui

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The outlines of the mouth diagram. They are traced from "Neutral Sagittal Section.svg" by Richard
 * Wright and Dan McCloy (CC0,
 * https://commons.wikimedia.org/wiki/File:Neutral_Sagittal_Section.svg): the five shapes of the
 * source, cut to the face and thinned to about 150 points. The highlight areas are drawn over the
 * same coordinates, which are millimetres of the source drawing.
 */
internal object MouthShapes {
    /** The part of the source drawing that is shown, in its own millimetres. */
    const val LEFT = 80f
    const val TOP = 70f
    const val WIDTH = 95f
    const val HEIGHT = 145f

    /** Where each label's leader line touches the drawing, in the same millimetres. */
    val throatAnchor = Anchor(95f, 165f)
    val tongueAnchor = Anchor(125f, 150f)
    val lipsAnchor = Anchor(159f, 125f)
    val noseAnchor = Anchor(135f, 98f)
    val openMouthAnchor = Anchor(128f, 125.5f)

    data class Anchor(val x: Float, val y: Float)

    /** The head and neck, with the air passages cut out of it. */
    val head =
        pathOf(
            """
            M89,215 L91.5,215 L92.6,202.4 L92.3,172.4 L90,149 L89.7,135.1 L89.9,122.5
            L90.4,119.3 L91.6,116 L93,113.4 L96,110.1 L107.8,100.3 L109.7,95.3 L110.6,90.5
            L111.5,89.2 L124.4,84.2 L136.8,81 L139.3,81.2 L146.8,83.7 L155.2,88.2 L163.2,95.5
            L167.1,99.9 L167.4,101.6 L165.4,105 L165.5,105.6 L167.6,106 L170.7,104.5
            L172.3,102.6 L172.8,101.3 L172.6,100.2 L153.1,75.7 L151.5,73.1 L151.2,70 L80,70
            L80,215 L89,215 Z
            """,
        )

    /** The face side of [head] without the cut edges, so only the real outline is stroked. */
    val headFace =
        pathOf(
            """
            M89,215 L91.5,215 L92.6,202.4 L92.3,172.4 L90,149 L89.7,135.1 L89.9,122.5
            L90.4,119.3 L91.6,116 L93,113.4 L96,110.1 L107.8,100.3 L109.7,95.3 L110.6,90.5
            L111.5,89.2 L124.4,84.2 L136.8,81 L139.3,81.2 L146.8,83.7 L155.2,88.2 L163.2,95.5
            L167.1,99.9 L167.4,101.6 L165.4,105 L165.5,105.6 L167.6,106 L170.7,104.5
            L172.3,102.6 L172.8,101.3 L172.6,100.2 L153.1,75.7 L151.5,73.1 L151.2,70
            """,
        )

    /** The tongue, floor of the mouth, lower lip and jaw. */
    val lowerJaw =
        pathOf(
            """
            M122.2,215 L125,215 L124.6,215 L128.3,206.6 L129.9,200.8 L131.1,199.1 L147.1,192.4
            L155.8,187.9 L159.7,184.2 L160.5,182.8 L161.3,179.2 L160.9,170.3 L158.2,155
            L162.5,146.8 L162.7,145 L161.9,143.2 L159,140.8 L157.3,140.4 L156.5,141 L154.4,145.2
            L151.3,154.4 L150.5,155.8 L149.8,156.1 L149.3,154.9 L150.5,148.5 L150.4,142.3
            L150.1,141.5 L149.2,142.3 L147.8,145.7 L145.2,155 L144.8,165.6 L144.4,166.9
            L143.6,167 L142.7,166.2 L142,164.2 L142,157.7 L148.7,138.6 L145.9,135 L141.2,131.9
            L135.8,129.8 L130.5,128.9 L122.7,129.4 L117.6,130.7 L112.9,132.9 L109,135.8
            L105.7,139.3 L102.5,144.7 L99.8,151.9 L98.6,158.9 L99,168.1 L101.3,173.5
            L103.9,175.8 L106.7,176.8 L109.4,177 L112.4,176.5 L108.7,178.4 L104.9,178.9
            L101.5,178.4 L98.4,176.4 L102.7,183.4 L107.3,188.8 L111.3,191.8 L118.6,195.2
            L120.2,196.9 L120.7,198.8 L120.3,201.6 L115.8,210 L114.9,214.6 L114.1,215 L108.1,215
            L122.2,215 Z
            """,
        )

    /** The palate, soft palate, uvula and upper lip. */
    val palate =
        pathOf(
            """
            M151.3,137 L152.2,134.8 L152.9,122.7 L154,135.9 L156.2,137 L158,137 L159.8,135.9
            L160.9,134.2 L161,130.8 L159.4,124.4 L158.4,117 L158.6,114.4 L160.8,109.9
            L160.3,108.8 L155.8,109.4 L105.7,120.7 L102.4,122 L95.5,125.8 L92.1,128.9
            L90.4,131.6 L90,138.1 L90.6,139.3 L91.5,139.4 L95.2,137.4 L107.1,128.4 L111.3,125.9
            L128.2,122.3 L139.3,121.8 L141.6,122.5 L145.9,128.2 L149.6,129.7 L150.5,135.7
            L151.3,137 Z
            """,
        )

    /** The windpipe and gullet at the bottom of the drawing. */
    val windpipe =
        pathOf(
            """
            M98.2,215 L97.5,215 L97.8,209.7 L100.6,203.8 L101.8,200 L101.2,197.4 L99.7,196.1
            L98.2,196.4 L96,199.6 L93.9,204.2 L93.3,207.5 L93.2,215 L98.2,215 Z
            """,
        )

    /** The throat: the air passage behind the tongue. */
    val throatArea =
        pathOf(
            """
            M89.9,139 L95.2,139.2 L100.6,141.5 L100.6,147 L99.8,151.9 L98.6,158.9 L99,168.1
            L101.3,173.5 L98.4,176.4 L102.7,183.4 L107.3,188.8 L111.3,191.8 L113,193 L92.6,193
            L92.3,172.4 L90,149 Z
            """,
        )

    /** The tongue. */
    val tongueArea =
        pathOf(
            """
            M142,157.7 L148.7,138.6 L145.9,135 L141.2,131.9 L135.8,129.8 L130.5,128.9
            L122.7,129.4 L117.6,130.7 L112.9,132.9 L109,135.8 L105.7,139.3 L102.5,144.7
            L99.8,151.9 L98.6,158.9 L99,168.1 L101.3,173.5 L103.9,175.8 L106.7,176.8 L109.4,177
            L112.4,176.5 L120,172 L130,167 L138,165 L142,164.2 Z
            """,
        )

    /** Both lips. */
    val lipsArea =
        pathOf(
            """
            M158.4,111 L160.6,110 L158.6,114.4 L158.4,117 L159.4,124.4 L161,130.8 L160.9,134.2
            L158,137 L156.2,137 L155,130 L156,120 Z
            M162.5,146.8 L162.7,145 L161.9,143.2 L159,140.8 L157.3,140.4 L156.5,141 L154.4,145.2
            L151.3,154.4 L155,158 L158.2,155 Z
            """,
        )

    /** The nasal passage above the palate. */
    val noseArea =
        pathOf(
            """
            M90.4,131.6 L89.9,122.5 L90.4,119.3 L91.6,116 L93,113.4 L96,110.1 L107.8,100.3
            L109.7,95.3 L110.6,90.5 L111.5,89.2 L124.4,84.2 L136.8,81 L146.8,83.7 L155.2,88.2
            L163.2,95.5 L167.1,99.9 L167.4,101.6 L165.4,105 L160.8,109.9 L160.3,108.8
            L155.8,109.4 L105.7,120.7 L95.5,125.8 L92.1,128.9 Z
            """,
        )

    /** The empty space of the mouth between the palate and the tongue. */
    val openMouthArea =
        pathOf(
            """
            M91.5,139.4 L95.2,137.4 L107.1,128.4 L111.3,125.9 L128.2,122.3 L139.3,121.8
            L141.6,122.5 L145.9,128.2 L149.6,129.7 L150.5,135.7 L151.3,137 L148.7,138.6
            L145.9,135 L141.2,131.9 L135.8,129.8 L130.5,128.9 L122.7,129.4 L117.6,130.7
            L112.9,132.9 L109,135.8 L105.7,139.3 L100.5,141.5 L95.2,139.5 Z
            """,
        )

    /** The epiglottis, the one line of the source that is not a closed shape. */
    val epiglottis =
        pathOf(
            """
            M101.2,205.7 L107,204 L111.7,203.3 L117.7,203.5 L115.3,204.3 L109.3,205.4 L101.2,205.7
            """,
        )

    private fun pathOf(data: String): Path = PathParser().parsePathString(data).toPath()
}
