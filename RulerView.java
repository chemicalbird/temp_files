/*
 * (c) 2016 PicsArt, Inc.  All rights reserved.
 */

package com.picsart.studio.editor.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.picsart.studio.R;
import com.picsart.studio.util.GraphicUtils;

public class RulerView extends View {

	private static final float DEFAULT_MIN_PROGRESS = -45f;
	private static final float DEFAULT_MAX_PROGRESS = 45f;

	private static final Orientation DEFAULT_ORIENTATION = Orientation.HORIZONTAL;

	private Orientation orientation;

	private float lastTouchX, lastTouchY;

	private OnProgressChangedListener onProgressChangedListener;

	private RectF viewportRect;

	private Drawable markerDrawable;

	private float scaleWidth1;
	private float scaleHeight1;

	private float scaleWidth5;
	private float scaleHeight5;

	private float scaleWidth10;
	private float scaleHeight10;
	private float selectorHeight;

	private float pointsSize;

	private float progress;

	private boolean showPopup;

	private float rulerTextOffset;

	private float minProgress;
	private float maxProgress;
	private float selectorPad;

	private Paint rotatePanelPointPaint;
	private Paint rotatePanelPaint;
	private Paint rotatePanelTextPaint;
	private Paint rotatePanelSelectorPaint;

	public RulerView(Context context) {
		this(context, null);
	}

	public RulerView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public RulerView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		setSaveEnabled(true);

		orientation = DEFAULT_ORIENTATION;

		minProgress = DEFAULT_MIN_PROGRESS;
		maxProgress = DEFAULT_MAX_PROGRESS;

		showPopup = true;

		viewportRect = new RectF();

		markerDrawable = context.getResources().getDrawable(R.drawable.bar_indicator);
		if (markerDrawable != null) {
			markerDrawable.setBounds(-markerDrawable.getIntrinsicWidth() / 2, -markerDrawable.getIntrinsicHeight() / 2,
					markerDrawable.getIntrinsicWidth() / 2, markerDrawable.getIntrinsicHeight() / 2);
		}

		initPaints();
		initDimensions();

		if (attrs != null) {
			TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.RulerView);

			orientation = array.getInt(R.styleable.RulerView_rulerOrientation, 0) == 0 ? Orientation.HORIZONTAL : Orientation.VERTICAL;
			pointsSize = array.getDimension(R.styleable.RulerView_pointSize, 8);
			minProgress = array.getFloat(R.styleable.RulerView_minProgress, DEFAULT_MIN_PROGRESS);
			maxProgress = array.getFloat(R.styleable.RulerView_maxProgress, DEFAULT_MAX_PROGRESS);

