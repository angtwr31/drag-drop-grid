//TO DO:
//
// - improve timer performance (especially on Eee Pad)
// - improve child rearranging

package com.bq.robotic.drag_drop_grid;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;


import java.util.ArrayList;
import java.util.Collections;


public class DraggableGridView extends ViewGroup implements View.OnTouchListener, View.OnClickListener, View.OnLongClickListener {
    //layout vars
    protected int colCount, childWidthSize, childHeightSize, padding, scroll = 0;
    protected float lastDelta = 0;
    protected Handler handler = new Handler();
    //dragging vars
    protected int dragged = -1, lastX = -1, lastY = -1, lastTarget = -1;
    protected boolean enabled = true, touching = false;
    //anim vars
    public static int animT = 150;
    protected ArrayList<Integer> newPositions = new ArrayList<Integer>();
    //listeners
    protected OnRearrangeListener onRearrangeListener;
    protected OnClickListener secondaryOnClickListener;
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    // Context
    Context context;
    //delete zone
    private DeleteDropZoneView deleteZone;
    private boolean draggedInDeleteZone = false;

    private int biggestChildWidth;
    private int biggestChildHeight;
    private float screenWidth;

    // Debugging
    private static final String LOG_TAG = "DraggableGridView";

    //CONSTRUCTOR AND HELPERS
    public DraggableGridView (Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        setListeners();
        handler.removeCallbacks(updateTask);
        handler.postAtTime(updateTask, SystemClock.uptimeMillis() + 500);
        setChildrenDrawingOrderEnabled(true);

        padding = getPaddingLeft();

    }


    //CONSTRUCTOR AND HELPERS
    public DraggableGridView (Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        this.context = context;
        setListeners();
        handler.removeCallbacks(updateTask);
        handler.postAtTime(updateTask, SystemClock.uptimeMillis() + 500);
        setChildrenDrawingOrderEnabled(true);

        padding = getPaddingLeft();

    }


    //CONSTRUCTOR AND HELPERS
    public DraggableGridView (Context context) {
        super(context);

        this.context = context;
        setListeners();
        handler.removeCallbacks(updateTask);
        handler.postAtTime(updateTask, SystemClock.uptimeMillis() + 500);
        setChildrenDrawingOrderEnabled(true);
    }


    public void setDeleteZone(DeleteDropZoneView deleteZone) {
        this.deleteZone = deleteZone;
    }


    protected void setListeners() {
        setOnTouchListener(this);
        super.setOnClickListener(this);
        setOnLongClickListener(this);
    }


    @Override
    public void setOnClickListener(OnClickListener l) {
        secondaryOnClickListener = l;
    }


    protected Runnable updateTask = new Runnable() {
        public void run() {

            if (dragged != -1) {
                if (lastY < padding * 3 && scroll > 0) {
                    scroll -= 20;
                } else if (lastY > getBottom() - getTop() - (padding * 3) && scroll < getMaxScroll()) {
                    scroll += 20;
                }

            } else if (lastDelta != 0 && !touching) {
                scroll += lastDelta;
                lastDelta *= .9;
                if (Math.abs(lastDelta) < .25) {
                    lastDelta = 0;
                }
            }

            clampScroll();
            requestLayout();

            handler.postDelayed(this, 25);
        }
    };


    /*
     * MANAGE VIEWS
     */
    @Override
    public void addView(View child) {
        super.addView(child);
        newPositions.add(-1);

        if(deleteZone != null) {
            deleteZone.bringToFront();
        }
    }

    @Override
    public void removeViewAt(int index) {
        super.removeViewAt(index);
        newPositions.remove(index);
    }


    public void removeAll() {
        super.removeAllViews();
        newPositions.clear();
        invalidate();
    }


