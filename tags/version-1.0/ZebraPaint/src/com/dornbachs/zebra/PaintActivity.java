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

import java.util.Iterator;
import java.util.LinkedList;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

public class PaintActivity
	extends ZebraActivity
	implements PaintView.LifecycleListener {
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.paint);
        _paintView = (PaintView)findViewById(R.id.paint_view);
        _paintView.setLifecycleListener(this);
        _progressBar = (ProgressBar)findViewById(R.id.paint_progress);
        _colorButtonManager = new ColorButtonManager();
        View pickColorsButton = findViewById(R.id.pick_color_button);
        pickColorsButton.setOnClickListener(new PickColorListener());

        // We need to make the paint view INVISIBLE (and not GONE) so that
        // it can measure itself correctly.
        _paintView.setVisibility(View.INVISIBLE);
        _progressBar.setVisibility(View.GONE);
}
    
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.paint_menu, menu);
        return true;
    }

    public void onPreparedToLoad() {
    	// We need to invoke InitPaintView in a callback otherwise
    	// the visibility changes do not seem to be effective.
    	new Handler() {
            @Override
            public void handleMessage(Message m) {
            	new InitPaintView(R.drawable.outline001_balloons);
            }
    	}.sendEmptyMessage(0);
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.open_new:
            startActivityForResult(
            		new Intent(INTENT_START_NEW),
            		REQUEST_START_NEW);
            return true;
        }
        return false;
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch (requestCode) {
    	case REQUEST_START_NEW:
    		if (resultCode != 0) {
    	    	new InitPaintView(resultCode);
    		}
        	break;
    	case REQUEST_PICK_COLOR:
    		if (resultCode != 0) {
    			_colorButtonManager.selectColor(resultCode);
    		}
    		break;
    	}
    }
    
    private class PickColorListener implements View.OnClickListener {
		public void onClick(View view) {
            startActivityForResult(
            		new Intent(INTENT_PICK_COLOR),
            		REQUEST_PICK_COLOR);
		}
    }
    
    private class ColorButtonManager implements View.OnClickListener {
    	public ColorButtonManager() {
            findAllColorButtons(_colorButtons);
            _selectedColorButton = _colorButtons.getFirst();
            _selectedColorButton.setSelected(true);
    		Iterator<ColorButton> i = _colorButtons.iterator();
    		while (i.hasNext()) {
    			i.next().setOnClickListener(this);
            }
    		setPaintViewColor();
    	}
    	
    	public void onClick(View view) {
			if (view instanceof ColorButton) {
				selectButton((ColorButton) view);
			}
    	}
    	
    	// Select the button that has the given color, or if there is no such
    	// button then set the least recently used button to have that color.
    	public void selectColor(int color) {
    		_selectedColorButton = selectAndRemove(color);
    		if (_selectedColorButton == null) {
    			// Recycle the last used button to hold the new color.
    			_selectedColorButton = _colorButtons.removeLast();
    			_selectedColorButton.setColor(color);
    			_selectedColorButton.setSelected(true);
    		}
			_colorButtons.addFirst(_selectedColorButton);
    		setPaintViewColor();
    	}
    	
    	// Select the given button.
    	private void selectButton(ColorButton button) {
			_selectedColorButton = selectAndRemove(button.getColor());
    		_colorButtons.addFirst(_selectedColorButton);
    		setPaintViewColor();
    	}
    	
    	private void setPaintViewColor() {
			_paintView.setPaintColor(_selectedColorButton.getColor());
    	}

    	// Finds the button with the color. If found, sets it to selected,
    	// removes it and returns it. If not found, it returns null. All
    	// other buttons are unselected.
    	private ColorButton selectAndRemove(int color) {
    		ColorButton result = null;
    		Iterator<ColorButton> i = _colorButtons.iterator();
    		while (i.hasNext()) {
    			ColorButton b = i.next();
    			if (b.getColor() == color) {
    				result = b;
    				b.setSelected(true);
    				i.remove();
    			} else {
    				b.setSelected(false);
    			}
    		}
    		return result;
    	}

    	// A list of pointers to all buttons in the order
    	// in which they have been used.
        private LinkedList<ColorButton> _colorButtons =
        	new LinkedList<ColorButton>();
        private ColorButton _selectedColorButton;
    }
    
    private class InitPaintView implements Runnable {
    	public InitPaintView(int outlineResourceId) {
    		// Make the progress bar visible and hide the view
    		_paintView.setVisibility(View.GONE);
			_progressBar.setProgress(0);
			_progressBar.setVisibility(View.VISIBLE);

    		_outlineResourceId = outlineResourceId;
    		_handler = new Handler() {
                @Override
                public void handleMessage(Message m) {
                	switch (m.what) {
                	case PaintView.MESSAGE_PROGRESS:
                		// Update progress bar.
	        			_progressBar.setProgress(m.arg1);
	        			break;
                	case PaintView.MESSAGE_DONE:
                		// We are done, hide the progress bar and turn 
                		// the paint view back on.
	        			_paintView.setVisibility(View.VISIBLE);
	        			_progressBar.setVisibility(View.GONE);
	        			break;
                	}
                }
    		};

			new Thread(this).start();
    	}
    	
		public void run() {
			_paintView.loadFromResource(_outlineResourceId, _handler);
		}

		private int _outlineResourceId;
		private Handler _handler;
    }

    private static final int REQUEST_START_NEW = 0;
    private static final int REQUEST_PICK_COLOR = 1;
    
    private PaintView _paintView;
    private ProgressBar _progressBar;
    private ColorButtonManager _colorButtonManager;
}