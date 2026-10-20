package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef

/**
 * How well the user knew an ayah, graded in one tap after reciting or revealing it. :app maps these
 * onto the revision schedule in :feature:learn: Easy to Easy, Good to Good, Hard to Hard and Forgot
 * to Again.
 */
enum class AyahGrade {
    Easy,
    Good,
    Hard,
    Forgot,
}

/**
 * The ayah that was just finished and waits for a grade. [suggested] is the grade the recitation
 * earned by itself and has already been sent to the revision schedule; it is null when the user
 * revealed words, because then only the user can say how well the ayah went.
 */
data class GradePrompt(val ayah: AyahRef, val suggested: AyahGrade?)
