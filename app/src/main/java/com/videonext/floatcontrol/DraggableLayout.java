package com.videonext.floatcontrol;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * 悬浮窗根布局：支持拖动，同时不影响子按钮的点击。
 *
 * 拖动逻辑：手指移动超过系统 touchSlop 后，拦截事件并移动窗口；
 * 若无明显移动则视为点击，交回给子按钮处理。
 */
public class DraggableLayout extends FrameLayout {

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private final int slop;

    private float downRawX;
    private float downRawY;
    private int startX;
    private int startY;
    private boolean dragging;

    public DraggableLayout(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void init(WindowManager wm, WindowManager.LayoutParams lp) {
        this.windowManager = wm;
        this.layoutParams = lp;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = ev.getRawX();
                downRawY = ev.getRawY();
                startX = layoutParams.x;
                startY = layoutParams.y;
                dragging = false;
                return false; // 先交给子按钮，候选点击
            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    float dx = ev.getRawX() - downRawX;
                    float dy = ev.getRawY() - downRawY;
                    if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                        dragging = true;
                    }
                }
                return dragging; // 开始拖动后拦截，子按钮收不到点击
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = ev.getRawX();
                downRawY = ev.getRawY();
                startX = layoutParams.x;
                startY = layoutParams.y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getRawX() - downRawX;
                float dy = ev.getRawY() - downRawY;
                if (!dragging && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    dragging = true;
                }
                if (dragging) {
                    moveTo(dx, dy);
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean wasDragging = dragging;
                dragging = false;
                return wasDragging; // 拖动后吞掉 UP，避免误触发点击
            }
            default:
                return super.onTouchEvent(ev);
        }
    }

    private void moveTo(float dx, float dy) {
        if (windowManager == null || layoutParams == null) {
            return;
        }
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int w = getWidth();
        int h = getHeight();
        int newX = clamp(startX + (int) dx, 0, Math.max(0, screenW - w));
        int newY = clamp(startY + (int) dy, 0, Math.max(0, screenH - h));
        layoutParams.x = newX;
        layoutParams.y = newY;
        try {
            windowManager.updateViewLayout(this, layoutParams);
        } catch (Throwable ignored) {
        }
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