    /**
     * Ask all children to measure themselves and compute the measurement of this
     * layout based on the children.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        measureChildren(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        measureChild(deleteZone, MeasureSpec.makeMeasureSpec(display.getWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int)getPixelFromDip(40), MeasureSpec.EXACTLY));

        setMeasuredDimension(widthSize, heightSize);
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

//        Log.e(LOG_TAG, "screen dpi: " + (right -left));

        screenWidth = right - left;

        float screenWidthAux = screenWidth;

        searchBiggestChildMeasures();
        int sub = biggestChildWidth;
        colCount = 0;

        for (int i = 0; i < getChildCount(); i++) {
            if(screenWidthAux < sub) {
                break;
            }

            colCount++;
            screenWidthAux -= sub;
        }

        childWidthSize = biggestChildWidth;
        childHeightSize = biggestChildHeight;

        for (int i = 0; i < getChildCount(); i++) {
            if (i != dragged) {
                Point xy = getCoorFromIndex(i);
                getChildAt(i).layout(xy.x, xy.y, xy.x + childWidthSize, xy.y + childHeightSize);
            }
        }

    }


    private float getPixelFromDip(int size) {
        Resources res = context.getResources();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size, res.getDisplayMetrics());
    }


    private void searchBiggestChildMeasures() {
        biggestChildWidth = 0;
        biggestChildHeight = 0;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);

            if (biggestChildHeight < child.getMeasuredHeight()) {
                biggestChildHeight = child.getMeasuredHeight();
            }

            if (biggestChildWidth < child.getMeasuredWidth()) {
                biggestChildWidth = child.getMeasuredWidth();
            }
        }
    }


    @Override
    protected int getChildDrawingOrder(int childCount, int i) {

        if (dragged == -1) {
            return i;
        } else if (i == childCount - 1) {
            return dragged;

        } else if (i >= dragged) {
            return i + 1;
        }

        return i;
    }


    public int getIndexFromCoor(int x, int y) {
        int col = getColFromCoor(x);
        int row = getRowFromCoor(y + scroll);

        if (col == -1 || row == -1) { //touch is between columns or rows
            return -1;
        }

        int index = row * colCount + col;

        if (index >= getChildCount()) {
            return -1;
        }

        return index;
    }


    protected int getColFromCoor(int coor) {
        coor -= padding;

        int widthForHorizontalCentering = 0;
        float emptySpaceInGrid = screenWidth - ((childWidthSize + padding) * colCount);

        if(emptySpaceInGrid < childWidthSize + padding) {
            widthForHorizontalCentering = Math.round(emptySpaceInGrid / 2);
        }

        coor -= widthForHorizontalCentering;

        for (int i = 0; coor > 0; i++) {
            if (coor < childWidthSize) {
                return i;
            }

            coor -= (childWidthSize + padding);
        }
        return -1;
    }


    protected int getRowFromCoor(int coor) {
        coor -= padding;

        for (int i = 0; coor > 0; i++) {
            if (coor < childHeightSize) {
                return i;
            }

            coor -= (childHeightSize + padding);
        }
        return -1;
    }


    protected int getTargetFromCoor(int x, int y) {
        if (getRowFromCoor(y + scroll) == -1) { //touch is between rows
            return -1;
        }

        int leftPos = getIndexFromCoor(x - (childWidthSize / 4), y);
        int rightPos = getIndexFromCoor(x + (childWidthSize / 4), y);

        if (leftPos == -1 && rightPos == -1) { //touch is in the middle of nowhere
            return -1;

        } else if (leftPos == rightPos) { //touch is in the middle of a visual
            return -1;
        }

        int target = -1;

        if (rightPos > -1) {
            target = rightPos;

        } else if (leftPos > -1) {
            target = leftPos + 1;
        }

        if (dragged < target) {
            return target - 1;
        }

        //Toast.makeText(getContext(), "Target: " + target + ".", Toast.LENGTH_SHORT).show();
        return target;
    }

    protected Point getCoorFromIndex(int index) {
        int col = index % colCount;
        int row = index / colCount;
        int widthForHorizontalCentering = 0;

        float emptySpaceInGrid = screenWidth - ((childWidthSize + padding) * colCount);

        if(emptySpaceInGrid < childWidthSize + padding) {
            widthForHorizontalCentering = Math.round(emptySpaceInGrid / 2);
        }

        return new Point(widthForHorizontalCentering + padding + (childWidthSize + padding) * col,
                padding + (childHeightSize + padding) * row - scroll);
    }

    public int getIndexOf(View child) {

        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) == child) {
                return i;
            }
        }

        return -1;
    }


    /**
     * EVENT HANDLERS
     */
    public void onClick(View view) {
        if (!enabled) {
            return;
        }

        if (onItemClickListener != null && getLastIndex() != -1) {
            onItemClickListener.onItemClick(null, getChildAt(getLastIndex()), getLastIndex(), getLastIndex() / colCount);
            return;
        }

        int index = getLastIndex();

        if (index != -1) {
            dragged = index;
            animateMoveAllItems();
            animateDragged();
            showDeleteView();

        }

    }


    public boolean onLongClick(View view) {
        if (!enabled) {
            return false;
        }

        if (onItemLongClickListener != null && getLastIndex() != -1) {
            onItemLongClickListener.onItemLongClick(null, getChildAt(getLastIndex()), getLastIndex(), getLastIndex() / colCount);

            return true;
        }

        int index = getLastIndex();

        if (index != -1) {
            dragged = index;
            animateMoveAllItems();
            animateDragged();
            showDeleteView();
            return true;
        }

        return false;
    }


