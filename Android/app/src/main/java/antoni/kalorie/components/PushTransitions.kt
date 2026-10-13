package antoni.kalorie.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene

// MARK: - Properties

private const val PUSH_MILLIS = 350
private const val PARALLAX_DIVISOR = 3

private val pushTransform = slideInHorizontally(tween(PUSH_MILLIS)) { it } togetherWith
    slideOutHorizontally(tween(PUSH_MILLIS)) { -it / PARALLAX_DIVISOR }

private val popTransform = slideInHorizontally(tween(PUSH_MILLIS)) { -it / PARALLAX_DIVISOR } togetherWith
    slideOutHorizontally(tween(PUSH_MILLIS)) { it }

// MARK: - Functions

fun <T : Any> pushTransitionSpec(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = { pushTransform }

fun <T : Any> popTransitionSpec(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = { popTransform }

fun <T : Any> predictivePopTransitionSpec(): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = { popTransform }
