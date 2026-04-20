package com.huaying.xstz.ui.animation.list

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.huaying.xstz.ui.animation.AnimationConstants
import kotlinx.coroutines.delay

/**
 * 列表动画类型
 */
enum class ListAnimationType {
    /** 从底部滑入 */
    SLIDE_UP,

    /** 从左侧滑入 */
    SLIDE_FROM_LEFT,

    /** 从右侧滑入 */
    SLIDE_FROM_RIGHT,

    /** 缩放进入 */
    SCALE,

    /** 淡入 */
    FADE,

    /** 混合效果 */
    MIXED
}

/**
 * 列表动画配置
 */
data class ListAnimationConfig(
    val animationType: ListAnimationType = ListAnimationType.SLIDE_UP,
    val staggerDelay: Int = AnimationConstants.Duration.LIST_ITEM_STAGGER,
    val durationMillis: Int = AnimationConstants.Duration.NORMAL,
    val maxStaggerItems: Int = 20
)

/**
 * 为 LazyColumn/LazyRow 添加动画项的扩展函数
 */
inline fun <T> LazyListScope.animatedItems(
    items: List<T>,
    config: ListAnimationConfig = ListAnimationConfig(),
    noinline key: ((item: T) -> Any)? = null,
    crossinline contentType: (item: T) -> Any? = { null },
    crossinline itemContent: @Composable LazyItemScope.(item: T, index: Int, modifier: Modifier) -> Unit
) {
    items(
        count = items.size,
        key = if (key != null) { index: Int -> key(items[index]) } else null,
        contentType = { index -> contentType(items[index]) }
    ) { index ->
        val item = items[index]
        val animatedModifier = getListItemModifier(
            index = index,
            config = config
        )
        itemContent(item, index, animatedModifier)
    }
}

/**
 * 获取列表项动画修饰符
 */
@Composable
fun getListItemModifier(
    index: Int,
    config: ListAnimationConfig = ListAnimationConfig()
): Modifier {
    val delay = (index.coerceAtMost(config.maxStaggerItems)) * config.staggerDelay
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    return when (config.animationType) {
        ListAnimationType.SLIDE_UP -> slideUpModifier(visible, config.durationMillis)
        ListAnimationType.SLIDE_FROM_LEFT -> slideFromLeftModifier(visible, config.durationMillis)
        ListAnimationType.SLIDE_FROM_RIGHT -> slideFromRightModifier(visible, config.durationMillis)
        ListAnimationType.SCALE -> scaleModifier(visible, config.durationMillis)
        ListAnimationType.FADE -> fadeModifier(visible, config.durationMillis)
        ListAnimationType.MIXED -> mixedModifier(visible, config.durationMillis)
    }
}

/**
 * 从底部滑入修饰符
 */
@Composable
private fun slideUpModifier(visible: Boolean, durationMillis: Int): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, easing = AnimationConstants.Easing.Decelerate),
        label = "slide_up_alpha"
    )

    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 50f,
        animationSpec = tween(durationMillis, easing = AnimationConstants.Easing.Decelerate),
        label = "slide_up_offset"
    )

    return Modifier
        .alpha(alpha)
        .graphicsLayer { translationY = offsetY }
}

/**
 * 从左侧滑入修饰符
 */
@Composable
private fun slideFromLeftModifier(visible: Boolean, durationMillis: Int): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, easing = AnimationConstants.Easing.Decelerate),
        label = "slide_left_alpha"
    )

    val offsetX by animateFloatAsState(
        targetValue = if (visible) 0f else -100f,
        animationSpec = tween(durationMillis, easing = AnimationConstants.Easing.Decelerate),
        label = "slide_left_offset"
    )

    return Modifier
        .alpha(alpha)
        .graphicsLayer { translationX = offsetX }
}

/**
 * 从右侧滑入修饰符
 */
@Composable
private fun slideFromRightModifier(visible: Boolean, durationMillis: Int): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, easing = AnimationConstants.Easing.Decelerate),
        label = "slide_right_alpha"
    )

    val offsetX by animateFloatAsState(
        targetValue = if (visible) 0f else 100f,
        animationSpec = tween(durationMillis, easing = AnimationConstants.Easing.Decelerate),
        label = "slide_right_offset"
    )

    return Modifier
        .alpha(alpha)
        .graphicsLayer { translationX = offsetX }
}

/**
 * 缩放修饰符
 */
@Composable
private fun scaleModifier(visible: Boolean, durationMillis: Int): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis / 2),
        label = "scale_alpha"
    )

    return Modifier
        .scale(scale)
        .alpha(alpha)
}

/**
 * 淡入修饰符
 */
@Composable
private fun fadeModifier(visible: Boolean, durationMillis: Int): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, easing = AnimationConstants.Easing.Decelerate),
        label = "fade_alpha"
    )

    return Modifier.alpha(alpha)
}

/**
 * 混合效果修饰符
 */
@Composable
private fun mixedModifier(visible: Boolean, durationMillis: Int): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, easing = AnimationConstants.Easing.Decelerate),
        label = "mixed_alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "mixed_scale"
    )

    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 30f,
        animationSpec = tween(durationMillis, easing = AnimationConstants.Easing.Decelerate),
        label = "mixed_offset"
    )

    return Modifier
        .alpha(alpha)
        .scale(scale)
        .graphicsLayer { translationY = offsetY }
}

/**
 * 列表项动画包装器
 * 用于非 Lazy 列表的项动画
 */
@Composable
fun AnimatedListItem(
    index: Int,
    config: ListAnimationConfig = ListAnimationConfig(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animatedModifier = getListItemModifier(index, config)

    Box(modifier = modifier.then(animatedModifier)) {
        content()
    }
}

/**
 * 可展开列表项动画
 */
@Composable
fun ExpandableListItem(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = expandVertically(
            animationSpec = tween(300, easing = AnimationConstants.Easing.Decelerate)
        ) + fadeIn(
            animationSpec = tween(200)
        ),
        exit = shrinkVertically(
            animationSpec = tween(200, easing = AnimationConstants.Easing.Accelerate)
        ) + fadeOut(
            animationSpec = tween(150)
        )
    ) {
        content()
    }
}

/**
 * 列表刷新动画
 */
@Composable
fun ListRefreshAnimation(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isRefreshing,
        modifier = modifier,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(200)
        ) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

/**
 * 列表空状态动画
 */
@Composable
fun ListEmptyStateAnimation(
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = isEmpty,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(500)
        ) + scaleIn(
            initialScale = 0.9f,
            animationSpec = spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            )
        ),
        exit = fadeOut(
            animationSpec = tween(200)
        ) + scaleOut(
            targetScale = 0.9f
        )
    ) {
        content()
    }
}

/**
 * 预定义的列表动画配置
 */
object ListAnimationPresets {
    /** 默认配置 - 从底部滑入 */
    val Default = ListAnimationConfig()

    /** 快速动画 */
    val Fast = ListAnimationConfig(
        staggerDelay = 30,
        durationMillis = 200
    )

    /** 慢速动画 - 更强调 */
    val Slow = ListAnimationConfig(
        staggerDelay = 80,
        durationMillis = 400
    )

    /** 缩放效果 */
    val Scale = ListAnimationConfig(
        animationType = ListAnimationType.SCALE
    )

    /** 从左侧滑入 */
    val SlideFromLeft = ListAnimationConfig(
        animationType = ListAnimationType.SLIDE_FROM_LEFT
    )

    /** 混合效果 */
    val Mixed = ListAnimationConfig(
        animationType = ListAnimationType.MIXED
    )
}
