/*
 * (c) 2016 PicsArt, Inc.  All rights reserved.
 */

package com.picsart.studio.editor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import com.picsart.studio.util.PicsartUtils;

import java.util.ArrayList;
import java.util.List;

public class RGBConvertView extends View {

	private static final int GRID_COLOR = Color.rgb(160, 160, 160);

	private final static float CIRCLE_RADIUS_DIP = 13.f;
	private final static float MOVE_TOLERANCE = 5.f;

	private float STROKE_WIDTH = 0.5f;

	private float circleRadiusPX;
	private float offset = 0.f;

	public final static int CURVE_RED = 0;
	public final static int CURVE_GREEN = 1;
	public final static int CURVE_BLUE = 2;
	public final static int CURVE_RGB = 3;

	private SparseArray<ArrayList<Point>> curvesFuncCoordsArray;
	private ArrayList<Point> baseCurvesFuncCoords;
	private SparseArray<Paint> curvesPaintArray;
	private SparseArray<Path> curvesPathArray;
	private Path diagonalPath = new Path();
	private Paint diagonalPaint;

	private Path gridPath = new Path();
	private Paint gridPaint;

	private int currentCurve = CURVE_RGB;
	private int touchPointIndex = -1;
	private PointF touchPoint = new PointF();
	private OnValuesChangedListener onValuesChangedListener;
	private OnPointsChangedListener onPointsChangedListener;

	private int[] rgbValues = new int[256];
	private int[] rValues = new int[256];
	private int[] gValues = new int[256];
	private int[] bValues = new int[256];
	private CurvesChangedCallback curvesChangedCallback;

	private Runnable postInvalidator = new Runnable() {
		@Override
		public void run() {
			invalidate();
		}
	};

	public RGBConvertView(Context context) {
		this(context, null);
	}

	public RGBConvertView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public RGBConvertView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		initView(context);
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		return new SavedState(super.onSaveInstanceState(), this);
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		SavedState savedState = (SavedState) state;

		super.onRestoreInstanceState(savedState.getSuperState());

