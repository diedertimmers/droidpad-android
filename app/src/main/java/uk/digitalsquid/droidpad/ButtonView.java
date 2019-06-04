/*  This file is part of DroidPad.
 *
 *  DroidPad is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  DroidPad is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with DroidPad.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.digitalsquid.droidpad;

import uk.digitalsquid.droidpad.buttons.Item;
import uk.digitalsquid.droidpad.buttons.Item.ScreenInfo;
import uk.digitalsquid.droidpad.buttons.Layout;
import uk.digitalsquid.droidpad.buttons.ModeSpec;
import uk.digitalsquid.droidpad.buttons.Slider;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;


/**
 * Shows the buttons onscreen.
 * @author william
 *
 */
public class ButtonView extends View implements LogTag, UICallbacks
{
	private boolean landscape;
	
	private Layout layout = new Layout();
	
	Buttons parent;
	
	public ButtonView(Context context, AttributeSet attrib) {
		super(context, attrib);
		if(isInEditMode()) return;
		landscape = false;
	}
	
	public ButtonView(Context context) {
		super(context);
		if(isInEditMode()) return;
		landscape = false;
	}
	
	/**
	 * Sets the current mode spec. Should only be called once, when the view is created.
	 * This is delayed as the class is instantiated through XML
	 * @param mode
	 */
	public void setModeSpec(Buttons parent, ModeSpec mode) {
		if(mode == null) {
			mode = new ModeSpec();
			mode.setLayout(new Layout());
		}
        boolean floatingAxes = !PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("axesfloat", false);
		
		this.parent = parent;
		
		layout = mode.getLayout(); // No need to keep type?
		layout.setUiCallbacks(this);
        
        for(Item item : layout) {
        	if(item instanceof Slider) {
        		((Slider)item).setAxesFloat(floatingAxes);
        	}
        }
        
        landscape = mode.isLandscape();
		
		parent.sendEvent(layout);
	}
	
	private float scale = 1;
	
	private static final Paint P_BLACK = new Paint(0xFF000000);
	
	private ScreenInfo tmpScreenInfo = new ScreenInfo();
	
	@Override
	public void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		scale = getResources().getDisplayMetrics().density;
		
		float width = (float)canvas.getWidth() / scale;
		float height = (float)canvas.getHeight() / scale;
		
		canvas.scale(scale, scale);
		
		canvas.drawRect(0, 0, width, height, P_BLACK);
		if(isInEditMode()) return;
		float widthIter = width / layout.getWidth();
		float heightIter = height / layout.getHeight();
		
		tmpScreenInfo.set(width, height, widthIter, heightIter, landscape);
		
		for(Item item : layout) {
			item.draw(canvas, tmpScreenInfo);
		}
	}

	private void processPoint(float x, float y) {
		processPoint(x, y, false);
	}
	private void processPoint(float x, float y, boolean up) {
		x /= scale;
		y /= scale;
		for(Item item : layout) {
			if(item.pointIsInArea(tmpScreenInfo, x, y)) {
				if(!up)
					item.onMouseOn(tmpScreenInfo, x, y);
				else {
					item.onMouseOff();
					item.resetStickyLock();
				}
			}
		}
	}
	
	/**
	 * Removes the sticky lock from each button indicating that at least 1 thing is pressing it.
	 */
	private void resetItemSticky() {
		for(Item item : layout) {
			item.resetStickyLock();
		}
	}
	
	/**
	 * Sets the sticky lock from each button indicating that at least 1 thing is pressing it.
	 */
	private void finaliseItemState() {
		for(Item item : layout) {
			item.finaliseState();
		}
	}
	
	public boolean onTouchEvent(MotionEvent event)
	{
		super.onTouchEvent(event);
		
		int actionCode = event.getAction() & MotionEvent.ACTION_MASK;
		if(actionCode == MotionEvent.ACTION_DOWN || actionCode == MotionEvent.ACTION_POINTER_DOWN) {
			// Something was pressed
			float x, y;
			if(actionCode == MotionEvent.ACTION_DOWN) {
				x = event.getX();
				y = event.getY();
			}
			else { // Other pointer
				int pid = event.getAction() >> MotionEvent.ACTION_POINTER_ID_SHIFT;
				x = event.getX(pid);
				y = event.getY(pid);
			}
			processPoint(x, y);
		}
		
		if(actionCode == MotionEvent.ACTION_UP || actionCode == MotionEvent.ACTION_POINTER_UP) {
			// Something was released
		
			float x, y;
			if(actionCode == MotionEvent.ACTION_UP) {
				x = event.getX();
				y = event.getY();
			}
			else { // Other pointer
				int pid = event.getAction() >> MotionEvent.ACTION_POINTER_ID_SHIFT;
				x = event.getX(pid);
				y = event.getY(pid);
			}
			processPoint(x, y, true);
		}
		
		if(actionCode == MotionEvent.ACTION_MOVE) {
			resetItemSticky();
			for(int i = 0; i < event.getPointerCount(); i++) {
				processPoint(event.getX(i), event.getY(i));
			}
		}
		
		if(parent != null) parent.sendEvent(layout); // Make sure always latest - eg service restart
		
		finaliseItemState();
		
		invalidate();

		return true;
	}

	Layout getLayout() {
		return layout;
	}

	@Override
	public void refreshScreen() {
		// TODO: Only call if something has changed?
		postInvalidate();
	}
}
