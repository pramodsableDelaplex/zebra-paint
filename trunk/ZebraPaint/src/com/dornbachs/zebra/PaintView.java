/*
 * Copyright (C) 2010 Peter Dornbach.
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

package com.dornbachs.zebra;

import java.util.Arrays;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class PaintView extends View {
	public static final int MESSAGE_PROGRESS = 1;
	public static final int MESSAGE_DONE = 2;

	public interface LifecycleListener {
		// After this method it is allowed to load resources.
		public void onPreparedToLoad();
	}

	public PaintView(Context context, AttributeSet attrs) {
		super(context, attrs);
		_paint = new Paint();
	}

	public PaintView(Context context) {
		this(context, null);
	}

	public synchronized void setLifecycleListener(LifecycleListener l) {
		_lifecycleListener = l;
	}

	public void loadFromResource(int resourceId, Handler handler) {
		// Proportion of progress in various places.
		// PROGRESS_DECODE + PROGRESS_RESIZE + PROGRESS_PER_SCAN
		// must be equal  to 100.
		final int PROGRESS_DECODE = 10;
		final int PROGRESS_RESIZE = 10;
		final int PROGRESS_PER_SCAN = 80;

		int w = 0;
		int h = 0;
		synchronized (this) {
			w = _width;
			h = _height;
		}
		final int n = w * h;

		// Initialize the progress and send it.
		int progress = 0;
		sendProgress(handler, progress);

		// Load the bitmap from the resource
		Bitmap originalBitmap = BitmapFactory.decodeResource(
				getContext().getResources(), resourceId);
		progress += PROGRESS_DECODE;
		sendProgress(handler, progress);

		// Resize so that it matches our paint size.
		Bitmap resizedBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		DrawUtils.convertSizeClip(originalBitmap, resizedBitmap);
		progress += PROGRESS_RESIZE;
		sendProgress(handler, progress);

		// Scan through the bitmap. We create the "outline" bitmap that is
		// completely black and has the alpha channel set only. We also
		// create the "mask" that we will use later when filling.
		Bitmap outlineBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		byte[] paintMask = new byte[n];
		{
			int p = 0;
			int pixels[] = new int[n];
			resizedBitmap.getPixels(pixels, 0, w, 0, 0, w, h);
			for (int y2 = 0; y2 < PROGRESS_PER_SCAN; y2++) {
				final int yStart = y2 * h / PROGRESS_PER_SCAN;
				final int yEnd = (y2 + 1) * h / PROGRESS_PER_SCAN;
				for (int y = yStart; y < yEnd; y++) {
					for (int x = 0; x < w; x++) {
						int alpha = 255 - DrawUtils.brightness(pixels[p]);
						paintMask[p] = (alpha < ALPHA_TRESHOLD ? (byte) 1 : (byte) 0);
						pixels[p] = alpha << 24;
						p++;
					}
				}
				progress++;
				sendProgress(handler, progress);
			}
			outlineBitmap.setPixels(pixels, 0, w, 0, 0, w, h);
		}

		// Initialize the rest.
		Bitmap paintedBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		paintedBitmap.eraseColor(Color.WHITE);
		byte[] workingMask = new byte[n];
		int[] pixels = new int[n];
		Arrays.fill(pixels, Color.WHITE);

		// Commit our changes. So far we have only worked on local variables
		// so we only synchronize now.
		synchronized (this) {
			_outlineBitmap = outlineBitmap;
			_paintedBitmap = paintedBitmap;
			_paintMask = paintMask;
			_workingMask = workingMask;
			_pixels = pixels;
		}
		Message m = Message.obtain(handler, MESSAGE_DONE);
		handler.sendMessage(m);
	}

	public synchronized void setPaintColor(int color) {
		_color = color;
		_paint.setColor(color);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		synchronized (this) {
			if (_width == 0 || _height == 0) {
				_width = w;
				_height = h;
				if (_lifecycleListener != null) {
					_lifecycleListener.onPreparedToLoad();
				}
			}
		}
	}

	@Override
	protected synchronized void onDraw(Canvas canvas) {
		if (_paintedBitmap != null)
			canvas.drawBitmap(_paintedBitmap, 0, 0, _paint);
		if (_outlineBitmap != null)
			canvas.drawBitmap(_outlineBitmap, 0, 0, _paint);
	}

	public boolean onTouchEvent(MotionEvent e) {
		if (e.getAction() == MotionEvent.ACTION_DOWN) {
			paint((int) e.getX(), (int) e.getY());
		}
		return true;
	}

	private synchronized void paint(int x, int y) {
		// Copy the original mask to the working mask because it will be modified.
		System.arraycopy(_paintMask, 0, _workingMask, 0, _width * _height);

		// Do the nasty stuff.
		FloodFill.fillRaw(x, y, _width, _height, _workingMask, _pixels, _color);

		// And now copy all the pixels back.
		_paintedBitmap.setPixels(_pixels, 0, _width, 0, 0, _width, _height);
		invalidate();
	}

	private static void sendProgress(Handler h, int progress) {
		Message m = Message.obtain(h, MESSAGE_PROGRESS, progress, 0);
		h.sendMessage(m);
	}

	private static final int ALPHA_TRESHOLD = 224;

	// The listener whom we notify when ready to load images.
	private LifecycleListener _lifecycleListener;

	// Bitmap containing the outlines that are never changed.
	private Bitmap _outlineBitmap;
	// Bitmap containing everything we have painted so far.
	private Bitmap _paintedBitmap;

	// Dimensions of both bitmaps.
	private int _height;
	private int _width;

	// Paint with the currently selected color.
	private Paint _paint;
	private int _color;

	// paintMask has 0 for each pixel that cannot be modified and 1
	// for each one that can.
	private byte _paintMask[];

	// workingMask is in fact only needed during the fill - it is a copy
	// of paintMask that is modified during the fill. To avoid reallocating
	// it each time we store it as a member.
	private byte _workingMask[];

	// All the pixels in _paintedBitmap. Because accessing an int array is
	// much faster than accessing pixels in a bitmap, we operate on this
	// and use setPixels() on the bitmap to copy them back.
	private int _pixels[];
}