    public boolean onTouch(View view, MotionEvent event) {
        int action = event.getAction();

        switch (action & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN:
                enabled = true;
                lastX = (int) event.getX();
                lastY = (int) event.getY();
                touching = true;
                break;

            case MotionEvent.ACTION_MOVE:
                int delta = lastY - (int)event.getY();

                invalidate();

                if (dragged != -1) {
                    //change draw location of dragged visual
                    int x = (int)event.getX();
                    int y = (int)event.getY();
                    int l = x - (3 * childWidthSize / 4);
                    int t = y - (3 * childHeightSize / 4);
                    getChildAt(dragged).layout(l, t, l + (childWidthSize * 3 / 2), t + (childHeightSize * 3 / 2));

                    //check for new target hover
                    int target = getTargetFromCoor(x, y);
                    if (lastTarget != target) {
                        if (target != -1) {
                            animateGap(target);
                            lastTarget = target;
                        }
                    }

                } else {
                    scroll += delta;
                    clampScroll();
                    if (Math.abs(delta) > 2) {
                        enabled = false;
                    }
                    requestLayout();
                }

                lastX = (int) event.getX();
                lastY = (int) event.getY();
                manageDeleteZoneHover(lastX, lastY);
                lastDelta = delta;
                break;

            case MotionEvent.ACTION_UP:
                if (dragged != -1) {
                    View v = getChildAt(dragged);

                    if(touchUpInDeleteZoneDrop(lastX, lastY)) {
                        draggedInDeleteZone = true;
                        reorderChildren();

                    } else if (lastTarget != -1) {
                        reorderChildren();
                    } else {
                        Point xy = getCoorFromIndex(dragged);
                        v.layout(xy.x, xy.y, xy.x + childWidthSize, xy.y + childHeightSize);
                    }

                    if (v instanceof ImageView) {
                        ((ImageView)v).setAlpha(255);
                    }

                    lastTarget = -1;
                    dragged = -1;
                    hideDeleteView();
                    draggedInDeleteZone = false;
                }
                touching = false;
                cancelAnimations();
                break;
        }

        if (dragged != -1) {
            return true;
        }

        return false;
    }


    /*
     * ANIMATIONS
     */
    protected void animateDragged() {
        View v = getChildAt(dragged);
        int x = getCoorFromIndex(dragged).x + childWidthSize / 2;
        int y = getCoorFromIndex(dragged).y + childHeightSize / 2;
        int l = x - (3 * childWidthSize / 4);
        int t = y - (3 * childHeightSize / 4);
        v.layout(l, t, l + (childWidthSize * 3 / 2), t + (childHeightSize * 3 / 2));

        AnimationSet animSet = new AnimationSet(true);
        ScaleAnimation scale = new ScaleAnimation(.667f, 1, .667f, 1, childWidthSize * 3 / 4, childHeightSize * 3 / 4);
        scale.setDuration(animT);
        AlphaAnimation alpha = new AlphaAnimation(1, .5f);
        alpha.setDuration(animT);

        animSet.addAnimation(createFastRotateAnimation());
        animSet.addAnimation(scale);
        animSet.addAnimation(alpha);
        animSet.setFillEnabled(true);
        animSet.setFillAfter(true);

        v.clearAnimation();
        v.startAnimation(animSet);
    }

    private void animateMoveAllItems() {
        for (int i=0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.startAnimation(createFastRotateAnimation());
        }
    }

