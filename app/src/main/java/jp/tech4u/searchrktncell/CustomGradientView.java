package jp.tech4u.searchrktncell;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class CustomGradientView extends View {

    private Paint paint;
    private Shader gradient;
    private Shader hairlineShader;
    private float gradientOffset = 0;
    private ValueAnimator animator;
    private int viewWidth = 0;
    private int viewHeight = 0;

    public CustomGradientView(Context context) {
        super(context);
        init();
    }

    public CustomGradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomGradientView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w; // 幅を保存
        viewHeight = h; // 高さを保存
        loadHairlineTexture();
        startAnimation();
    }
    private void loadHairlineTexture() {
        // ヘアライン風の画像を読み込み
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.hairline);
        bitmap = Bitmap.createScaledBitmap(bitmap, viewWidth, viewHeight, true);
        hairlineShader = new BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
    }
    private void updateGradient() {
        gradient = new RadialGradient(
                viewWidth/2 + gradientOffset * viewWidth,
                viewHeight/2,
                viewWidth,
                new int[]{0xFF666666, 0xFF555555, 0xFF222222, 0xFF222222}, // 灰色 → 白 → 灰色
                new float[]{0f, 0.2f, 0.8f, 1f},
                Shader.TileMode.MIRROR
        );
        Shader combinedShader = new ComposeShader(hairlineShader, gradient, PorterDuff.Mode.ADD);
        paint.setShader(combinedShader);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    private void startAnimation() {
        if (animator != null) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(-1, 1);
        animator.setDuration(1000); // 移動ミリ秒
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        //animator.setRepeatMode(ValueAnimator.RESTART);

        animator.addUpdateListener(animation -> {
            gradientOffset = (float) animation.getAnimatedValue();
            updateGradient();
        });

        animator.start();
    }

    public void pause() {
        if (animator != null) {
            animator.pause();
        }
    }

    public void resume() {
        if (animator != null) {
            animator.resume();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }
}