		curvesFuncCoordsArray = savedState.curvesFuncCoordsArray;
		generateAllChannelsPaths();
		updateAllChannelValues();
	}

	public void setOnValuesChangedListener(OnValuesChangedListener listener) {
		this.onValuesChangedListener = listener;
	}

	public void setOnPointsChangedListener(OnPointsChangedListener listener) {
		this.onPointsChangedListener = listener;
	}

	public void setTouchPointIndex(int index) {
		touchPointIndex = index;
		if (onPointsChangedListener != null) {
			boolean canDeletePoint = true;
			if (index == 0 || index == -1 || index == curvesFuncCoordsArray.get(currentCurve).size() - 1) {
				canDeletePoint = false;
			}
			onPointsChangedListener.onPointsChanged(canDeletePoint);
		}
	}

	public int getTouchPointIndex(){
		return touchPointIndex;
	}

	private void initView(Context context) {
		initParams(context);
		resetAllFuncCoords();
	}

	private void initParams(Context context) {

		STROKE_WIDTH = PicsartUtils.convertDpToPixel((int) STROKE_WIDTH, context);
		circleRadiusPX = PicsartUtils.convertDpToPixel((int) CIRCLE_RADIUS_DIP, context);
		offset = PicsartUtils.convertDpToPixel((int) offset, context);

		Paint redPaint = new Paint();
		setupPaint(redPaint);
		redPaint.setColor(Color.RED);

		Paint greenPaint = new Paint();
		setupPaint(greenPaint);
		greenPaint.setColor(Color.GREEN);

		Paint bluePaint = new Paint();
		setupPaint(bluePaint);
		bluePaint.setColor(Color.BLUE);

		Paint rgbPaint = new Paint();
		setupPaint(rgbPaint);
		rgbPaint.setColor(Color.WHITE);

		diagonalPaint = new Paint();
		setupPaint(diagonalPaint);
		diagonalPaint.setColor(GRID_COLOR);
		diagonalPaint.setStrokeWidth(diagonalPaint.getStrokeWidth() * 1.1f);

		gridPaint = new Paint();
		setupPaint(gridPaint);
		gridPaint.setColor(GRID_COLOR);
		gridPaint.setStrokeWidth(gridPaint.getStrokeWidth() * 0.7f);

		curvesPaintArray = new SparseArray<>();
		curvesPaintArray.put(CURVE_RED, redPaint);
		curvesPaintArray.put(CURVE_GREEN, greenPaint);
		curvesPaintArray.put(CURVE_BLUE, bluePaint);
		curvesPaintArray.put(CURVE_RGB, rgbPaint);

		curvesFuncCoordsArray = new SparseArray<>();
		curvesFuncCoordsArray.put(CURVE_RED, new ArrayList<Point>());
		curvesFuncCoordsArray.put(CURVE_GREEN, new ArrayList<Point>());
		curvesFuncCoordsArray.put(CURVE_BLUE, new ArrayList<Point>());
		curvesFuncCoordsArray.put(CURVE_RGB, new ArrayList<Point>());

		curvesPathArray = new SparseArray<>();
		curvesPathArray.put(CURVE_RED, new Path());
		curvesPathArray.put(CURVE_GREEN, new Path());
		curvesPathArray.put(CURVE_BLUE, new Path());
		curvesPathArray.put(CURVE_RGB, new Path());

		baseCurvesFuncCoords = new ArrayList<>();
		baseCurvesFuncCoords.add(new Point(0, 255));
		baseCurvesFuncCoords.add(new Point(255, 0));
	}

	private void setupPaint(Paint paint) {
		paint.setAntiAlias(true);
		paint.setDither(true);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeWidth(STROKE_WIDTH);
	}

	public int[] getRGBValues() {
		return rgbValues;
	}

	public int[] getRedValues() {
		return rValues;
	}

	public int[] getGreenValues() {
		return gValues;
	}

	public int[] getBlueValues() {
		return bValues;
	}

	public int getCurrentChannel() {
		return currentCurve;
	}

	public SparseArray<ArrayList<Point>> getAllChannelPoints() {
		return curvesFuncCoordsArray;
	}

	public ArrayList<Point> getCurrentChannelPoints() {
		return curvesFuncCoordsArray.get(currentCurve);
	}

	public void resetChannel(int channel) {
		ArrayList<Point> channelFuncCoords = curvesFuncCoordsArray.get(channel);
		Path path = curvesPathArray.get(channel);
		resetChannelFuncCoords(channelFuncCoords);
		float w = getWidth();
		float h = getHeight();
		if (w > 0 && h > 0) {
			float scaleX = get255ToScreenScaleX();
			float scaleY = get255ToScreenScaleY();
			generateChannelPath(channelFuncCoords, path, scaleX, scaleY, circleRadiusPX, getHeight());
		}
	}
	public void updateChannel(int channel) {
		ArrayList<Point> channelFuncCoords = curvesFuncCoordsArray.get(channel);
		Path path = curvesPathArray.get(channel);
		float w = getWidth();
		float h = getHeight();
		if (w > 0 && h > 0) {
			float scaleX = get255ToScreenScaleX();
			float scaleY = get255ToScreenScaleY();
			generateChannelPath(channelFuncCoords, path, scaleX, scaleY, circleRadiusPX, getHeight());
		}
	}

	private void resetChannelFuncCoords(ArrayList<Point> channelFuncCoords) {
		channelFuncCoords.clear();
		channelFuncCoords.add(new Point(0, 255));
		channelFuncCoords.add(new Point(255, 0));
	}

	public void resetAllFuncCoords() {
		resetChannel(CURVE_RED);
		resetChannel(CURVE_GREEN);
		resetChannel(CURVE_BLUE);
		resetChannel(CURVE_RGB);
	}

	public void setDrawChannel(int channel) {
		currentCurve = channel;
		setTouchPointIndex(-1);
	}

	private void generateChannelPath(int channel) {
		Path path = curvesPathArray.get(channel);
		ArrayList<Point> channelFuncCoords = curvesFuncCoordsArray.get(channel);
		float scaleX = get255ToScreenScaleX();
		float scaleY = get255ToScreenScaleY();
		generateChannelPath(channelFuncCoords, path, scaleX, scaleY, circleRadiusPX, getHeight());
	}

	private void generateAllChannelsPaths() {
		generateChannelPath(CURVE_RED);
		generateChannelPath(CURVE_GREEN);
		generateChannelPath(CURVE_BLUE);
		generateChannelPath(CURVE_RGB);
	}

	private void generateChannelPath(ArrayList<Point> coords, Path channelPath, float scaleX, float scaleY, float offset, float maxHeight) {
		channelPath.reset();
		if (coords.size() == 2) {
			Point point = coords.get(0);
			channelPath.moveTo(point.x * scaleX + offset, point.y * scaleY + offset);
			point = coords.get(1);
			channelPath.lineTo(point.x * scaleX + offset, point.y * scaleY + offset);
		} else {
			Point point;
			float x1, x2, y1, y2, px, py;
			float a, b, c, d;

			float dx = 0.5f;
			float startX, endX, startY, endY;

			float s1;
			float s2;

			for (int i = 0; i < coords.size() - 1; i++) {
				point = coords.get(i);
				x1 = point.x * scaleX + offset;
				y1 = point.y * scaleY + offset;

				point = coords.get(i + 1);

				x2 = point.x * scaleX + offset;
				y2 = point.y * scaleY + offset;


				if (i == 0) {
					s1 = (y2 - y1) / (x2 - x1);
				} else {
					point = coords.get(i - 1);
					px = point.x * scaleX + offset;
					py = point.y * scaleY + offset;
					s1 = (y2 - py) / (x2 - px);
				}

				if (i == coords.size() - 2) {
					s2 = (y2 - y1) / (x2 - x1);
				} else {
					point = coords.get(i + 2);
					px = point.x * scaleX + offset;
					py = point.y * scaleY + offset;
					s2 = (py - y1) / (px - x1);
				}

				a = 2.f * (y2 - y1 - ((x2 + x1) * (s2 - s1) / 2.f + s1 * (x2 - x1) - x1 * (s2 - s1)));
				a /= Math.pow((x1 - x2), 3);

				b = (s2 - s1) / (2.f * (x2 - x1)) - 3.f * a * (x1 + x2) / 2.f;
				c = s1 - x1 * (s2 - s1) / (x2 - x1) + 3.f * a * x1 * x2;
				d = (float) (y1 - a * Math.pow(x1, 3) - b * Math.pow(x1, 2) - c * x1);

				startX = x1;
				while (startX < x2) {
					endX = Math.min(startX + dx, x2);

					startY = getY(startX, a, b, c, d);
					endY = getY(endX, a, b, c, d);

					if (startY < offset) {
						startY = offset;
					} else if (startY >= (maxHeight - offset)) {
						startY = (maxHeight - offset - 1);
					}

					if (endY < offset) {
						endY = offset;
					} else if (endY >= (maxHeight - offset)) {
						endY = (maxHeight - offset - 1);
					}

					channelPath.moveTo(startX, startY);
					channelPath.lineTo(endX, endY);
					startX += dx;
				}
			}
		}
	}

	private static float getY(float x, float a, float b, float c, float d) {
		return (float) (a * Math.pow(x, 3) + b * Math.pow(x, 2) + c * x + d);
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		float x = event.getX();
		float y = event.getY();

		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				setTouchPointIndex(getTouchedPointIndex(x));
				if (touchPointIndex >= 0) {
					setTouchPoint(x, y, touchPointIndex);
					generateChannelPath(currentCurve);
					invalidate();
				} else {
					int index = addNewPoint(x, y);
					setTouchPointIndex(index);
					if (index >= 0) {
						setTouchPoint(x, y, touchPointIndex);
						generateChannelPath(currentCurve);
					}
					invalidate();
				}

				break;

			case MotionEvent.ACTION_MOVE:

				if (touchPointIndex >= 0) {
					moveTouchPoint(x, y, touchPointIndex);
					generateChannelPath(currentCurve);
					processChannelChanges(currentCurve, true);
					invalidate();
				}

				break;

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:

				if (touchPointIndex != -1) {
					switch (currentCurve) {
						case RGBConvertView.CURVE_RED:
							if (curvesChangedCallback != null) {
								curvesChangedCallback.redChange();
							}
							break;

						case RGBConvertView.CURVE_GREEN:
							if (curvesChangedCallback != null) {
								curvesChangedCallback.greenChange();
							}
							break;

						case RGBConvertView.CURVE_BLUE:
							if (curvesChangedCallback != null) {
								curvesChangedCallback.blueChange();
							}
							break;

						default:
							if (curvesChangedCallback != null) {
								curvesChangedCallback.compositeChange();
							}
					}
					if (curvesChangedCallback != null) {
						curvesChangedCallback.channelChange(currentCurve);
					}
					processChannelChanges(currentCurve, true);
				}

				break;
		}

		// on some devices (e.x. Nexus 9) instant invalidation is not working properly (PIA-6321)
		post(postInvalidator);

		return true;
	}

	private int getTouchedPointIndex(float x) {
		float w = getWidth() - 2 * circleRadiusPX;
		float scaleX = w / 255.f;

		ArrayList<Point> points = curvesFuncCoordsArray.get(currentCurve);
		for (int i = 0; i < points.size(); i++) {
			Point point = points.get(i);
			if (x >= point.x * scaleX && x <= point.x * scaleX + 2 * circleRadiusPX) {
				return i;
			}
		}
		return -1;
	}

	private void setTouchPoint(float x, float y, int index) {
		ArrayList<Point> points = curvesFuncCoordsArray.get(currentCurve);
		Point point = points.get(index);
		float scaleY = get255ToScreenScaleY();
		point.y = castValue((int) ((y - circleRadiusPX) / scaleY));
		touchPoint.x = x;
		touchPoint.y = y;
	}

	private void moveTouchPoint(float x, float y, int index) {
		float dx = x - touchPoint.x;
		float dy = y - touchPoint.y;
		float dist = dx * dx + dy * dy;
		if (dist >= MOVE_TOLERANCE) {

			ArrayList<Point> points = curvesFuncCoordsArray.get(currentCurve);
			Point point = points.get(index);

			float scaleX = get255ToScreenScaleX();
			float scaleY = get255ToScreenScaleY();

			float oldX = point.x * scaleX + circleRadiusPX;
			float oldY = point.y * scaleY + circleRadiusPX;

			if (index != 0 && index != points.size() - 1) {
				oldX += dx;
			}

			oldY += dy;

			float left = circleRadiusPX;
			float right = getWidth() - circleRadiusPX;

			float prevX = left;
			float nextX = right;

			int previousIndex = index - 1;
			int nextIndex = index + 1;
			if (previousIndex >= 0) {
				Point prevPoint = points.get(previousIndex);
				prevX = prevPoint.x * scaleX + circleRadiusPX;
				left = prevX + 2.f * circleRadiusPX;
			}

			if (nextIndex < points.size()) {
				Point nextPoint = points.get(nextIndex);
				nextX = nextPoint.x * scaleX + circleRadiusPX;
				right = nextX - 2.f * circleRadiusPX;
			}

			if (oldX < left) {
				oldX = left;
			} else if (oldX > right) {
				oldX = right;
			}

			if ((oldX <= prevX || oldX >= nextX || left >= right) && index != 0 && index != points.size() - 1) {
				oldX = (prevX + nextX) / 2.f;
			}


			point.x = castValue((int) ((oldX - circleRadiusPX) / scaleX));
			point.y = castValue((int) ((oldY - circleRadiusPX) / scaleY));


			touchPoint.x = x;
			touchPoint.y = y;

		}
	}

	private int addNewPoint(float x, float y) {
		ArrayList<Point> points = curvesFuncCoordsArray.get(currentCurve);
		//

		float scaleX = get255ToScreenScaleX();
		float scaleY = get255ToScreenScaleY();

		float w = getWidth();
		float h = getHeight();

		if (y <= circleRadiusPX) {
			y = circleRadiusPX;
		} else if (y > h - circleRadiusPX) {
			y = h - circleRadiusPX;
		}


		if (x >= 2 * circleRadiusPX && x <= w - 2 * circleRadiusPX) {

			float left, right;

			for (int i = 0; i < points.size() - 1; i++) {
				Point point = points.get(i);
				left = point.x * scaleX + 2.f * 1.1f * circleRadiusPX;

				point = points.get(i + 1);
				right = point.x * scaleX - 2.f * 1.1f * circleRadiusPX;

				if (x >= left && x <= right) {
					int x1 = castValue((int) ((x - circleRadiusPX) / scaleX));
					int y1 = castValue((int) ((y - circleRadiusPX) / scaleY));
					points.add(i + 1, new Point(x1, y1));
					return i + 1;
				} else if (x < right) {
					break;
				}
			}
		}

		return -1;
	}

	public boolean deleteTouchedPoint() {
		ArrayList<Point> points = curvesFuncCoordsArray.get(currentCurve);
		if (touchPointIndex > 0 && touchPointIndex < points.size() - 1) {
			points.remove(touchPointIndex);
			generateChannelPath(currentCurve);
			setTouchPointIndex(-1);
			return true;
		}

		return false;
	}

	private float get255ToScreenScaleX() {
		float w = getWidth() - 2 * circleRadiusPX;
		return w / 255.f;
	}

	private float get255ToScreenScaleY() {
		float h = getHeight() - 2 * circleRadiusPX;
		return h / 255.f;
	}

	private int castValue(int value) {
		if (value < 0) {
			return 0;
		} else if (value > 255) {
			return 255;
		} else return value;
	}


	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		/*Log.e("ex","oldW = "+oldw);
		Log.e("ex","W = "+w);
		if(oldw >0 && w >0){
			circleRadiusPX = com.picsart.studio.util.PicsartUtils.convertDpToPixel((int)CIRCLE_RADIUS_DIP,getContext());
			if(w>oldw){
				circleRadiusPX *= (float)w/oldw;
			}
			
		}*/

		generateAllChannelsPaths();
		updateAllChannelValues();
		setupBlackPath();
		setupGridPath(w, h);
		invalidate();
	}

	private void setupBlackPath() {
		diagonalPath.reset();
		diagonalPath.moveTo(circleRadiusPX, getHeight() - circleRadiusPX);
		diagonalPath.lineTo(getWidth() - circleRadiusPX, circleRadiusPX);
	}

	private void setupGridPath(float width, float height) {
		gridPath.reset();

		float deltaX = (width - 2.f * offset - 2.f * circleRadiusPX) / 4.f;
		float deltaY = (height - 2.f * offset - 2.f * circleRadiusPX) / 4.f;

		float y = offset + circleRadiusPX;
		float startX = offset + circleRadiusPX;
		float endX = width - offset - circleRadiusPX;

		for (int i = 0; i < 5; i++) {
			gridPath.moveTo(startX, y);
			gridPath.lineTo(endX, y);
			y += deltaY;
		}

		float x = offset + circleRadiusPX;

		float startY = offset + circleRadiusPX;
		float endY = height - offset - circleRadiusPX;

		for (int i = 0; i < 5; i++) {
			gridPath.moveTo(x, startY);
			gridPath.lineTo(x, endY);
			x += deltaX;
		}

	}


	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		canvas.drawPath(gridPath, gridPaint);

		switch (currentCurve) {
			case CURVE_RGB:
				//drawCurvePath(canvas, CURVE_RGB);
				drawCurvePath(canvas, CURVE_RED);
				drawCurvePath(canvas, CURVE_GREEN);
				drawCurvePath(canvas, CURVE_BLUE);
				break;

			case CURVE_RED:
				drawCurvePath(canvas, CURVE_RGB);
				//drawCurvePath(canvas, CURVE_RED);
				drawCurvePath(canvas, CURVE_GREEN);
				drawCurvePath(canvas, CURVE_BLUE);
				break;

			case CURVE_GREEN:
				drawCurvePath(canvas, CURVE_RGB);
				drawCurvePath(canvas, CURVE_RED);
				//drawCurvePath(canvas, CURVE_GREEN);
				drawCurvePath(canvas, CURVE_BLUE);
				break;

			case CURVE_BLUE:
				drawCurvePath(canvas, CURVE_RGB);
				drawCurvePath(canvas, CURVE_RED);
				drawCurvePath(canvas, CURVE_GREEN);
				//drawCurvePath(canvas, CURVE_BLUE);
				break;
		}

		canvas.drawPath(diagonalPath, diagonalPaint);

		drawCurvePath(canvas, currentCurve);
		drawChannelPoints(currentCurve, canvas);
	}

	private void drawCurvePath(Canvas canvas, int curveIndex) {
		Paint paint = curvesPaintArray.get(curveIndex);
		Path path = curvesPathArray.get(curveIndex);
		canvas.drawPath(path, paint);
	}

	private void drawChannelPoints(int channel, Canvas canvas) {
		float scaleX = get255ToScreenScaleX();
		float scaleY = get255ToScreenScaleY();

		Paint paint = curvesPaintArray.get(channel);
		ArrayList<Point> centers = curvesFuncCoordsArray.get(channel);

		float circleRadius = circleRadiusPX - paint.getStrokeWidth() / 2.f;
		for (int i = 0; i < centers.size(); i++) {
			Point center = centers.get(i);
			float centerX = circleRadiusPX + center.x * scaleX;
			float centerY = circleRadiusPX + center.y * scaleY;
			if (i == touchPointIndex) {
				paint.setStyle(Paint.Style.FILL_AND_STROKE);
			}
			canvas.drawCircle(centerX, centerY, circleRadius, paint);
			paint.setStyle(Paint.Style.STROKE);
		}
	}

	public void processChannelChanges(int curve) {
		processChannelChanges(curve, false);
	}

	public void processChannelChanges(int curve, boolean isManuallyChanged) {
		ArrayList<Point> points = getCurrentChannelPoints();

		int[] lookupTable;

		switch (curve) {
			case RGBConvertView.CURVE_RED:
				lookupTable = rValues;
				break;

			case RGBConvertView.CURVE_GREEN:
				lookupTable = gValues;
				break;

			case RGBConvertView.CURVE_BLUE:
				lookupTable = bValues;
				break;

			default:
				lookupTable = rgbValues;
		}

		generateChannelArray(points, lookupTable, 255);

		onValuesChangedListener.onValuesChanged(rgbValues, rValues, gValues, bValues);
	}

	private void updateAllChannelValues() {
		generateChannelArray(curvesFuncCoordsArray.get(CURVE_RED), rValues, 255);
		generateChannelArray(curvesFuncCoordsArray.get(CURVE_GREEN), gValues, 255);
		generateChannelArray(curvesFuncCoordsArray.get(CURVE_BLUE), bValues, 255);
		generateChannelArray(curvesFuncCoordsArray.get(CURVE_RGB), rgbValues, 255);

		if (onValuesChangedListener != null) {
			onValuesChangedListener.onValuesChanged(rgbValues, rValues, gValues, bValues);
		}
	}

	public interface OnValuesChangedListener {
		void onValuesChanged(int[] rgbValues, int[] rValues, int[] gValues, int[] bValues);
	}

	public interface OnPointsChangedListener {
		void onPointsChanged(boolean canDelete);
	}

	private static class SavedState extends BaseSavedState {
		private SparseArray<ArrayList<Point>> curvesFuncCoordsArray;

		public SavedState(Parcel source) {
			super(source);

			curvesFuncCoordsArray = new SparseArray<>(4);
			curvesFuncCoordsArray.put(CURVE_RED, new ArrayList<Point>());
			curvesFuncCoordsArray.put(CURVE_GREEN, new ArrayList<Point>());
			curvesFuncCoordsArray.put(CURVE_BLUE, new ArrayList<Point>());
			curvesFuncCoordsArray.put(CURVE_RGB, new ArrayList<Point>());

			source.readTypedList(curvesFuncCoordsArray.get(CURVE_RED), Point.CREATOR);
			source.readTypedList(curvesFuncCoordsArray.get(CURVE_GREEN), Point.CREATOR);
			source.readTypedList(curvesFuncCoordsArray.get(CURVE_BLUE), Point.CREATOR);
			source.readTypedList(curvesFuncCoordsArray.get(CURVE_RGB), Point.CREATOR);
		}

		public SavedState(Parcelable superState, RGBConvertView view) {
			super(superState);

			curvesFuncCoordsArray = view.curvesFuncCoordsArray;
		}

		@Override
		public void writeToParcel(@NonNull Parcel dest, int flags) {
			super.writeToParcel(dest, flags);

			dest.writeTypedList(curvesFuncCoordsArray.get(CURVE_RED));
			dest.writeTypedList(curvesFuncCoordsArray.get(CURVE_GREEN));
			dest.writeTypedList(curvesFuncCoordsArray.get(CURVE_BLUE));
			dest.writeTypedList(curvesFuncCoordsArray.get(CURVE_RGB));
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

	public static void generateChannelArray(ArrayList<Point> coords, int[] points, int maxValue) {
		if (coords.size() == 2) {
			for (int i = 0; i <= maxValue; i++) {
				points[i] = (maxValue - coords.get(0).y) + Math.round(i * (coords.get(0).y - coords.get(1).y) / maxValue);
			}
		} else {
			Point point;
			int x1, x2, y1, y2, px, py;
			float a, b, c, d;

			int startX, startY;

			float s1, s2;

			for (int i = 0; i < coords.size() - 1; i++) {
				point = coords.get(i);
				x1 = point.x;
				y1 = point.y;

				point = coords.get(i + 1);

				x2 = point.x;
				y2 = point.y;

				if (i == 0) {
					s1 = (y2 - y1) / (x2 - x1);
				} else {
					point = coords.get(i - 1);
					px = point.x;
					py = point.y;
					s1 = (y2 - py) / (x2 - px);
				}

				if (i == coords.size() - 2) {
					s2 = (y2 - y1) / (x2 - x1);
				} else {
					point = coords.get(i + 2);
					px = point.x;
					py = point.y;
					s2 = (py - y1) / (px - x1);
				}

				a = 2.f * (y2 - y1 - ((x2 + x1) * (s2 - s1) / 2.f + s1 * (x2 - x1) - x1 * (s2 - s1)));
				a /= Math.pow((x1 - x2), 3);

				b = (s2 - s1) / (2.f * (x2 - x1)) - 3.f * a * (x1 + x2) / 2.f;
				c = s1 - x1 * (s2 - s1) / (x2 - x1) + 3.f * a * x1 * x2;
				d = (float) (y1 - a * Math.pow(x1, 3) - b * Math.pow(x1, 2) - c * x1);

				startX = x1;
				while (startX < x2) {
					startY = Math.round(getY(startX, a, b, c, d));

					if (startY < 0) {
						startY = 0;
					} else if (startY >= 256) {
						startY = maxValue;
					}

					points[startX] = maxValue - startY;
					startX++;
				}
			}
			points[maxValue] = maxValue - coords.get(coords.size() - 1).y;
		}
	}

	public void setCurveChangeListener(CurvesChangedCallback curvesChangedCallback) {
		this.curvesChangedCallback = curvesChangedCallback;
	}

	public interface CurvesChangedCallback {
		void redChange();
		void greenChange();
		void blueChange();
		void compositeChange();
		void channelChange(int channel);
	}

	public void resetCurvesFuncCoordsArrayWithPoints(SparseArray<List<Point>> points){
		curvesFuncCoordsArray.clear();
		for (int i = 0; i < 4; i++) {
			ArrayList<Point> helpList = new ArrayList<>();
			for(int j=0;j<points.get(i).size();j++) {
				helpList.add(new Point(points.get(i).get(j)));
			}
			curvesFuncCoordsArray.put(i,helpList);
		}
	}

	public boolean isChanngelChanged(int channel) {
		return !curvesFuncCoordsArray.get(channel).equals(baseCurvesFuncCoords);
	}
}
