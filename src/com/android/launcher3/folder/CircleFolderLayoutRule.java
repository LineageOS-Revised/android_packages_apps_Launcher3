/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.launcher3.folder;

public class CircleFolderLayoutRule extends ClippedFolderIconLayoutRule {

    public static final int MAX_NUM_ITEMS_IN_PREVIEW = 7;
    private static final int PERIMETER_ICONS = 6;

    @Override
    public void init(int availableSpace, float intrinsicIconSize, boolean rtl,
            int numFolderColumns) {
        super.init(availableSpace, intrinsicIconSize, rtl, numFolderColumns);
        mRadius = availableSpace * 0.32f;
    }

    @Override
    public PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        float totalScale = scaleForItem(curNumItems, 0);
        float half = mIconSize * totalScale / 2f;
        float center = mAvailableSpace / 2f;
        float transX = center - half;
        float transY = center - half;
        int n = Math.min(curNumItems, PERIMETER_ICONS);
        if (n > 1 && index >= 0 && index < n) {
            int pos = mIsRtl ? n - index : index;
            double angle = 2 * Math.PI * pos / n - Math.PI / 2;
            transX += (float) (mRadius * Math.cos(angle));
            transY += (float) (mRadius * Math.sin(angle));
        }
        if (params == null) {
            return new PreviewItemDrawingParams(transX, transY, totalScale);
        }
        params.update(transX, transY, totalScale);
        return params;
    }

    @Override
    public PreviewItemDrawingParams computeSpringAnimationItemParams(int index, int numItemsInPage,
            int page, int numFolderColumns, PreviewItemDrawingParams params) {
        return computePreviewItemDrawingParams(page == 0 ? index : -1,
                Math.min(numItemsInPage, MAX_NUM_ITEMS_IN_PREVIEW), params);
    }

    @Override
    public float scaleForItem(int numItems, int page) {
        return 0.30f * mBaselineIconScale;
    }
}