    protected void animateGap(int target) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);

            if (i == dragged) {
                continue;
            }

            int newPos = i;
            if (dragged < target && i >= dragged + 1 && i <= target) {
                newPos--;
            } else if (target < dragged && i >= target && i < dragged) {
                newPos++;
            }

            int oldPos = i;
            if (newPositions.get(i) != -1) {
                oldPos = newPositions.get(i);
            }

            if (oldPos == newPos) {
                continue;
            }

            Point oldXY = getCoorFromIndex(oldPos);
            Point newXY = getCoorFromIndex(newPos);
            Point oldOffset = new Point(oldXY.x - v.getLeft(), oldXY.y - v.getTop());
            Point newOffset = new Point(newXY.x - v.getLeft(), newXY.y - v.getTop());

            AnimationSet animSet = new AnimationSet(true);
            animSet.addAnimation(createFastRotateAnimation());
            animSet.addAnimation(createTranslateAnimation(oldOffset, newOffset));

            v.clearAnimation();
            v.startAnimation(animSet);

            newPositions.set(i, newPos);
        }
    }


    private Animation createTranslateAnimation(Point oldOffset, Point newOffset) {
        TranslateAnimation translate = new TranslateAnimation(Animation.ABSOLUTE, oldOffset.x,
                Animation.ABSOLUTE, newOffset.x,
                Animation.ABSOLUTE, oldOffset.y,
                Animation.ABSOLUTE, newOffset.y);
        translate.setDuration(animT);
        translate.setFillEnabled(true);
        translate.setFillAfter(true);
        translate.setInterpolator(new AccelerateDecelerateInterpolator());

        return translate;
    }


    private Animation createFastRotateAnimation() {
        Animation rotate = new RotateAnimation(-3.0f,
                3.0f,
                Animation.RELATIVE_TO_SELF,
                0.8f,
                Animation.RELATIVE_TO_SELF,
                0.8f);

        rotate.setRepeatMode(Animation.REVERSE);
        rotate.setRepeatCount(Animation.INFINITE);
        rotate.setDuration(60);
        rotate.setInterpolator(new AccelerateDecelerateInterpolator());

        return rotate;
    }


    private void cancelAnimations() {
        for (int i=0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.clearAnimation();
        }
    }


    /**
     * REORDER CHILDREN
     */
    protected void reorderChildren() {
        //FIXME: FIGURE OUT HOW TO REORDER CHILDREN WITHOUT REMOVING THEM ALL AND RECONSTRUCTING THE LIST!!!

        ArrayList<View> children = new ArrayList<View>();
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).clearAnimation();
            children.add(getChildAt(i));
        }

        removeAllViews();

        if(draggedInDeleteZone) { // dragged in deleted zone
            children.remove(dragged);


            if (onRearrangeListener != null) {
                onRearrangeListener.onRearrange(true, dragged);
            }

        } else {

            if (onRearrangeListener != null) {
                onRearrangeListener.onRearrange(dragged, lastTarget);
            }

            while (dragged != lastTarget) {
                if (lastTarget == children.size()) { // dragged and dropped to the right of the last element
                    children.add(children.remove(dragged));
                    dragged = lastTarget;

                } else if (dragged < lastTarget) { // shift to the right
                    Collections.swap(children, dragged, dragged + 1);
                    dragged++;

                } else if (dragged > lastTarget) { // shift to the left
                    Collections.swap(children, dragged, dragged - 1);
                    dragged--;
                }
            }
        }

        for (int i = 0; i < children.size(); i++) {
            newPositions.set(i, -1);
            addView(children.get(i));
        }

        if(deleteZone != null) {
            deleteZone.bringToFront();
        }

        requestLayout();
    }


    public void scrollToTop() {
        scroll = 0;
    }


    public void scrollToBottom() {
        scroll = Integer.MAX_VALUE;
        clampScroll();
    }


    protected void clampScroll() {
        int stretch = 3, overreach = getHeight() / 2;
        int max = getMaxScroll();
        max = Math.max(max, 0);

        if (scroll < -overreach) {
            scroll = -overreach;
            lastDelta = 0;

        } else if (scroll > max + overreach) {
            scroll = max + overreach;
            lastDelta = 0;

        } else if (scroll < 0) {
            if (scroll >= -stretch) {
                scroll = 0;
            } else if (!touching) {
                scroll -= scroll / stretch;
            }

        } else if (scroll > max) {
            if (scroll <= max + stretch) {
                scroll = max;
            } else if (!touching) {
                scroll += (max - scroll) / stretch;
            }
        }
    }


    protected int getMaxScroll() {
        int rowCount = (int)Math.ceil((double)getChildCount()/colCount), max = rowCount * childHeightSize + (rowCount + 1) * padding - getHeight();
        return max;
    }


    public int getLastIndex() {
        return getIndexFromCoor(lastX, lastY);
    }


    /*
     * DELETE ZONE
     */
    private void manageDeleteZoneHover(int x, int y) {

        if(deleteZone == null) {
            return;
        }

        if (touchUpInDeleteZoneDrop(x, y) ) {
            deleteZone.highlight();
        } else {
            deleteZone.smother();
        }
    }


    private boolean touchUpInDeleteZoneDrop(int x, int y) {

        if(deleteZone == null) {
            return false;
        }

        Rect zone = new Rect();
        deleteZone.getHitRect(zone);

        int offset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics());

        if (zone.intersect(x, y, x + offset, y + offset)) {
            deleteZone.smother();
            return true;
        }
        return false;
    }


    private void showDeleteView() {
        if(deleteZone != null) {
            deleteZone.setVisibility(View.VISIBLE);
        }
    }


    private void hideDeleteView() {
        if(deleteZone != null) {
            deleteZone.setVisibility(View.INVISIBLE);
        }
    }


    /*
     * LISTENERS
     */
    public void setOnRearrangeListener(OnRearrangeListener l) {
        this.onRearrangeListener = l;
    }


    public void setOnItemClickListener(OnItemClickListener l) {
        this.onItemClickListener = l;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener l) {
        this.onItemLongClickListener = l;
    }
}