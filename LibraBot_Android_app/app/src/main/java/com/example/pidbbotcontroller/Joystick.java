/*
 * Copyright 2020 Zakaria Madaoui. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.pidbbotcontroller;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class Joystick extends View {

    RectF rectF;
    Paint circlePaint;
    private float joyX;
    private float joyY;
    private Paint ballPaint;
    private float ballRadius = 25f;
    private float width = 0;
    private float height = 0;
    private float centerX  = 0;
    private float centerY  = 0;
    private float offset = 0;
    private boolean straightOnly = false;
    private boolean unlocked = true;
    private boolean lockedOnX = false;
    private boolean lockedOnY = false;
    private OnBallMoveListener ballMoveListener;
    public final static String FLAG_ONLY_X = "onlyX";
    public final static String FLAG_ONLY_Y = "onlyY";
    public final static String FLAG_XY = "XY";
    private String pFlag = FLAG_XY;
    private float TO_DEGREES = (float) (180 / Math.PI);


    public void setStraightOnly(boolean straightOnly, String flag) {
        this.straightOnly = straightOnly;
        pFlag = flag;
        lockedOnX = false;
        lockedOnY = false;
    }

    public Joystick(Context context) {
        super(context);
    }
    public Joystick(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    public Joystick(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public Joystick(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        centerX = (float)w/2;
        centerY = (float)h/2;
        joyX = centerX; //centering joystick ball
        joyY = centerY; //centering joystick ball

        //outer circle position parameters
        rectF.left = offset;
        rectF.top = offset;
        rectF.right = width - offset;
        rectF.bottom = height -offset;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        float x = event.getX() - centerX;
        float y = event.getY() - centerY;
        float r = (float) Math.sqrt(x * x + y * y);
        float angle = 0;
        float px = 0;
        float py = 0;
        float xmax = width / 2 - ballRadius ;
        float ymax = height / 2 - ballRadius ;

        if (straightOnly){// if movement is straight only (X-axis / Y-axis or both )
            if (pFlag.equals(FLAG_XY)){ // case1: straight movement on both X/Y axis

                if (r <= 1.5*ballRadius && unlocked){ //this gives the ball a small area where it is not locked to any axis ,
                    // which makes the joystick in this mode feel more realistic and easy to use as if it is a real one
                    joyX = event.getX();
                    joyY = event.getY();
                    lockedOnX = false;
                    lockedOnY = false;
                }
                else {// determine whether to move on X or Y axis and lock movement to it
                    if (Math.abs(x) >= Math.abs(y) && unlocked){
                        lockedOnX = true;
                        lockedOnY = false;
                        unlocked = false;
                    }
                    else if(Math.abs(y) >= Math.abs(x) && unlocked) {
                        lockedOnX = false;
                        lockedOnY = true;
                        unlocked = false;
                    }
                }
            }

            if (lockedOnX || pFlag.equals(FLAG_ONLY_X)){//case2: movement only on X-axis
                joyX = x > 0 ? Math.min(event.getX(), width - ballRadius): Math.max(event.getX(), ballRadius);//constrain the ball movement on X-axis to be only within or in the perimeter of the outer circle
                joyY = centerY;
                px = x > 0 ? Math.min(x / xmax, 1f) : Math.max(x / xmax, -1f); //scale displacement to values between -1 and 1
                py = 0;//displacement on Y is always zero in this case
                angle = (float) (Math.atan2(-py, px) * 180 / Math.PI);
            }
            else if (lockedOnY || pFlag.equals(FLAG_ONLY_Y)){
                joyX = centerX;
                joyY = y > 0 ? Math.min(event.getY(), height - ballRadius): Math.max(event.getY(), ballRadius);//constrain the ball movement on Y-axis to be only within or in the perimeter of the outer circle
                py = y > 0 ? -Math.min(y / ymax, 1f) : -Math.max(y / ymax, -1f);//scale displacement to values between -1 and 1
                px = 0;//displacement on X is always zero in this case
                angle = (float) (Math.atan2(py, px) * TO_DEGREES);
            }
        }
        else {//case4: free movement
            if (r > centerX || r > centerY) {//constrain ball movement to the perimeter of the outer circle if the finger is touching outside the circle
                float cost = x / r;
                float sint = y / r;
                joyX = (width / 2 - ballRadius) * cost + centerX;
                joyY = (height / 2 - ballRadius) * sint + centerY;

                px = cost;//scale values to -1,1 range
                py = -sint;//scale values to -1,1 range
                angle = (float) (Math.atan2(-y,x)* TO_DEGREES);//calculate angle

            } else {//movement is withing the circle so move same as the finger
                joyX = event.getX();
                joyY = event.getY();
                px = x / xmax;//scale values to -1,1 range
                py = -y / ymax;//scale values to -1,1 range
                angle = (float) (Math.atan2(-y,x)* TO_DEGREES);//calculate angle
            }
        }

        if (event.getAction() != 2) {//if the ball is released reset its position to center
            joyX = centerX;
            joyY = centerY;
            unlocked = true;
            px = 0;
            py = 0;
            angle = 0;
            ballMoveListener.onMove(0 ,0, 0);
        }

        invalidate();

        angle = angle >=0 ? angle :(360 + angle) ; // correct angle range
        ballMoveListener.onMove(px ,py, angle); //invoke onMove Event

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //drawing ball and outer circle
        canvas.drawArc(rectF,0,360,false, circlePaint);
        canvas.drawCircle(joyX, joyY, ballRadius, ballPaint);

    }

    //All object instantiation should be here for optimal performance
    public void init(@Nullable AttributeSet attrs){

        //getting the attributes from xml
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.Joystick);
        int joyColor = ta.getColor(R.styleable.Joystick_JoystickColor, 0xFF4DD0E1); // blue is default color
        int ballColor = ta.getColor(R.styleable.Joystick_ballColor, 0xFFFFB74D); // blue is default color
        ballRadius = ta.getDimension(R.styleable.Joystick_ballRadius, 25f); //25dp is the default size
        float circleThikness = ta.getDimension(R.styleable.Joystick_outerCircleThickness, 20f); //20dp is the default size
        ta.recycle();

        //finding the optimal offset for outer circle to avoid clipping
        if (ballRadius >= circleThikness) offset =  ballRadius;
        else offset = circleThikness /2;

        //instantiating shapes and paints
        rectF = new RectF();//container for outer circle
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG); // anti aliasing prevents pixelized shapes
        ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG); // anti aliasing prevents pixelized shapes
        circlePaint.setColor(joyColor);
        circlePaint.setStrokeWidth(circleThikness);
        circlePaint.setStyle(Paint.Style.STROKE);
        ballPaint.setColor(ballColor);
        ballPaint.setStyle(Paint.Style.FILL);

    }

    //interface and listener setter for onMove() event
    public interface OnBallMoveListener{
        void onMove(float px, float py, float angle);
    }
    public void setOnMoveListener(OnBallMoveListener listener){
        ballMoveListener = listener;
    }
}
