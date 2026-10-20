/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.util

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.view.View
import android.view.ViewTreeObserver
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.folder.Folder
import com.android.launcher3.graphics.PathWrapper
import com.android.launcher3.util.WindowBlurState.WINDOW_BLUR_STATE
import com.android.launcher3.views.ActivityContext
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Named

/**
 * Quickstep implementation of the helper class that creates and updates the blur drawable used
 * for folders and the homescreen popup.
 */
@ActivityContextSingleton
class QuickstepBackgroundBlurHelper
@Inject
constructor(
    private val activityContext: ActivityContext,
    @Named(WINDOW_BLUR_STATE) private val blurState: ListenableRef<Boolean>,
) : BlurBackgroundHelper() {

    private val folderBlurRadius = activityContext.asContext().resources.getDimension(
        R.dimen.folder_blur_radius
    )

    private val cornerRadius = Themes.getDialogCornerRadius(activityContext.asContext())
    private val workspaceBlurRenderNode = RenderNode("workspaceBlur")
    private val workspaceBlurRenderNodeOutline = Outline()
    private val bounds = Rect()
    private val iconBlurs = WeakHashMap<Any, BackgroundBlurDrawable>()
    private var folderOpen = false
    private var alphaListener: ViewTreeObserver.OnPreDrawListener? = null
    private val blurPref =
        LauncherPrefs.get(activityContext.asContext()).get(LauncherPrefs.FOLDER_BLUR)
    private val folderBlurDrawable: BackgroundBlurDrawable? by lazy {
        if (!isBlurEnabled()) null
        else
            activityContext.dragLayer.getViewRootImpl()
                .createBackgroundBlurDrawable()?.apply {
                    setBlurRadius(folderBlurRadius.toInt())
                    setVisible(false, false)
                }
    }

    override fun prepareToOpenFolder(folder: Folder) {
        if (!isBlurEnabled()) {
            return
        }

        val folderIcon = folder.folderIcon
        val folderNameVisibility: Int = folderIcon.folderName.visibility
        val isIconVisible = folderIcon.iconVisible

        folderIcon.setTextVisible(false)
        folderIcon.setIconVisible(false)
        setFolderOpen(true)

        val dragLayer = activityContext.dragLayer
        val canvas =
            workspaceBlurRenderNode.beginRecording(dragLayer.getWidth(), dragLayer.getHeight())
        dragLayer.draw(canvas)
        workspaceBlurRenderNode.endRecording()
        workspaceBlurRenderNode.setPosition(0, 0, dragLayer.getWidth(), dragLayer.getHeight())

        folderIcon.folderName.visibility = folderNameVisibility
        folderIcon.setIconVisible(isIconVisible)
    }

    override fun drawFolderBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        if (!isBlurEnabled()) {
            return
        }

        drawCrossWindowBlur(canvas, pathWrapper, view)
        drawWorkspaceBlur(canvas, pathWrapper?.path, view)
    }

    override fun drawIconBlur(
        canvas: Canvas,
        key: Any,
        left: Int,
        top: Int,
        radius: Int,
        cornerRadius: Float,
    ) {
        if (!isBlurEnabled() || !canvas.isHardwareAccelerated || folderOpen) return
        if (alphaListener == null) {
            alphaListener = ViewTreeObserver.OnPreDrawListener { updateIconAlphas(); true }
                .also { activityContext.dragLayer.viewTreeObserver.addOnPreDrawListener(it) }
        }
        val d = iconBlurs.getOrPut(key) {
            activityContext.dragLayer.getViewRootImpl()?.createBackgroundBlurDrawable() ?: return
        }
        if (d.setVisible(true, false) || d.bounds.isEmpty) {
            (key as? View)?.postInvalidateOnAnimation()
        }
        d.setBlurRadius(folderBlurRadius.toInt())
        d.setCornerRadius(cornerRadius)
        d.setBounds(left, top, left + 2 * radius, top + 2 * radius)
        d.alpha = blurAlpha(key)
        d.draw(canvas)
    }

    private fun drawWorkspaceBlur(canvas: Canvas, path: Path?, view: View) {
        if (!workspaceBlurRenderNode.hasDisplayList()) {
            return
        }
        workspaceBlurRenderNode.translationX = -view.left.toFloat()
        workspaceBlurRenderNode.translationY = -view.top.toFloat()

        if (path != null && !path.isEmpty) {
            workspaceBlurRenderNodeOutline.setPath(path)
            workspaceBlurRenderNodeOutline.mPath.offset(view.left.toFloat(), view.top.toFloat())
        } else {
            workspaceBlurRenderNodeOutline.setRoundRect(
                view.left,
                view.top,
                view.right,
                view.bottom,
                cornerRadius,
            )
        }
        workspaceBlurRenderNode.setOutline(workspaceBlurRenderNodeOutline)
        workspaceBlurRenderNode.setClipToOutline(true)

        workspaceBlurRenderNode.setRenderEffect(
            RenderEffect.createBlurEffect(
                folderBlurRadius,
                folderBlurRadius,
                Shader.TileMode.CLAMP
            )
        )
        canvas.drawRenderNode(workspaceBlurRenderNode)
    }

    private fun drawCrossWindowBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        val d = folderBlurDrawable ?: return

        d.setVisible(true, false)
        if (pathWrapper != null) {
            pathWrapper.bounds.roundOut(bounds)
            d.bounds = bounds
            d.setCornerRadius(pathWrapper.cornerRadius)
        } else {
            d.setBounds(0, 0, view.width, view.height)
            d.setCornerRadius(cornerRadius)
        }
        d.draw(canvas)
    }

    override fun folderCloseComplete() {
        if (workspaceBlurRenderNode.hasDisplayList()) {
            workspaceBlurRenderNode.discardDisplayList()
        }
        folderBlurDrawable?.setVisible(false, false)
        setFolderOpen(false)
    }

    private fun setFolderOpen(open: Boolean) {
        if (folderOpen == open) return
        folderOpen = open
        iconBlurs.keys.forEach { (it as? View)?.invalidate() }
    }

    private fun updateIconAlphas() {
        if (folderOpen) return
        iconBlurs.forEach { (k, d) ->
            val a = blurAlpha(k)
            if (d.alpha == 0 && a > 0) (k as? View)?.invalidate()
            d.alpha = a
        }
    }

    private fun blurAlpha(key: Any): Int {
        val v = key as? View ?: return 255
        if (!v.isShown) return 0
        val p = (activityContext as? Launcher)?.allAppsController?.progress?.coerceIn(0f, 1f) ?: 1f
        return (generateSequence(v) { it.parent as? View }.fold(p) { a, x -> a * x.alpha } * 255)
            .toInt()
    }

    override fun isBlurEnabled(): Boolean {
        return blurPref && blurState.value
    }
}