			array.recycle();
		}
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		return new SavedState(super.onSaveInstanceState(), progress);
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		SavedState savedState = (SavedState) state;

		super.onRestoreInstanceState(savedState.getSuperState());

		progress = savedState.progress;
	}

	private void initPaints() {
		rotatePanelPaint = new Paint();
		rotatePanelPaint.setStyle(Paint.Style.STROKE);
		rotatePanelPaint.setColor(0xFFFFFFFF);
		rotatePanelPaint.setAlpha((int) (255 * 0.6f));

		rotatePanelTextPaint = new TextPaint();
		rotatePanelTextPaint.setTextSize(getResources().getDimension(R.dimen.category_title_size));
		rotatePanelTextPaint.setTextAlign(Paint.Align.CENTER);
		rotatePanelTextPaint.setColor(0xFFFFFFFF);

		rotatePanelSelectorPaint = new Paint();
		rotatePanelSelectorPaint.setStyle(Paint.Style.STROKE);
		rotatePanelSelectorPaint.setColor(0xFF00A3FF);
		rotatePanelSelectorPaint.setStrokeWidth(getResources().getDimension(R.dimen.space_3dp));
		rotatePanelSelectorPaint.setStrokeJoin(Paint.Join.ROUND);
		rotatePanelSelectorPaint.setStrokeCap(Paint.Cap.ROUND);

		rotatePanelPointPaint = new Paint();
		rotatePanelPointPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		rotatePanelPointPaint.setColor(0xFFFFFFFF);
		rotatePanelPointPaint.setStrokeWidth(getResources().getDimension(R.dimen.space_3dp));
		rotatePanelPointPaint.setStrokeJoin(Paint.Join.ROUND);
		rotatePanelPointPaint.setStrokeCap(Paint.Cap.ROUND);

	}

	private void initDimensions() {
		scaleWidth1 = getResources().getDimension(R.dimen.crop_ruler_scale_width_1);
		scaleHeight1 = getResources().getDimension(R.dimen.space_16dp);

		scaleWidth5 = getResources().getDimension(R.dimen.crop_ruler_scale_width_1);
		scaleHeight5 = getResources().getDimension(R.dimen.space_18dp);

		scaleWidth10 = getResources().getDimension(R.dimen.space_1dp);
		scaleHeight10 = getResources().getDimension(R.dimen.space_24dp);

		rulerTextOffset = getResources().getDimension(R.dimen.space_8dp);

		selectorHeight = getResources().getDimension(R.dimen.space_32dp);
		selectorPad = getResources().getDimension(R.dimen.space_8dp);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		viewportRect.set(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), h - getPaddingBottom());
	}

	public void setOnProgressChangedListener(OnProgressChangedListener onProgressChangedListener) {
		this.onProgressChangedListener = onProgressChangedListener;
	}

	public void setProgress(float progress) {
		setProgress(progress, false, true);
	}

	public void setProgress(float progress, boolean notify, boolean notifyView) {
		this.progress = progress;

		if (notify && onProgressChangedListener != null) {
			onProgressChangedListener.onProgressChanged(progress, notifyView);
		}

		invalidate();
	}

	public float getProgress() {
		return progress;
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		float x = event.getX();
		float y = event.getY();

		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				lastTouchX = x;
				lastTouchY = y;
				if (onProgressChangedListener != null) {
					onProgressChangedListener.onStoppedProgressChanging();
				}
				break;

			case MotionEvent.ACTION_MOVE:
				if (onProgressChangedListener != null) {
					if (progress != 0f || GraphicUtils.dist(lastTouchX, lastTouchY, x, y) > 20) {
						float deltaProgress = getDeltaProgress(x - lastTouchX, y - lastTouchY);

						float totalProgress = Math.min(Math.max(progress - deltaProgress, minProgress), maxProgress);

						if ((progress > 0.1f || progress < -0.1f) && (totalProgress < 0.1f && totalProgress > -0.1f)) {
							totalProgress = 0f;
						}
						setProgress(totalProgress, true, true);

						lastTouchX = x;
						lastTouchY = y;
					}
				}
				break;

			case MotionEvent.ACTION_UP:
				if (onProgressChangedListener != null) {
					onProgressChangedListener.onStoppedProgressChanging();
				}

		}

		return true;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (isOrientationHorizontal()) {
			drawHorizontally(canvas);
		} else {
			drawVertically(canvas);
		}
	}

	private void drawHorizontally(Canvas canvas) {
		rotatePanelTextPaint.setTextAlign(Paint.Align.CENTER);

		float centerX = viewportRect.centerX();
		float centerY = viewportRect.bottom - selectorHeight / 2f - selectorPad / 2f;

		final float viewportWidth = viewportRect.width();

		final int firstPoint = (int) (progress - (viewportWidth / 2) / pointsSize);

		for (int p = firstPoint; ; ++p) {
			float x = (p - progress) * pointsSize + centerX;

			if (x <= viewportWidth) {
				if (p % 10 == 0f) {
					if (p >= minProgress && p <= maxProgress) {
						if (p == 0) {
							canvas.drawPoint(x, centerY - scaleHeight10 / 2f - rulerTextOffset, rotatePanelPointPaint);
						} else {
							canvas.drawText(String.valueOf(p), x, centerY - scaleHeight10 / 2f - rulerTextOffset, rotatePanelTextPaint);
						}
					}
					rotatePanelPaint.setStrokeWidth(scaleWidth10);
					canvas.drawLine(x, centerY + scaleHeight10 / 2f, x, centerY - scaleHeight10 / 2f, rotatePanelPaint);
				} else if (p % 5 == 0) {
					rotatePanelPaint.setStrokeWidth(scaleWidth5);
					canvas.drawLine(x, centerY + scaleHeight5 / 2f, x, centerY - scaleHeight5 / 2f, rotatePanelPaint);
				} else {
					rotatePanelPaint.setStrokeWidth(scaleWidth1);
					canvas.drawLine(x, centerY + scaleHeight1 / 2f, x, centerY - scaleHeight1 / 2f, rotatePanelPaint);
				}
			} else {
				break;
			}
		}

		canvas.save();
		canvas.translate(centerX, centerY);
		canvas.drawLine(0, selectorHeight / 2f + selectorPad / 2, 0, -selectorHeight / 2f + selectorPad / 2, rotatePanelSelectorPaint);
		canvas.restore();
	}

	private void drawVertically(Canvas canvas) {
		rotatePanelTextPaint.setTextAlign(Paint.Align.RIGHT);

		float centerX = viewportRect.right - selectorHeight / 2f - selectorPad / 2;
		float centerY = viewportRect.centerY();

		final float viewportHeight = viewportRect.height();

		final int firstPoint = (int) (progress + (viewportHeight / 2) / pointsSize);

		for (int p = firstPoint; ; --p) {
			float y = (progress - p) * pointsSize + centerY;

			if (y <= viewportHeight) {
				if (p % 10 == 0) {
					if (p >= minProgress && p <= maxProgress) {
						if (p == 0) {
							canvas.drawPoint(centerX - scaleHeight10 / 2f - rulerTextOffset, y, rotatePanelPointPaint);
						} else {
							canvas.drawText(String.valueOf(p), centerX - scaleHeight10 / 2f - rulerTextOffset, y, rotatePanelTextPaint);
						}
					}

					rotatePanelPaint.setStrokeWidth(scaleWidth10);
					canvas.drawLine(centerX + scaleHeight10 / 2f, y, centerX - scaleHeight10 / 2f, y, rotatePanelPaint);
				} else if (p % 5 == 0) {
					rotatePanelPaint.setStrokeWidth(scaleWidth5);
					canvas.drawLine(centerX + scaleHeight5 / 2f, y, centerX - scaleHeight5 / 2f, y, rotatePanelPaint);
				} else {
					rotatePanelPaint.setStrokeWidth(scaleWidth1);
					canvas.drawLine(centerX + scaleHeight1 / 2f, y, centerX - scaleHeight1 / 2f, y, rotatePanelPaint);
				}
			} else {
				break;
			}
		}

		canvas.save();
		canvas.translate(centerX, centerY);
		canvas.rotate(270f);
		canvas.drawLine(0, selectorHeight / 2f + selectorPad / 2, 0, -selectorHeight / 2f + selectorPad / 2, rotatePanelSelectorPaint);
		canvas.restore();
	}

	private float getDeltaProgress(float dx, float dy) {
		if (isOrientationHorizontal()) {
			return dx / pointsSize;
		} else {
			return -dy / pointsSize;
		}
	}

	private boolean isOrientationHorizontal() {
		return orientation == Orientation.HORIZONTAL;
	}

	private enum Orientation {
		HORIZONTAL, VERTICAL
	}

	public interface OnProgressChangedListener {
		void onStartedProgressChanging();

		void onProgressChanged(float newProgress, boolean notifyView);

		void onStoppedProgressChanging();
	}

	private static class SavedState extends BaseSavedState {
		private float progress;

		public SavedState(Parcel source) {
			super(source);

			progress = source.readFloat();
		}

		public SavedState(Parcelable superState, float progress) {
			super(superState);

			this.progress = progress;
		}

		@Override
		public void writeToParcel(@NonNull Parcel dest, int flags) {
			super.writeToParcel(dest, flags);

			dest.writeFloat(progress);
		}

		public static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {
			@Override
			public SavedState createFromParcel(Parcel source) {
				return new SavedState(source);
			}

			@Override
			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}
}
