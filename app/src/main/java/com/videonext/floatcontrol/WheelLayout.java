package com.videonext.floatcontrol;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * 波轮布局：第一个子 View 放在正中，其后四个依次放在上、右、下、左。
 *
 * 容器是正方形，子 View 各自带固定尺寸的 LayoutParams；环上按钮的位置按
 * 「(容器边长 - 按钮边长) / 2」算半径，所以按钮大小一变，环会自动收放。
 */
public class WheelLayout extends ViewGroup {

    public WheelLayout(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = MeasureSpec.getSize(widthMeasureSpec);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            int childW = getChildMeasureSpec(widthMeasureSpec, 0, lp.width);
            int childH = getChildMeasureSpec(heightMeasureSpec, 0, lp.height);
            child.measure(childW, childH);
        }
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int size = getWidth();
        int mid = size / 2;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            int cx;
            int cy;
            if (i == 0) {
                cx = mid;
                cy = mid;
            } else {
                int radius = (size - Math.max(w, h)) / 2;
                if (i == 1) {
                    cx = mid;
                    cy = mid - radius;
                } else if (i == 2) {
                    cx = mid + radius;
                    cy = mid;
                } else if (i == 3) {
                    cx = mid;
                    cy = mid + radius;
                } else {
                    cx = mid - radius;
                    cy = mid;
                }
            }
            child.layout(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        }
    }
}
