package com.videonext.floatcontrol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * 设置页里的示意图：把波轮按真实比例画一遍，并标出每个键是做什么的。
 *
 * 配色、透明度都跟着当前设置走，所以改了颜色这张图立刻变，等于所见即所得。
 */
public class WheelGuideView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WheelGuideView(Context context) {
        this(context, null);
    }

    public WheelGuideView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(1));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(1));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(sp(11));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Context context = getContext();

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float discR = Math.min(getWidth(), getHeight()) * 0.30f;
        float ringR = discR * 0.68f;
        float iconHalf = discR * 0.20f;
        float hubR = discR * 0.34f;

        int foreground = Theme.foreground(context);

        // 盘面（用当前基色与透明度，所见即所得）+ 中性描边，
        // 这样即使选了白色，在浅色页面上也能看出边界
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Theme.surface(context));
        canvas.drawCircle(cx, cy, discR, fill);
        ring.setColor(0x40000000);
        canvas.drawCircle(cx, cy, discR, ring);

        // 环上四个符号
        drawIcon(canvas, R.drawable.ic_settings, cx, cy - ringR, iconHalf, foreground);
        drawIcon(canvas, R.drawable.ic_next, cx + ringR, cy, iconHalf, foreground);
        drawIcon(canvas, R.drawable.ic_play_pause, cx, cy + ringR, iconHalf, foreground);
        drawIcon(canvas, R.drawable.ic_prev, cx - ringR, cy, iconHalf, foreground);

        // 中心键：没有图标，只有底色差
        fill.setColor(Theme.hub(context));
        canvas.drawCircle(cx, cy, hubR, fill);
        ring.setColor(0x40000000);
        canvas.drawCircle(cx, cy, hubR, ring);

        // 引线 + 标注
        int dim = 0x99000000;
        line.setColor(dim);
        text.setColor(0xFF101820);
        float gap = dp(6);

        drawLeaderUp(canvas, cx, cy - ringR - iconHalf, cy - discR - gap);
        text.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("设置", cx, cy - discR - dp(10), text);

        drawLeaderUp(canvas, cx, cy + ringR + iconHalf, cy + discR + gap);
        canvas.drawText("播放 / 暂停", cx, cy + discR + dp(22), text);

        line.setColor(dim);
        canvas.drawLine(cx - ringR - iconHalf, cy, cx - discR - gap, cy, line);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("上一首", cx - discR - dp(10), cy + dp(4), text);

        canvas.drawLine(cx + ringR + iconHalf, cy, cx + discR + gap, cy, line);
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("下一首", cx + discR + dp(10), cy + dp(4), text);

        // 中心键的说明写在圆里
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(foreground);
        text.setTextSize(sp(10));
        canvas.drawText("收起", cx, cy + dp(1), text);
        text.setTextSize(sp(8));
        canvas.drawText("长按退出", cx, cy + dp(13), text);
        text.setTextSize(sp(11));
    }

    private void drawLeaderUp(Canvas canvas, float x, float fromY, float toY) {
        line.setColor(0x99000000);
        canvas.drawLine(x, fromY, x, toY, line);
    }

    private void drawIcon(Canvas canvas, int resId, float cx, float cy, float half, int tint) {
        Drawable d = getContext().getDrawable(resId);
        if (d == null) {
            return;
        }
        d.setTint(tint);
        d.setBounds(
                Math.round(cx - half), Math.round(cy - half),
                Math.round(cx + half), Math.round(cy + half));
        d.draw(canvas);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
